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

package com.publicissapient.knowhow.processor.scm.client.gitlab;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.bson.types.ObjectId;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;

import lombok.extern.slf4j.Slf4j;

/**
 * GitLab API client for interacting with GitLab repositories. Handles
 * authentication, rate limiting, and data fetching operations.
 */
@Component
@Slf4j
public class GitLabClient {

	private static final int GITLAB_API_MAX_PER_PAGE = 100;
	private static final int HTTP_STATUS_RATE_LIMIT = 429;
	private static final int HTTP_STATUS_BAD_REQUEST = 400;
	private static final int HTTP_STATUS_SERVICE_UNAVAILABLE = 503;
	private static final int MIN_REVIEWER_COMMENT_LENGTH = 3;
	private static final String PROJECT_PATH_SEPARATOR = "/";

	@Value("${git.platforms.gitlab.api-url:https://gitlab.com}")
	private String defaultGitlabApiUrl;

	private final GitUrlParser gitUrlParser;

	@Value("${git.scanner.pagination.max-commits-per-scan:1000}")
	private int maxCommitsPerScan;

	@Value("${git.scanner.pagination.max-merge-requests-per-scan:500}")
	private int maxMergeRequestsPerScan;

	private final RateLimitService rateLimitService;

	GitLabClient(GitUrlParser gitUrlParser, RateLimitService rateLimitService) {
		this.gitUrlParser = gitUrlParser;
		this.rateLimitService = rateLimitService;
	}

	/**
	 * Creates and returns an authenticated GitLab API client for the default GitLab
	 * instance
	 */
	public GitLabApi getGitLabClient(String token) throws GitLabApiException {
		return getGitLabClient(token, defaultGitlabApiUrl);
	}

	/**
	 * Creates and returns an authenticated GitLab API client for a specific GitLab
	 * instance
	 */
	public GitLabApi getGitLabClient(String token, String apiBaseUrl) throws GitLabApiException {
		validateToken(token);
		String effectiveApiUrl = getEffectiveApiUrl(apiBaseUrl);

		try {
			GitLabApi gitLabApi = new GitLabApi(effectiveApiUrl, token);
			// Test the connection
			gitLabApi.getUserApi().getCurrentUser();
			log.info("Successfully authenticated with GitLab API at {}", effectiveApiUrl);
			return gitLabApi;
		} catch (GitLabApiException e) {
			log.error("Failed to authenticate with GitLab API at {}: {}", effectiveApiUrl, e.getMessage());
			throw e;
		}
	}

	/**
	 * Creates and returns an authenticated GitLab API client based on repository
	 * URL
	 */
	public GitLabApi getGitLabClientFromRepoUrl(String token, String repositoryUrl) throws GitLabApiException {
		try {
			String apiBaseUrl = gitUrlParser.getGitLabApiBaseUrl(repositoryUrl);
			return getGitLabClient(token, apiBaseUrl);
		} catch (Exception e) {
			log.warn("Failed to extract API URL from repository URL {}, falling back to default: {}", repositoryUrl,
					e.getMessage());
			return getGitLabClient(token);
		}
	}

	/**
	 * Fetches commits from a GitLab repository with date filtering
	 */
	public List<Commit> fetchCommits(String organization, String repository, String branchName, String token,
			LocalDateTime since, LocalDateTime until, String repositoryUrl) throws GitLabApiException {

		GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);
		String projectPath = buildProjectPath(organization, repository);

