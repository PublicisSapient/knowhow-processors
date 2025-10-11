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
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import lombok.extern.slf4j.Slf4j;
import org.azd.connection.Connection;
import org.azd.git.GitApi;
import org.azd.git.types.GitCommitChanges;
import org.azd.git.types.GitCommitRefs;
import org.azd.git.types.GitCommitsBatch;
import org.azd.git.types.GitPullRequest;
import org.azd.git.types.GitPullRequestQueryParameters;
import org.azd.git.types.GitRepository;
import org.azd.git.types.GitCommitRef;
import org.azd.enums.PullRequestStatus;
import org.azd.wiki.types.GitVersionDescriptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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

	/**
	 * Creates and returns an authenticated Azure DevOps connection instance.
	 *
	 * @param token
	 *            Azure DevOps personal access token
	 * @param organization
	 *            Azure DevOps organization name
	 * @return Connection instance
	 * @throws Exception
	 *             if authentication fails
	 */
	public Connection getAzureDevOpsConnection(String token, String organization, String project) throws Exception {
		if (token == null || token.trim().isEmpty()) {
			throw new IllegalArgumentException("Azure DevOps token cannot be null or empty");
		}

		if (organization == null || organization.trim().isEmpty()) {
			throw new IllegalArgumentException("Azure DevOps organization cannot be null or empty");
		}

		try {
			Connection connection = new Connection(organization, project, token);

			log.debug("Successfully authenticated with Azure DevOps API for organization: {}", organization);
			return connection;

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
		Connection connection = getAzureDevOpsConnection(token, organization, project);
		GitApi gitApi = new GitApi(connection);

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
		Connection connection = getAzureDevOpsConnection(token, organization, project);
		GitApi gitApi = new GitApi(connection);

		List<GitCommitRef> allCommits = new ArrayList<>();

		try {
			log.info("Fetching commits from Azure DevOps repository: {}/{}/{} (branch: {})", organization, project,
					repository, branchName != null ? branchName : "default");

			// CHANGE: Extract batch configuration to reduce complexity
			GitCommitsBatch gitCommitsBatch = createCommitsBatch(branchName, since);

			// CHANGE: Extract pagination logic to separate method
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

	// CHANGE: New helper method to create commits batch configuration
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

	// CHANGE: New helper method to handle pagination logic
	private void fetchCommitsWithPagination(GitApi gitApi, String repository, GitCommitsBatch gitCommitsBatch,
			List<GitCommitRef> allCommits, LocalDateTime since, LocalDateTime until) {
		boolean hasMore = true;

		while (hasMore) {
			try {
				GitCommitRefs commitRefs = gitApi.getCommitsBatch(repository, gitCommitsBatch);
				List<GitCommitRef> commitRefsList = commitRefs.getGitCommitRefs();

				if (commitRefsList == null || commitRefsList.isEmpty()) {
					break;
				}

				// CHANGE: Extract date filtering to separate method
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

	// CHANGE: New helper method to filter commits by date
	private List<GitCommitRef> filterCommitsByDate(List<GitCommitRef> commits, LocalDateTime since,
			LocalDateTime until) {
		return commits.stream().filter(createCommitDateFilter(since, until)).toList();
	}

	// CHANGE: New helper method to create date filter predicate
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
		Connection connection = getAzureDevOpsConnection(token, organization, project);
		GitApi gitApi = new GitApi(connection);

		List<GitPullRequest> allPullRequests = new ArrayList<>();

		try {
			log.info("Fetching pull requests from Azure DevOps repository: {}/{}/{}", organization, project,
					repository);

			// CHANGE: Extract query parameters creation to reduce complexity
			GitPullRequestQueryParameters queryParams = createPullRequestQueryParams(branch);

			// CHANGE: Extract pagination logic to separate method
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

	// CHANGE: New helper method to create pull request query parameters
	private GitPullRequestQueryParameters createPullRequestQueryParams(String branch) {
		GitPullRequestQueryParameters queryParams = new GitPullRequestQueryParameters();
		queryParams.top = 100;
		queryParams.skip = 0;
		queryParams.status = PullRequestStatus.ALL;
		queryParams.targetRefName = "refs/heads/" + branch;
		return queryParams;
	}

	// CHANGE: New helper method to handle pull request pagination
	private void fetchPullRequestsWithPagination(GitApi gitApi, String repository,
			GitPullRequestQueryParameters queryParams, List<GitPullRequest> allPullRequests, LocalDateTime since) {
		boolean hasMore = true;

		while (hasMore) {
			try {
				var pullRequestsResponse = gitApi.getPullRequests(repository, queryParams);
				List<GitPullRequest> pullRequests = pullRequestsResponse.getPullRequests();

				if (pullRequests == null || pullRequests.isEmpty()) {
					break;
				}

				// CHANGE: Use extracted method for date filtering
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

	// CHANGE: New helper method to filter pull requests by date
	private List<GitPullRequest> filterPullRequestsByDate(List<GitPullRequest> pullRequests, LocalDateTime since) {
		if (since == null) {
			return pullRequests;
		}

		return pullRequests.stream().filter(createPullRequestDateFilter(since)).toList();
	}

	// CHANGE: New helper method to create pull request date filter
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
			// CHANGE: Extract WebClient creation to reduce complexity
			WebClient webClient = createWebClient(token);

			String creationDateStr = azurePrId.getCreationDate();
			if (creationDateStr == null || creationDateStr.isEmpty()) {
				log.warn("PR creation date is null for PR ID: {}", azurePrId);
				return prPickupTime;
			}

			// CHANGE: Extract threads fetching logic to reduce complexity
			JsonNode threadsArray = fetchPullRequestThreads(webClient, organization, project, repository, azurePrId);
			if (threadsArray == null || threadsArray.isEmpty()) {
				log.debug("No threads found for PR ID: {}", azurePrId);
				return prPickupTime;
			}

			// CHANGE: Extract pickup time calculation to reduce complexity
			prPickupTime = calculatePickupTime(creationDateStr, threadsArray, azurePrId);

		} catch (Exception e) {
			log.error("Failed to fetch pull request pickup time from Azure DevOps repository {}/{}/{}: {}",
					organization, project, repository, e.getMessage());
		}
		return prPickupTime;
	}

	// CHANGE: New helper method to create WebClient
	private WebClient createWebClient(String token) {
		String credentials = "Basic " + Base64.getEncoder().encodeToString((":" + token).getBytes());
		int bufferSize = 1024 * 1024;

		return webClientBuilder.baseUrl(azureDevOpsApiUrl).defaultHeader(HttpHeaders.AUTHORIZATION, credentials)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(bufferSize)).build();
	}

	// CHANGE: New helper method to fetch PR threads
	private JsonNode fetchPullRequestThreads(WebClient webClient, String organization, String project,
			String repository, GitPullRequest azurePrId) throws JsonProcessingException {
		String threadsUrl = String.format("/%s/%s/_apis/git/repositories/%s/pullrequests/%s/threads?api-version=7.1",
				organization, project, repository, azurePrId.getPullRequestId().toString());

		String threadsResponse = webClient.get().uri(threadsUrl).retrieve().bodyToMono(String.class).block();

		JsonNode rootNode = objectMapper.readTree(threadsResponse);
		return rootNode.path("value");
	}

	// CHANGE: New helper method to calculate pickup time
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

	// CHANGE: New helper method to find first review time
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
            if (commentTime != null && isValidReviewTime(commentTime, creationTime, currentFirstReviewTime) &&
                    (earliestTime == null || commentTime.isBefore(earliestTime))) {
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
			Connection connection = getAzureDevOpsConnection(token, organization, project);
			GitApi gitApi = new GitApi(connection);
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
	 * Gets the API URL for Azure DevOps.
	 *
	 * @return the Azure DevOps API URL
	 */
	public String getApiUrl() {
		return azureDevOpsApiUrl;
	}
}
