/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.knowhow.processor.scm.client.azuredevops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.knowhow.processor.scm.exception.RepositoryException;
import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import lombok.extern.slf4j.Slf4j;
import org.azd.core.types.Project;
import org.azd.enums.PullRequestStatus;
import org.azd.git.types.GitCommitChanges;
import org.azd.git.types.GitCommitRef;
import org.azd.git.types.GitCommitRefs;
import org.azd.git.types.GitCommitsBatch;
import org.azd.git.types.GitPullRequest;
import org.azd.git.types.GitPullRequestQueryParameters;
import org.azd.git.types.GitRef;
import org.azd.git.types.GitRepository;
import org.azd.interfaces.CoreDetails;
import org.azd.interfaces.GitDetails;
import org.azd.utils.AzDClientApi;
import org.azd.wiki.types.GitVersionDescriptor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Azure DevOps API client for interacting with Azure Repos. Handles
 * authentication, rate limiting, and data fetching operations.
 */
@Component
@Slf4j
public class AzureDevOpsClient {

	@Value("${git.platforms.azure-devops.api-url:https://dev.azure.com}")
	private String azureDevOpsApiUrl;

	private final WebClient.Builder webClientBuilder;
	private final ObjectMapper objectMapper;

	public AzureDevOpsClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
		this.webClientBuilder = webClientBuilder;
		this.objectMapper = objectMapper;
	}

	private AzDClientApi createClient(String token, String project, String organization) throws Exception {
		if (token == null || token.trim().isEmpty()) {
			throw new IllegalArgumentException("Azure DevOps token cannot be null or empty");
		}

		if (organization == null || organization.trim().isEmpty()) {
			throw new IllegalArgumentException("Azure DevOps organization cannot be null or empty");
		}

		try {
			AzDClientApi client = new AzDClientApi(organization, project, token);
			log.debug("Successfully authenticated with Azure DevOps API for organization: {}", organization);
			return client;
		} catch (Exception e) {
			log.error("Failed to authenticate with Azure DevOps API: {}", e.getMessage());
			throw new RepositoryException.RepositoryAuthenticationException(
					"Azure DevOps authentication failed: " + e.getMessage());
		}
	}

	/**
	 * Gets an Azure DevOps repository instance.
	 *
	 * @param organization
	 *            Azure DevOps organization
	 * @param project
	 *            Azure DevOps project
	 * @param repository
	 *            Repository name
	 * @param token
	 *            Azure DevOps access token
	 * @return GitRepository instance
	 * @throws Exception
	 *             if repository access fails
	 */
	public GitRepository getRepository(String organization, String project, String repository, String token)
			throws Exception {
		AzDClientApi client = createClient(token, project, organization);
		GitDetails gitApi = client.getGitApi();

		try {
			GitRepository repo = gitApi.getRepository(repository);
			log.debug("Successfully accessed Azure DevOps repository: {}/{}/{}", organization, project, repository);
			return repo;
		} catch (Exception e) {
			log.error("Failed to access Azure DevOps repository {}/{}/{}: {}", organization, project, repository,
					e.getMessage());
			throw new RepositoryException.RepositoryAccessDeniedException(
					"Failed to access repository: " + organization + "/" + project + "/" + repository);
		}
	}

	/**
	 * Fetches commits from an Azure DevOps repository with pagination and date
	 * filtering.
	 *
	 * @param organization
	 *            Azure DevOps organization
	 * @param project
	 *            Azure DevOps project
	 * @param repository
	 *            Repository name
	 * @param branchName
	 *            Branch name to fetch commits from
	 * @param token
	 *            Azure DevOps access token
	 * @param since
	 *            Start date for commit filtering
	 * @param until
	 *            End date for commit filtering
	 * @return List of GitCommit objects
	 * @throws Exception
	 *             if fetching commits fails
	 */
	public List<GitCommitRef> fetchCommits(String organization, String project, String repository, String branchName,
			String token, LocalDateTime since, LocalDateTime until) throws Exception {
		AzDClientApi client = createClient(token, project, organization);
		GitDetails gitApi = client.getGitApi();

		List<GitCommitRef> allCommits = new ArrayList<>();

		try {
			log.info("Fetching commits from Azure DevOps repository: {}/{}/{} (branch: {})", organization, project,
					repository, branchName != null ? branchName : "default");

			GitCommitsBatch gitCommitsBatch = createCommitsBatch(branchName, since);

			fetchCommitsWithPagination(gitApi, repository, gitCommitsBatch, allCommits, since, until);

			log.info("Successfully fetched {} commits from Azure DevOps repository: {}/{}/{}", allCommits.size(),
					organization, project, repository);
			return allCommits;

		} catch (Exception e) {
			log.error("Failed to fetch commits from Azure DevOps repository {}/{}/{}: {}", organization, project,
					repository, e.getMessage());
			throw new RepositoryException("Failed to fetch commits from Azure DevOps", e);
		}
	}

	private GitCommitsBatch createCommitsBatch(String branchName, LocalDateTime since) {
		GitVersionDescriptor versionDescriptor = new GitVersionDescriptor();
		versionDescriptor.version = branchName;

		GitCommitsBatch gitCommitsBatch = new GitCommitsBatch();
		gitCommitsBatch.top = 100;
		gitCommitsBatch.skip = 0;
		gitCommitsBatch.fromDate = since.toString();
		gitCommitsBatch.showOldestCommitsFirst = false;
		gitCommitsBatch.itemVersion = versionDescriptor;

		return gitCommitsBatch;
	}

	private void fetchCommitsWithPagination(GitDetails gitApi, String repository, GitCommitsBatch gitCommitsBatch,
			List<GitCommitRef> allCommits, LocalDateTime since, LocalDateTime until) {
		boolean hasMore = true;

		while (hasMore) {
			try {
				GitCommitRefs commitRefs = gitApi.getCommitsBatch(repository, gitCommitsBatch);
				List<GitCommitRef> commitRefsList = commitRefs.getGitCommitRefs();

				if (commitRefsList == null || commitRefsList.isEmpty()) {
					break;
				}

				List<GitCommitRef> filteredCommits = filterCommitsByDate(commitRefsList, since, until);
				allCommits.addAll(filteredCommits);

				// Check if we've reached the end
				if (commitRefsList.size() < gitCommitsBatch.top) {
					hasMore = false;
				} else {
					gitCommitsBatch.skip += gitCommitsBatch.top;
				}

				log.debug("Fetched {} commits (total: {}) from Azure DevOps repository", filteredCommits.size(),
						allCommits.size());

			} catch (Exception e) {
				log.warn("Failed to fetch commits batch (skip: {}, top: {}) from Azure DevOps: {}",
						gitCommitsBatch.skip, gitCommitsBatch.top, e.getMessage());
				hasMore = false;
			}
		}
	}

	private List<GitCommitRef> filterCommitsByDate(List<GitCommitRef> commits, LocalDateTime since,
			LocalDateTime until) {
		return commits.stream().filter(createCommitDateFilter(since, until)).toList();
	}

	private Predicate<GitCommitRef> createCommitDateFilter(LocalDateTime since, LocalDateTime until) {
		return commit -> {
			if (commit.getCommitter() == null || commit.getCommitter().getDate() == null) {
				return true; // Include commits without date info
			}

			try {
				String dateStr = commit.getCommitter().getDate();
				LocalDateTime commitDate = LocalDateTime.parse(dateStr.substring(0, 19));

				boolean afterSince = !commitDate.isBefore(since);
				boolean beforeUntil = until == null || !commitDate.isAfter(until);

				return afterSince && beforeUntil;
			} catch (Exception e) {
				log.warn("Failed to parse commit date: {}", e.getMessage());
				return true; // Include commits with unparseable dates
			}
		};
	}

	/**
	 * Fetches pull requests from an Azure DevOps repository with date filtering.
	 *
	 * @param organization
	 *            Azure DevOps organization
	 * @param project
	 *            Azure DevOps project
	 * @param repository
	 *            Repository name
	 * @param token
	 *            Azure DevOps access token
	 * @param since
	 *            Start date for pull request filtering
	 * @return List of GitPullRequest objects
	 * @throws Exception
	 *             if fetching pull requests fails
	 */
	public List<GitPullRequest> fetchPullRequests(String organization, String project, String repository, String token,
			LocalDateTime since, String branch) throws Exception {
		AzDClientApi client = createClient(token, project, organization);
        GitDetails gitApi = client.getGitApi();

		List<GitPullRequest> allPullRequests = new ArrayList<>();

		try {
			log.info("Fetching pull requests from Azure DevOps repository: {}/{}/{}", organization, project,
					repository);

			GitPullRequestQueryParameters queryParams = createPullRequestQueryParams(branch);

			fetchPullRequestsWithPagination(gitApi, repository, queryParams, allPullRequests, since);

			log.info("Successfully fetched {} pull requests from Azure DevOps repository: {}/{}/{}",
					allPullRequests.size(), organization, project, repository);
			return allPullRequests;

		} catch (Exception e) {
			log.error("Failed to fetch pull requests from Azure DevOps repository {}/{}/{}: {}", organization, project,
					repository, e.getMessage());
			throw new RepositoryException("Failed to fetch pull requests from Azure DevOps", e);
		}
	}

	private GitPullRequestQueryParameters createPullRequestQueryParams(String branch) {
		GitPullRequestQueryParameters queryParams = new GitPullRequestQueryParameters();
		queryParams.top = 100;
		queryParams.skip = 0;
		queryParams.status = PullRequestStatus.ALL;
		queryParams.targetRefName = "refs/heads/" + branch;
		return queryParams;
	}

	private void fetchPullRequestsWithPagination(GitDetails gitApi, String repository,
			GitPullRequestQueryParameters queryParams, List<GitPullRequest> allPullRequests, LocalDateTime since) {
		boolean hasMore = true;

		while (hasMore) {
			try {
				var pullRequestsResponse = gitApi.getPullRequests(repository, queryParams);
				List<GitPullRequest> pullRequests = pullRequestsResponse.getPullRequests();

				if (pullRequests == null || pullRequests.isEmpty()) {
					break;
				}

				List<GitPullRequest> filteredPRs = filterPullRequestsByDate(pullRequests, since);
				allPullRequests.addAll(filteredPRs);

				// Check if we've reached the end
				if (pullRequests.size() < queryParams.top) {
					hasMore = false;
				} else {
					queryParams.skip += queryParams.top;
				}

				log.debug("Fetched {} pull requests (total: {}) from Azure DevOps repository", filteredPRs.size(),
						allPullRequests.size());

			} catch (Exception e) {
				log.warn("Failed to fetch pull requests batch (skip: {}, top: {}) from Azure DevOps: {}",
						queryParams.skip, queryParams.top, e.getMessage());
				hasMore = false;
			}
		}
	}

	private List<GitPullRequest> filterPullRequestsByDate(List<GitPullRequest> pullRequests, LocalDateTime since) {
		if (since == null) {
			return pullRequests;
		}

		return pullRequests.stream().filter(createPullRequestDateFilter(since)).toList();
	}

	private Predicate<GitPullRequest> createPullRequestDateFilter(LocalDateTime since) {
		return pr -> {
			if (pr.getCreationDate() == null) {
				return true; // Include PRs without date info
			}

			try {
				String dateStr = pr.getCreationDate();
				LocalDateTime prDate = LocalDateTime.parse(dateStr.substring(0, 19));
				return !prDate.isBefore(since);
			} catch (Exception e) {
				log.warn("Failed to parse pull request date: {}", e.getMessage());
				return true; // Include PRs with unparseable dates
			}
		};
	}

	public long getPullRequestPickupTime(String organization, String project, String repository, String token,
			GitPullRequest azurePrId) {
		long prPickupTime = 0L;
		try {
			WebClient webClient = createWebClient(token);

			String creationDateStr = azurePrId.getCreationDate();
			if (creationDateStr == null || creationDateStr.isEmpty()) {
				log.warn("PR creation date is null for PR ID: {}", azurePrId);
				return prPickupTime;
			}

			JsonNode threadsArray = fetchPullRequestThreads(webClient, organization, project, repository, azurePrId);
			if (threadsArray == null || threadsArray.isEmpty()) {
				log.debug("No threads found for PR ID: {}", azurePrId);
				return prPickupTime;
			}

			prPickupTime = calculatePickupTime(creationDateStr, threadsArray, azurePrId);

		} catch (Exception e) {
			log.error("Failed to fetch pull request pickup time from Azure DevOps repository {}/{}/{}: {}",
					organization, project, repository, e.getMessage());
		}
		return prPickupTime;
	}

	private WebClient createWebClient(String token) {
		String credentials = "Basic " + Base64.getEncoder().encodeToString((":" + token).getBytes());
		int bufferSize = 1024 * 1024;

		return webClientBuilder.baseUrl(azureDevOpsApiUrl).defaultHeader(HttpHeaders.AUTHORIZATION, credentials)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(bufferSize)).build();
	}

	private JsonNode fetchPullRequestThreads(WebClient webClient, String organization, String project,
			String repository, GitPullRequest azurePrId) throws JsonProcessingException {
		String threadsUrl = String.format("/%s/%s/_apis/git/repositories/%s/pullrequests/%s/threads?api-version=7.1",
				organization, project, repository, azurePrId.getPullRequestId().toString());

		String threadsResponse = webClient.get().uri(threadsUrl).retrieve().bodyToMono(String.class).block();

		JsonNode rootNode = objectMapper.readTree(threadsResponse);
		return rootNode.path("value");
	}

	private long calculatePickupTime(String creationDateStr, JsonNode threadsArray, GitPullRequest azurePrId) {
		LocalDateTime creationTime = LocalDateTime.parse(creationDateStr.substring(0, 19));
		LocalDateTime firstReviewTime = findFirstReviewTime(threadsArray, creationTime);

		if (firstReviewTime != null) {
			long pickupTime = firstReviewTime.toInstant(ZoneOffset.UTC).toEpochMilli();
			log.debug("PR pickup time for PR #{}: {} ms", azurePrId, pickupTime);
			return pickupTime;
		} else {
			log.debug("No review activity found for PR #{}", azurePrId);
			return 0L;
		}
	}

	private LocalDateTime findFirstReviewTime(JsonNode threadsArray, LocalDateTime creationTime) {
		LocalDateTime firstReviewTime = null;

		for (JsonNode thread : threadsArray) {
			JsonNode comments = thread.path("comments");
			if (comments.isMissingNode() || comments.isEmpty()) {
				continue;
			}

			LocalDateTime earliestCommentTime = findEarliestCommentTime(comments, creationTime, firstReviewTime);
			if (earliestCommentTime != null
					&& (firstReviewTime == null || earliestCommentTime.isBefore(firstReviewTime))) {
				firstReviewTime = earliestCommentTime;
			}
		}

		return firstReviewTime;
	}

	private LocalDateTime findEarliestCommentTime(JsonNode comments, LocalDateTime creationTime,
			LocalDateTime currentFirstReviewTime) {
		LocalDateTime earliestTime = null;

		for (JsonNode comment : comments) {
			String commentDateStr = comment.path("publishedDate").asText();
			if (commentDateStr == null || commentDateStr.isEmpty()) {
				continue;
			}

			LocalDateTime commentTime = parseCommentTime(commentDateStr);
			if (commentTime != null && isValidReviewTime(commentTime, creationTime, currentFirstReviewTime)
					&& (earliestTime == null || commentTime.isBefore(earliestTime))) {
				earliestTime = commentTime;
			}
		}

		return earliestTime;
	}

	private LocalDateTime parseCommentTime(String commentDateStr) {
		try {
			return LocalDateTime.parse(commentDateStr.substring(0, 19));
		} catch (Exception e) {
			log.warn("Failed to parse comment date: {}", commentDateStr);
			return null;
		}
	}

	private boolean isValidReviewTime(LocalDateTime commentTime, LocalDateTime creationTime,
			LocalDateTime currentFirstReviewTime) {
		return commentTime.isAfter(creationTime)
				&& (currentFirstReviewTime == null || commentTime.isBefore(currentFirstReviewTime));
	}

	public ScmCommits.FileChange getCommitDiffStats(String organization, String project, String repository,
			String commitId, String token) {

		ScmCommits.FileChange fileChange = new ScmCommits.FileChange();
		try {
			AzDClientApi client = createClient(token, project, organization);
            GitDetails gitApi = client.getGitApi();
			GitCommitChanges commit = gitApi.getChanges(repository, commitId);

			if (commit == null || commit.getChangeCounts() == null) {
				log.warn("No change counts found for commit: {}", commitId);
				return new ScmCommits.FileChange();
			}

			log.debug("Fetched diff stats for commit {}: {}", commitId, fileChange);

		} catch (Exception e) {
			log.error("Failed to fetch diff stats for commit {}: {}", commitId, e.getMessage());
		}
		return fileChange;
	}

	/**
	 * Fetches all repositories accessible with the given credentials that were
	 * updated after the specified date, along with their branches that were also
	 * updated after that date.
	 *
	 * @param token
	 *            Azure DevOps personal access token
	 * @param organization
	 *            Azure DevOps organization name
	 * @param sinceDate
	 *            Date filter - only repos/branches updated after this date will be
	 *            included
	 * @return Map where key is repository (format: project/repo) and value is list
	 *         of branch names
	 * @throws Exception
	 *             if fetching repositories or branches fails
	 */
	public List<ScmRepos> fetchRepositories(String token, String organization, LocalDateTime sinceDate,
			ObjectId connectionId) throws Exception {

		List<ScmRepos> repositoriesWithBranches = new ArrayList<>();

		try {
			// Create client for organization level access
			AzDClientApi client = createClient(token, null, organization);
			CoreDetails coreApi = client.getCoreApi();

			log.info("Fetching all accessible projects from organization: {}", organization);

			// Get all projects in the organization
			var projectsResponse = coreApi.getProjects();
			List<Project> projects = projectsResponse.getProjects();

			if (projects == null || projects.isEmpty()) {
				log.warn("No projects found in organization: {}", organization);
				return repositoriesWithBranches;
			}

			processProjects(projects, organization, token, sinceDate, connectionId, repositoriesWithBranches);

			log.info("Successfully fetched {} repositories with branches from organization: {}",
					repositoriesWithBranches.size(), organization);

		} catch (Exception e) {
			log.error("Failed to fetch accessible repositories from organization {}: {}", organization, e.getMessage());
			throw new RepositoryException("Failed to fetch accessible repositories", e);
		}

		return repositoriesWithBranches;
	}

	private void processProjects(List<Project> projects, String organization, String token, LocalDateTime sinceDate,
			ObjectId connectionId, List<ScmRepos> repositoriesWithBranches) {

		for (Project projectRef : projects) {
			processProject(projectRef, organization, token, sinceDate, connectionId, repositoriesWithBranches);
		}
	}

	private void processProject(Project projectRef, String organization, String token, LocalDateTime sinceDate,
			ObjectId connectionId, List<ScmRepos> repositoriesWithBranches) {
		try {
			String projectName = projectRef.getName();
			log.debug("Processing project: {}", projectName);

			// Create project-specific GitApi
			AzDClientApi client = createClient(token, projectName, organization);
			GitDetails projectGitApi = client.getGitApi();

			// Get all repositories in the project
			var reposResponse = projectGitApi.getRepositories();
			List<GitRepository> repositories = reposResponse.getRepositories();

			if (repositories == null || repositories.isEmpty()) {
				log.debug("No repositories found in project: {}", projectName);
				return;
			}

			// Process each repository
			for (GitRepository repo : repositories) {
				ScmRepos scmRepos = ScmRepos.builder().repositoryName(repo.getName()).connectionId(connectionId)
						.url(repo.getRemoteUrl()).build();
				processRepository(projectGitApi, scmRepos, projectName, sinceDate);
				log.info("Repository Name: {}", scmRepos.getRepositoryName());
				if (!CollectionUtils.isEmpty(scmRepos.getBranchList())) {
					repositoriesWithBranches.add(scmRepos);
				}
			}

		} catch (Exception e) {
			log.warn("Failed to process project {}: {}", projectRef.getName(), e.getMessage());
			// Continue with other projects
		}
	}

	/**
	 * Processes a single repository to check if it was updated after the given date
	 * and fetches its branches that were also updated after that date.
	 */
	private void processRepository(GitDetails gitApi, ScmRepos repo, String projectName, LocalDateTime sinceDate) {

		try {
			String repoName = repo.getRepositoryName();
			String repoKey = projectName + "/" + repoName;

			// Check if repository has commits after the specified date
			GitCommitsBatch commitsBatch = new GitCommitsBatch();
			commitsBatch.top = 1;
			commitsBatch.skip = 0;
			commitsBatch.fromDate = sinceDate.toString();
			commitsBatch.showOldestCommitsFirst = false;

			GitCommitRefs commitRefs = gitApi.getCommitsBatch(repoName, commitsBatch);

			if (commitRefs.getGitCommitRefs() == null || commitRefs.getGitCommitRefs().isEmpty()) {
				log.debug("Repository {} has no commits after {}", repoKey, sinceDate);
				return;
			}

			// Repository has recent commits, now get branches
			List<ScmBranch> recentBranches = getRecentBranches(gitApi, repoName, sinceDate);

			if (!recentBranches.isEmpty()) {
				repo.setBranchList(recentBranches);
				repo.setLastUpdated(recentBranches.stream().map(ScmBranch::getLastUpdatedAt).filter(Objects::nonNull)
						.max(Long::compareTo).orElse(0L));
				log.debug("Repository {} has {} branches updated after {}", repoKey, recentBranches.size(), sinceDate);
			}

		} catch (Exception e) {
			log.warn("Failed to process repository {}/{}: {}", projectName, repo.getRepositoryName(), e.getMessage());
		}
	}

	private List<ScmBranch> getRecentBranches(GitDetails gitApi, String repositoryName, LocalDateTime sinceDate) {
		List<ScmBranch> recentBranches = new ArrayList<>();

		try {
			// Get all branches (refs)
			var branchesResponse = gitApi.getRefs(repositoryName, "heads");
			List<GitRef> branches = branchesResponse.getRefs();

			if (branches == null || branches.isEmpty()) {
				return recentBranches;
			}

			processBranches(branches, gitApi, repositoryName, sinceDate, recentBranches);

		} catch (Exception e) {
			log.warn("Failed to get branches for repository {}: {}", repositoryName, e.getMessage());
		}

		return recentBranches;
	}

	private void processBranches(List<GitRef> branches, GitDetails gitApi, String repositoryName, LocalDateTime sinceDate,
			List<ScmBranch> recentBranches) {
		for (GitRef branch : branches) {
			processSingleBranch(branch, gitApi, repositoryName, sinceDate, recentBranches);
		}
	}

	private void processSingleBranch(GitRef branch, GitDetails gitApi, String repositoryName, LocalDateTime sinceDate,
			List<ScmBranch> recentBranches) {
		try {
			String branchName = branch.getName().replace("refs/heads/", "");

			// Get the latest commit for this branch
			GitCommitsBatch branchCommitsBatch = new GitCommitsBatch();
			branchCommitsBatch.top = 1;
			branchCommitsBatch.skip = 0;
			branchCommitsBatch.fromDate = sinceDate.toString();
			branchCommitsBatch.showOldestCommitsFirst = false;

			GitVersionDescriptor versionDescriptor = new GitVersionDescriptor();
			versionDescriptor.version = branchName;
			branchCommitsBatch.itemVersion = versionDescriptor;

			GitCommitRefs branchCommits = gitApi.getCommitsBatch(repositoryName, branchCommitsBatch);

			if (branchCommits.getGitCommitRefs() != null && !branchCommits.getGitCommitRefs().isEmpty()) {
				ScmBranch scmBranch = ScmBranch
						.builder().name(branchName).lastUpdatedAt(Instant
								.parse(branchCommits.getGitCommitRefs().get(0).getAuthor().getDate()).toEpochMilli())
						.build();
				recentBranches.add(scmBranch);
			}

		} catch (Exception e) {
			log.debug("Failed to check branch {}: {}", branch.getName(), e.getMessage());
		}
	}

	/**
	 * Gets the API URL for Azure DevOps.
	 *
	 * @return the Azure DevOps API URL
	 */
	public String getApiUrl() {
		return azureDevOpsApiUrl;
	}
}