		try {
			log.info("Starting to fetch commits from GitLab repository {} on branch {} with date range: {} to {}",
					projectPath, branchName, since, until);

			Project project = getProjectWithRateLimit(gitLabApi, projectPath, token);

			Date sinceDate = convertToDate(since);
			Date untilDate = convertToDate(until);

			return fetchCommitsPaginated(gitLabApi, project, branchName, sinceDate, untilDate, projectPath, token);

		} catch (GitLabApiException e) {
			log.error("Failed to fetch commits from GitLab repository {} on branch {}: {} (HTTP Status: {})",
					repository, branchName, e.getMessage(), e.getHttpStatus());
			throw e;
		} catch (Exception e) {
			log.error("Unexpected error while fetching commits from GitLab repository {} on branch {}: {}", repository,
					branchName, e.getMessage(), e);
			throw new GitLabApiException("Unexpected error during commit fetch", HTTP_STATUS_SERVICE_UNAVAILABLE);
		}
	}

	/**
	 * Fetches merge requests from a GitLab repository with date and branch
	 * filtering
	 */
	public List<MergeRequest> fetchMergeRequests(String organization, String repository, String branchName,
			String token, LocalDateTime since, LocalDateTime until, String repositoryUrl) throws GitLabApiException {

		GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);
		String projectPath = buildProjectPath(organization, repository);

		log.info(
				"Starting to fetch merge requests from GitLab repository {} with branch filter: {} and date range: {} to {}",
				projectPath, branchName, since, until);

		try {
			Project project = getProjectWithRateLimit(gitLabApi, projectPath, token);
			Date sinceDate = convertToDate(since);

			MergeRequestFilter filter = createMergeRequestFilter(project.getId(), sinceDate, branchName);

			return fetchMergeRequestsPaginated(gitLabApi, project, filter, branchName, projectPath, token);

		} catch (GitLabApiException e) {
			log.error("Failed to fetch merge requests from GitLab repository {}: {}", projectPath, e.getMessage());
			throw e;
		}
	}

	/**
	 * Fetches all repositories accessible to the user that were updated after the
	 * specified date, along with their branches that were also updated after the
	 * same date.
	 *
	 * @param token
	 *            GitLab access token
	 * @param updatedAfter
	 *            Date filter - only repos/branches updated after this date
	 * @param apiBaseUrl
	 *            Optional GitLab API base URL (uses default if null)
	 * @return List of repositories with their filtered branches
	 * @throws GitLabApiException
	 *             if API call fails
	 */
	public List<ScmRepos> fetchRepositories(String token, LocalDateTime updatedAfter, String apiBaseUrl,
			ObjectId connectionId) throws GitLabApiException {

		GitLabApi gitLabApi = getGitLabClient(token, apiBaseUrl);
		List<ScmRepos> result = new ArrayList<>();
		Date updatedAfterDate = convertToDate(updatedAfter);

		try {
			log.info("Starting to fetch repositories updated after {}", updatedAfter);

			// Fetch all accessible projects with pagination
			ProjectFilter projectFilter = new ProjectFilter().withMembership(true) // Only projects user is member of
					.withLastActivityAfter(updatedAfterDate).withOrderBy(Constants.ProjectOrderBy.LAST_ACTIVITY_AT)
					.withSortOder(Constants.SortOrder.DESC);

			Pager<Project> projectPager = gitLabApi.getProjectApi().getProjects(projectFilter, GITLAB_API_MAX_PER_PAGE);

			// CHANGE: Extracted project processing to eliminate nested try-catch
			int projectCount = processProjects(projectPager, gitLabApi, result, updatedAfterDate, updatedAfter,
					connectionId, token);

			log.info("Successfully fetched {} repositories with branches updated after {}", projectCount, updatedAfter);
			return result;

		} catch (GitLabApiException e) {
			log.error("Failed to fetch repositories: {}", e.getMessage());
			throw e;
		}
	}

	private int processProjects(Pager<Project> projectPager, GitLabApi gitLabApi, List<ScmRepos> result,
			Date updatedAfterDate, LocalDateTime updatedAfter, ObjectId connectionId, String token) {

		int projectCount = 0;

		while (projectPager.hasNext()) {
			checkRateLimitForProject(gitLabApi, "user-projects", token);

			List<Project> projects = projectPager.next();
			if (projects == null || projects.isEmpty()) {
				break;
			}

			for (Project project : projects) {
				if (processProject(project, gitLabApi, result, updatedAfterDate, updatedAfter, connectionId, token)) {
					projectCount++;
				}
			}
		}

		return projectCount;
	}

	private boolean processProject(Project project, GitLabApi gitLabApi, List<ScmRepos> result, Date updatedAfterDate,
			LocalDateTime updatedAfter, ObjectId connectionId, String token) {
		try {
			// Fetch branches for each project
			List<ScmBranch> filteredBranches = fetchBranchesUpdatedAfter(gitLabApi, project, updatedAfterDate, token);

			if (!filteredBranches.isEmpty()) {
				log.info("Repository Name: {}", project.getName());
				ScmRepos repo = ScmRepos.builder().repositoryName(project.getName()).branchList(filteredBranches)
						.lastUpdated(project.getLastActivityAt().toInstant().toEpochMilli()).connectionId(connectionId)
						.build();
				result.add(repo);

				log.debug("Found {} branches for project {} updated after {}", filteredBranches.size(),
						project.getPathWithNamespace(), updatedAfter);
				return true;
			}
			return false;
		} catch (GitLabApiException e) {
			log.warn("Failed to fetch branches for project {}: {}", project.getPathWithNamespace(), e.getMessage());
			return false;
		}
	}

	/**
	 * Fetches branches for a project that were updated after the specified date
	 */
	private List<ScmBranch> fetchBranchesUpdatedAfter(GitLabApi gitLabApi, Project project, Date updatedAfter,
			String token) throws GitLabApiException {

		List<ScmBranch> filteredBranches = new ArrayList<>();

		try {
			// GitLab API doesn't support filtering branches by last activity date directly,
			// so we need to fetch all branches and filter manually
			Pager<Branch> branchPager = gitLabApi.getRepositoryApi().getBranches(project.getId(),
					GITLAB_API_MAX_PER_PAGE);

			while (branchPager.hasNext()) {
				checkRateLimitForProject(gitLabApi, project.getPathWithNamespace(), token);

				List<Branch> branches = branchPager.next();
				if (branches == null || branches.isEmpty()) {
					break;
				}

				for (Branch branch : branches) {
					// Check if branch's last commit is after the specified date
					if (branch.getCommit() != null && branch.getCommit().getCommittedDate() != null
							&& branch.getCommit().getCommittedDate().after(updatedAfter)) {
						ScmBranch scmBranch = ScmBranch.builder().name(branch.getName())
								.lastUpdatedAt(branch.getCommit().getCommittedDate().toInstant().toEpochMilli())
								.build();
						filteredBranches.add(scmBranch);
					}
				}
			}

			return filteredBranches;

		} catch (GitLabApiException e) {
			log.error("Failed to fetch branches for project {}: {}", project.getPathWithNamespace(), e.getMessage());
			throw e;
		}
	}

	public long getPrPickUpTimeStamp(String organization, String repository, String token, String repositoryUrl,
			Long mrId) throws GitLabApiException {

		try {
			GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);
			String projectPath = buildProjectPath(organization, repository);

			log.debug("Getting first reviewer action timestamp for MR !{} from GitLab repository {}", mrId,
					projectPath);

			Project project = getProjectWithRateLimit(gitLabApi, projectPath, token);

			// Fetch merge request details to get creation time
			MergeRequest mergeRequest = gitLabApi.getMergeRequestApi().getMergeRequest(project.getId(), mrId);
			Date mrCreatedAt = mergeRequest.getCreatedAt();

			// Fetch all notes for the merge request
			List<Note> notes = gitLabApi.getNotesApi().getMergeRequestNotes(project.getId(), mrId);

			return findFirstReviewerActionTimestamp(notes, mergeRequest, mrCreatedAt, mrId);

		} catch (NumberFormatException e) {
			log.error("Invalid merge request ID format: {}", mrId);
			throw new GitLabApiException("Invalid merge request ID: " + mrId, HTTP_STATUS_BAD_REQUEST);
		} catch (GitLabApiException e) {
			log.error("Failed to get first reviewer action timestamp for MR {} from repository {}: {}", mrId,
					buildProjectPath(organization, repository), e.getMessage());
			throw e;
		}
	}

	/**
	 * Fetches commit diffs from a GitLab repository
	 */
	public List<Diff> fetchCommitDiffs(String owner, String repository, String commitSha, String token,
			String repositoryUrl) throws GitLabApiException {

		return fetchDiffsInternal(owner, repository, token, repositoryUrl,
				(gitLabApi, project) -> gitLabApi.getCommitsApi().getDiff(project.getId(), commitSha), "commit",
				commitSha);
	}

	/**
	 * Fetches merge request changes from a GitLab repository
	 */
	public List<Diff> fetchMergeRequestChanges(String owner, String repository, Long mergeRequestIid, String token,
			String repositoryUrl) throws GitLabApiException {

		return fetchDiffsInternal(owner, repository, token, repositoryUrl, (gitLabApi, project) -> {
			MergeRequest mr = gitLabApi.getMergeRequestApi().getMergeRequestChanges(project.getId(), mergeRequestIid);
			return mr.getChanges();
		}, "merge request", "!" + mergeRequestIid);
	}

	/**
	 * Fetches commits for a specific merge request
	 */
	public List<Commit> fetchMergeRequestCommits(String owner, String repository, Long mergeRequestIid, String token,
			String repositoryUrl) throws GitLabApiException {

		GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);
		String projectPath = buildProjectPath(owner, repository);

		try {
			Project project = getProjectWithRateLimit(gitLabApi, projectPath, token);
			List<Commit> commits = gitLabApi.getMergeRequestApi().getCommits(project.getId(), mergeRequestIid);

			log.debug("Fetched {} commits for merge request !{} from GitLab repository {}",
					commits != null ? commits.size() : 0, mergeRequestIid, projectPath);
			return commits != null ? commits : new ArrayList<>();

		} catch (GitLabApiException e) {
			log.warn("Failed to fetch commits for merge request !{} from GitLab repository {}: {}", mergeRequestIid,
					projectPath, e.getMessage());
			return new ArrayList<>();
		}
	}

	private void validateToken(String token) throws GitLabApiException {
		if (token == null || token.trim().isEmpty()) {
			throw new GitLabApiException("GitLab token cannot be null or empty");
		}
	}

	private String getEffectiveApiUrl(String apiBaseUrl) {
		if (apiBaseUrl == null || apiBaseUrl.trim().isEmpty()) {
			return defaultGitlabApiUrl;
		}
		return apiBaseUrl;
	}

	private String buildProjectPath(String organization, String repository) {
		return organization + PROJECT_PATH_SEPARATOR + repository;
	}

	private Date convertToDate(LocalDateTime dateTime) {
		return dateTime != null ? Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()) : null;
	}

	private Project getProjectWithRateLimit(GitLabApi gitLabApi, String projectPath, String token)
			throws GitLabApiException {
		checkRateLimitForProject(gitLabApi, projectPath, token);
		return gitLabApi.getProjectApi().getProject(projectPath);
	}

	private void checkRateLimitForProject(GitLabApi gitLabApi, String projectPath, String token) {
		rateLimitService.checkRateLimit("GitLab", token, projectPath, gitLabApi.getGitLabServerUrl());
	}

	private List<Commit> fetchCommitsPaginated(GitLabApi gitLabApi, Project project, String branchName, Date sinceDate,
			Date untilDate, String projectPath, String token) throws GitLabApiException {

		List<Commit> allCommits = new ArrayList<>();
		int page = 1;
		int perPage = GITLAB_API_MAX_PER_PAGE;
		int totalFetched = 0;
		boolean hasNext = true;

		log.debug("Starting pagination for repository {} on branch {}", projectPath, branchName);

		Pager<Commit> fetchedCommits = gitLabApi.getCommitsApi().getCommits(project.getId(), branchName, sinceDate,
				untilDate, perPage);

		while (hasNext) {
			checkRateLimitForProject(gitLabApi, projectPath, token);

			List<Commit> commits = fetchPageOfCommits(fetchedCommits, page, perPage, projectPath);
			if (commits.isEmpty()) {
				break;
			}

			int commitsAdded = addCommitsUpToLimit(allCommits, commits, totalFetched, maxCommitsPerScan);
			totalFetched += commitsAdded;

			if (shouldStopPagination(commits.size(), perPage) || (!fetchedCommits.hasNext())) {
				hasNext = false;
			}
			page++;
		}

		log.info("Successfully fetched {} commits from GitLab repository {} on branch {} (pages processed: {})",
				allCommits.size(), projectPath, branchName, page - 1);
		return allCommits;
	}

	private List<Commit> fetchPageOfCommits(Pager<Commit> pager, int page, int perPage, String projectPath) {
		log.debug("Fetching page {} with {} commits per page from repository {}", page, perPage, projectPath);
		List<Commit> commits = pager.next();

		if (commits == null) {
			log.debug("No more commits found for repository {}", projectPath);
			return Collections.emptyList();
		}
		return commits;
	}

	private int addCommitsUpToLimit(List<Commit> allCommits, List<Commit> newCommits, int totalFetched, int limit) {
		int commitsToAdd = Math.min(newCommits.size(), limit - totalFetched);
		allCommits.addAll(newCommits.subList(0, commitsToAdd));
		log.debug("Added {} commits (total so far: {})", commitsToAdd, totalFetched + commitsToAdd);
		return commitsToAdd;
	}

	private boolean shouldStopPagination(int pageSize, int expectedPageSize) {
		return pageSize < expectedPageSize;
	}

	private MergeRequestFilter createMergeRequestFilter(Object projectId, Date sinceDate, String branchName) {
		return new MergeRequestFilter().withProjectId((Long) projectId).withState(Constants.MergeRequestState.ALL)
				.withUpdatedAfter(sinceDate).withTargetBranch(branchName);
	}

	private List<MergeRequest> fetchMergeRequestsPaginated(GitLabApi gitLabApi, Project project,
			MergeRequestFilter filter, String branchName, String projectPath, String token) throws GitLabApiException {

		List<MergeRequest> allMergeRequests = new ArrayList<>();
		int page = 1;
		int perPage = Math.min(GITLAB_API_MAX_PER_PAGE, maxMergeRequestsPerScan);
		boolean hasNext = true;

		while (hasNext) {
			log.debug("Fetching merge requests page {} for GitLab repository {}", page, projectPath);

			try {
				List<MergeRequest> pageMergeRequests = fetchMergeRequestPage(gitLabApi, filter, page, perPage);
				if (pageMergeRequests.isEmpty()) {
					break;
				}

				List<MergeRequest> filteredMergeRequests = filterMergeRequestsByBranch(pageMergeRequests, branchName);
				allMergeRequests.addAll(filteredMergeRequests);

				// Fetch notes for the first MR to warm up the API
				if (!pageMergeRequests.isEmpty()) {
					gitLabApi.getNotesApi().getMergeRequestNotes(project.getId(), pageMergeRequests.get(0).getIid());
				}

				log.debug("Fetched {} merge requests from page {} for GitLab repository {}",
						filteredMergeRequests.size(), page, projectPath);

				if (shouldStopPagination(pageMergeRequests.size(), perPage)) {
					hasNext = false;
				}
				page++;

			} catch (GitLabApiException e) {
				handleMergeRequestFetchError(e, page, projectPath, token, gitLabApi);
			}
		}

		log.info("Successfully fetched {} merge requests from GitLab repository {}", allMergeRequests.size(),
				projectPath);
		return allMergeRequests;
	}

	private List<MergeRequest> fetchMergeRequestPage(GitLabApi gitLabApi, MergeRequestFilter filter, int page,
			int perPage) throws GitLabApiException {
		List<MergeRequest> mergeRequests = gitLabApi.getMergeRequestApi().getMergeRequests(filter, page, perPage);
		return mergeRequests != null ? mergeRequests : Collections.emptyList();
	}

	private List<MergeRequest> filterMergeRequestsByBranch(List<MergeRequest> mergeRequests, String branchName) {
		if (branchName == null || branchName.isEmpty()) {
			return mergeRequests;
		}

		return mergeRequests.stream()
				.filter(mr -> branchName.equals(mr.getSourceBranch()) || branchName.equals(mr.getTargetBranch()))
				.toList();
	}

	private void handleMergeRequestFetchError(GitLabApiException e, int page, String projectPath, String token,
			GitLabApi gitLabApi) throws GitLabApiException {
		if (e.getHttpStatus() == HTTP_STATUS_RATE_LIMIT) {
			log.warn("Rate limit hit while fetching merge requests from {}, page {}: {}", projectPath, page,
					e.getMessage());
			checkRateLimitForProject(gitLabApi, projectPath, token);
		} else {
			log.error("API error while fetching merge requests from {} on page {}: {}", projectPath, page,
					e.getMessage());
			throw e;
		}
	}

	private long findFirstReviewerActionTimestamp(List<Note> notes, MergeRequest mergeRequest, Date mrCreatedAt,
			Long mrId) {

		if (notes == null || notes.isEmpty()) {
			log.debug("No notes found for MR !{}", mrId);
			return 0L;
		}

		// Sort notes by creation date
		List<Note> sortedNotes = new ArrayList<>(notes);
		sortedNotes.sort(this::compareNotesByCreationDate);

		String mrAuthorUsername = Optional.ofNullable(mergeRequest.getAuthor()).map(AbstractUser::getUsername)
				.orElse(null);

		for (Note note : sortedNotes) {
			if (isValidReviewerNote(note, mrCreatedAt, mrAuthorUsername)) {
				long firstReviewerActionTime = note.getCreatedAt().getTime();
				String noteAuthor = Optional.ofNullable(note.getAuthor()).map(AbstractUser::getUsername)
						.orElse("unknown");

				log.debug("Found first reviewer action timestamp {} for MR !{} by user {}", firstReviewerActionTime,
						mrId, noteAuthor);
				return firstReviewerActionTime;
			}
		}

		log.debug("No reviewer actions found for MR !{}", mrId);
		return 0L;
	}

	private int compareNotesByCreationDate(Note a, Note b) {
		if (a.getCreatedAt() == null && b.getCreatedAt() == null) {
			return 0;
		}
		if (a.getCreatedAt() == null) {
			return 1;
		}
		if (b.getCreatedAt() == null) {
			return -1;
		}
		return a.getCreatedAt().compareTo(b.getCreatedAt());
	}

	private boolean isValidReviewerNote(Note note, Date mrCreatedAt, String mrAuthorUsername) {
		// Skip notes created before or at the same time as MR creation
		if (note.getCreatedAt() == null || (mrCreatedAt != null && !note.getCreatedAt().after(mrCreatedAt))) {
			return false;
		}

		// Skip notes from the MR author
		if (mrAuthorUsername != null && note.getAuthor() != null
				&& mrAuthorUsername.equals(note.getAuthor().getUsername())) {
			return false;
		}

		return isReviewerAction(note);
	}

	private boolean isReviewerAction(Note note) {
		if (note.getBody() == null) {
			return false;
		}

		String body = note.getBody().toLowerCase().trim();

		// Check for system notes that indicate reviewer actions
		if (Boolean.TRUE.equals(note.getSystem())) {
			return isSystemReviewerAction(body);
		}

		// Check for regular comments that indicate reviewer engagement
		// Any non-empty comment from someone other than the author is considered a
		// reviewer action
		// Ignore very short comments like "ok", "👍"
		return body.length() >= MIN_REVIEWER_COMMENT_LENGTH;
	}

	private boolean isSystemReviewerAction(String body) {
		return body.contains("approved this merge request") || body.contains("unapproved this merge request")
				|| body.contains("requested changes") || body.contains("started a review")
				|| body.contains("requested review from") || body.contains("assigned to") || body.contains("unassigned")
				|| body.contains("marked as draft") || body.contains("marked as ready") || body.contains("closed")
				|| body.contains("reopened");
	}

	private interface DiffFetcher {
		List<Diff> fetch(GitLabApi api, Project project) throws GitLabApiException;
	}

	private List<Diff> fetchDiffsInternal(String owner, String repository, String token, String repositoryUrl,
			DiffFetcher fetcher, String entityType, String entityId) throws GitLabApiException {

		GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);
		String projectPath = buildProjectPath(owner, repository);

		try {
			Project project = getProjectWithRateLimit(gitLabApi, projectPath, token);
			List<Diff> diffs = fetcher.fetch(gitLabApi, project);

			log.debug("Fetched {} diffs for {} {} from GitLab repository {}", diffs != null ? diffs.size() : 0,
					entityType, entityId, projectPath);
			return diffs != null ? diffs : new ArrayList<>();

		} catch (GitLabApiException e) {
			log.warn("Failed to fetch diffs for {} {} from GitLab repository {}: {}", entityType, entityId, projectPath,
					e.getMessage());
			return new ArrayList<>();
		}
	}
}
