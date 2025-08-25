package com.publicissapient.knowhow.processor.scm.client.gitlab;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestFilter;
import org.gitlab4j.api.models.Note;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;

import lombok.extern.slf4j.Slf4j;

/**
 * GitLab API client for interacting with GitLab repositories.
 * Handles authentication, rate limiting, and data fetching operations.
 */
@Component
public class GitLabClient {

    // Manual logger field since Lombok @Slf4j is not working properly
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GitLabClient.class);

    @Value("${git.platforms.gitlab.api-url:https://gitlab.com}")
    private String defaultGitlabApiUrl;

    @Autowired
    private GitUrlParser gitUrlParser;

    @Value("${git.scanner.pagination.max-commits-per-scan:1000}")
    private int maxCommitsPerScan;

    @Value("${git.scanner.pagination.max-merge-requests-per-scan:500}")
    private int maxMergeRequestsPerScan;

    @Autowired
    private RateLimitService rateLimitService;

    /**
     * Creates and returns an authenticated GitLab API client
     */
    /**
     * Creates and returns an authenticated GitLab API client for the default GitLab instance
     */
    public GitLabApi getGitLabClient(String token) throws GitLabApiException {
        return getGitLabClient(token, defaultGitlabApiUrl);
    }

    /**
     * Creates and returns an authenticated GitLab API client for a specific GitLab instance
     */
    public GitLabApi getGitLabClient(String token, String apiBaseUrl) throws GitLabApiException {
        if (token == null || token.trim().isEmpty()) {
            throw new GitLabApiException("GitLab token cannot be null or empty");
        }

        if (apiBaseUrl == null || apiBaseUrl.trim().isEmpty()) {
            apiBaseUrl = defaultGitlabApiUrl;
        }

        try {
            GitLabApi gitLabApi = new GitLabApi(apiBaseUrl, token);

            // Test the connection
            gitLabApi.getUserApi().getCurrentUser();

            log.info("Successfully authenticated with GitLab API at {}", apiBaseUrl);
            return gitLabApi;

        } catch (GitLabApiException e) {
            log.error("Failed to authenticate with GitLab API at {}: {}", apiBaseUrl, e.getMessage());
            throw e;
        }
    }

    /**
     * Creates and returns an authenticated GitLab API client based on repository URL
     */
    public GitLabApi getGitLabClientFromRepoUrl(String token, String repositoryUrl) throws GitLabApiException {
        try {
            String apiBaseUrl = gitUrlParser.getGitLabApiBaseUrl(repositoryUrl);
            return getGitLabClient(token, apiBaseUrl);
        } catch (Exception e) {
            log.warn("Failed to extract API URL from repository URL {}, falling back to default: {}",
                    repositoryUrl, e.getMessage());
            return getGitLabClient(token);
        }
    }

    /**
     * Fetches commits from a GitLab repository with date filtering
     */
    public List<Commit> fetchCommits(String organization, String repository, String branchName,
                                    String token, LocalDateTime since, LocalDateTime until, String repositoryUrl) throws GitLabApiException {

        GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);

        try {
            String projectPath = organization+"/"+repository;
            log.info("Starting to fetch commits from GitLab repository {} on branch {} with date range: {} to {}",
                    projectPath, branchName, since, until);
            // Check rate limit before making API calls
            rateLimitService.checkRateLimit("GitLab", token, projectPath, gitLabApi.getGitLabServerUrl());

            Project project = gitLabApi.getProjectApi().getProject(projectPath);
            List<Commit> allCommits = new ArrayList<>();

            // Convert LocalDateTime to Date for GitLab API
            Date sinceDate = since != null ? Date.from(since.atZone(ZoneId.systemDefault()).toInstant()) : null;
            Date untilDate = until != null ? Date.from(until.atZone(ZoneId.systemDefault()).toInstant()) : null;

            int page = 1;
            int perPage = 100; // GitLab API default max per page
            boolean hasMore = true;
            int totalFetched = 0;

            log.debug("Starting pagination for repository {} on branch {}, max commits per scan: {}",
                     projectPath, branchName, maxCommitsPerScan);
            Pager<Commit> fetchedCommits = gitLabApi.getCommitsApi().getCommits(
                    project.getId(),
                    branchName,
                    sinceDate,
                    untilDate,
                    perPage
            );
            while (fetchedCommits.hasNext() && totalFetched < maxCommitsPerScan) {
                // Check rate limit before each page request
                rateLimitService.checkRateLimit("GitLab", token, projectPath, gitLabApi.getGitLabServerUrl());

                log.debug("Fetching page {} with {} commits per page from repository {}",
                         page, perPage, projectPath);


                List<Commit> commits = fetchedCommits.next();
                if (commits == null || commits.isEmpty()) {
                    log.debug("No more commits found for repository {}", projectPath);
                    break;
                } else {
                    int commitsToAdd = Math.min(commits.size(), maxCommitsPerScan - totalFetched);
                    allCommits.addAll(commits.subList(0, commitsToAdd));
                    totalFetched += commitsToAdd;

                    log.debug("Fetched {} commits from page {} (total so far: {})",
                             commitsToAdd, page, totalFetched);

                    // Check if we've reached the last page or hit our limit
                    if (commits.size() < perPage || totalFetched >= maxCommitsPerScan) {
                        if (totalFetched >= maxCommitsPerScan) {
                            log.info("Reached maximum commits per scan limit ({}) for repository {}",
                                    maxCommitsPerScan, projectPath);
                        }
                        break;
                    } else {
                        page++;
                    }
                }
            }

            log.info("Successfully fetched {} commits from GitLab repository {} on branch {} (pages processed: {})",
                    allCommits.size(), projectPath, branchName, page - 1);
            return allCommits;

        } catch (GitLabApiException e) {
            log.error("Failed to fetch commits from GitLab repository {} on branch {}: {} (HTTP Status: {})",
                     repository, branchName, e.getMessage(), e.getHttpStatus());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while fetching commits from GitLab repository {} on branch {}: {}",
                     repository, branchName, e.getMessage(), e);
            throw new GitLabApiException("Unexpected error during commit fetch", 503);
        }
    }

    /**
     * Fetches merge requests from a GitLab repository with date and branch filtering
     */
    public List<MergeRequest> fetchMergeRequests(String organization, String repository, String branchName,
                                               String token, LocalDateTime since, LocalDateTime until, String repositoryUrl) throws GitLabApiException {
        GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);
        String projectPath = organization+"/"+repository;

        log.info("Starting to fetch merge requests from GitLab repository {} with branch filter: {} and date range: {} to {}",
                projectPath, branchName, since, until);

        try {
            // Check rate limit before making API calls
            rateLimitService.checkRateLimit("GitLab", token, projectPath, gitLabApi.getGitLabServerUrl());

            Project project = gitLabApi.getProjectApi().getProject(projectPath);
            List<MergeRequest> allMergeRequests = new ArrayList<>();

            // Convert LocalDateTime to Date for GitLab API
            Date sinceDate = since != null ? Date.from(since.atZone(ZoneId.systemDefault()).toInstant()) : null;

            int page = 1;
            int perPage = Math.min(100, maxMergeRequestsPerScan); // GitLab API max per page is 100
            int totalFetched = 0;
            MergeRequestFilter mergeRequestFilter = new MergeRequestFilter()
                    .withProjectId(project.getId())
                    .withState(Constants.MergeRequestState.ALL)
                    .withUpdatedAfter(sinceDate)
                    .withTargetBranch(branchName);

            while (totalFetched < maxMergeRequestsPerScan) {
                log.debug("Fetching merge requests page {} for GitLab repository {}", page, projectPath);

                try {
                    // Use a simpler getMergeRequests method call
                    List<MergeRequest> pageMergeRequests = gitLabApi.getMergeRequestApi().getMergeRequests(
                            mergeRequestFilter,
                            page,
                            perPage
                    );
                    if (pageMergeRequests == null || pageMergeRequests.isEmpty()) {
                        log.debug("No more merge requests found for GitLab repository {} on page {}", projectPath, page);
                        break;
                    }

                    // Additional filtering by branch if specified
                    List<MergeRequest> filteredMergeRequests = pageMergeRequests.stream()
                            .filter(mr -> {
                                if (branchName != null && !branchName.isEmpty()) {
                                    return branchName.equals(mr.getSourceBranch()) || branchName.equals(mr.getTargetBranch());
                                }
                                return true;
                            })
                            .collect(Collectors.toList());

                    allMergeRequests.addAll(filteredMergeRequests);
                    totalFetched += filteredMergeRequests.size();
                    gitLabApi.getNotesApi().getMergeRequestNotes(project.getId(), pageMergeRequests.get(0).getIid());
                    log.debug("Fetched {} merge requests from page {} for GitLab repository {}",
                             filteredMergeRequests.size(), page, projectPath);

                    // Check if we've reached the limit or if there are no more pages
                    if (pageMergeRequests.size() < perPage || totalFetched >= maxMergeRequestsPerScan) {
                        break;
                    }

                    page++;

                    // Add a small delay to be respectful to the API
//                    try {
//                        Thread.sleep(100);
//                    } catch (InterruptedException e) {
//                        Thread.currentThread().interrupt();
//                        break;
//                    }

                } catch (GitLabApiException e) {
                    if (e.getHttpStatus() == 429) {
                        log.warn("Rate limit hit while fetching merge requests from {}, page {}: {}",
                                projectPath, page, e.getMessage());
                        rateLimitService.checkRateLimit("GitLab", token, projectPath, gitLabApi.getGitLabServerUrl());
                        continue; // Retry the same page
                    } else {
                        log.error("API error while fetching merge requests from {} on page {}: {}",
                                 projectPath, page, e.getMessage());
                        throw e;
                    }
                }
            }

            log.info("Successfully fetched {} merge requests from GitLab repository {}", allMergeRequests.size(), projectPath);
            return allMergeRequests;

        } catch (GitLabApiException e) {
            log.error("Failed to fetch merge requests from GitLab repository {}: {}", projectPath, e.getMessage());
            throw e;
        }
    }

    public long getPrPickUpTimeStamp(String organization, String repository,
                                     String token, String repositoryUrl, Long mrId) throws GitLabApiException {

        try {
            GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);
            String projectPath = organization + "/" + repository;

            log.debug("Getting first reviewer action timestamp for MR !{} from GitLab repository {}",
                    mrId, projectPath);

            // Check rate limit before making API calls
            rateLimitService.checkRateLimit("GitLab", token, projectPath, gitLabApi.getGitLabServerUrl());

            Project project = gitLabApi.getProjectApi().getProject(projectPath);

            // Fetch merge request details to get creation time
            MergeRequest mergeRequest = gitLabApi.getMergeRequestApi().getMergeRequest(project.getId(), mrId);
            Date mrCreatedAt = mergeRequest.getCreatedAt();

            // Fetch all notes for the merge request
            List<Note> notes = gitLabApi.getNotesApi().getMergeRequestNotes(project.getId(), mrId);

            if (notes == null || notes.isEmpty()) {
                log.debug("No notes found for MR !{}", mrId);
                return 0L;
            }

            // Sort notes by creation date to find the first reviewer action
            notes.sort((a, b) -> {
                if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                if (a.getCreatedAt() == null) return 1;
                if (b.getCreatedAt() == null) return -1;
                return a.getCreatedAt().compareTo(b.getCreatedAt());
            });

            // Find the first reviewer action (excluding the MR author's own actions)
            String mrAuthorUsername = mergeRequest.getAuthor() != null ? mergeRequest.getAuthor().getUsername() : null;

            for (Note note : notes) {
                // Skip notes created before or at the same time as MR creation
                if (note.getCreatedAt() == null ||
                        (mrCreatedAt != null && !note.getCreatedAt().after(mrCreatedAt))) {
                    continue;
                }

                // Skip notes from the MR author (self-actions don't count as reviewer actions)
                if (mrAuthorUsername != null && note.getAuthor() != null &&
                        mrAuthorUsername.equals(note.getAuthor().getUsername())) {
                    continue;
                }

                // Check if this note represents a reviewer action
                if (isReviewerAction(note)) {
                    long firstReviewerActionTime = note.getCreatedAt().getTime();
                    log.debug("Found first reviewer action timestamp {} for MR !{} by user {}",
                            firstReviewerActionTime, mrId,
                            note.getAuthor() != null ? note.getAuthor().getUsername() : "unknown");
                    return firstReviewerActionTime;
                }
            }

            log.debug("No reviewer actions found for MR !{}", mrId);
            return 0L;

        } catch (NumberFormatException e) {
            log.error("Invalid merge request ID format: {}", mrId);
            throw new GitLabApiException("Invalid merge request ID: " + mrId, 400);
        } catch (GitLabApiException e) {
            log.error("Failed to get first reviewer action timestamp for MR {} from repository {}: {}",
                    mrId, organization + "/" + repository, e.getMessage());
            throw e;
        }
    }

    private boolean isReviewerAction(Note note) {
        if (note.getBody() == null) {
            return false;
        }

        String body = note.getBody().toLowerCase().trim();

        // Check for system notes that indicate reviewer actions
        if (note.getSystem() != null && note.getSystem()) {
            return body.contains("approved this merge request") ||
                    body.contains("unapproved this merge request") ||
                    body.contains("requested changes") ||
                    body.contains("started a review") ||
                    body.contains("requested review from") ||
                    body.contains("assigned to") ||
                    body.contains("unassigned") ||
                    body.contains("marked as draft") ||
                    body.contains("marked as ready") ||
                    body.contains("closed") ||
                    body.contains("reopened");
        }

        // Check for regular comments that indicate reviewer engagement
        // Any non-empty comment from someone other than the author is considered a reviewer action
        if (body.length() > 2) { // Ignore very short comments like "ok", "👍"
            return true;
        }

        return false;
    }


    /**
     * Fetches merge requests by state from a GitLab repository with date and branch filtering
     */
    public List<MergeRequest> fetchMergeRequestsByState(String owner, String repository, String branchName, String state,
                                                       String token, LocalDateTime since, LocalDateTime until, String repositoryUrl) throws GitLabApiException {
        GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);
        String projectPath = owner + "/" + repository;

        try {
            Project project = gitLabApi.getProjectApi().getProject(projectPath);
            List<MergeRequest> allMergeRequests = new ArrayList<>();

            // Convert LocalDateTime to Date for GitLab API
            Date sinceDate = since != null ? Date.from(since.atZone(ZoneId.systemDefault()).toInstant()) : null;
            Date untilDate = until != null ? Date.from(until.atZone(ZoneId.systemDefault()).toInstant()) : null;

            int page = 1;
            int perPage = 100;
            boolean hasMore = true;

            while (hasMore && allMergeRequests.size() < maxMergeRequestsPerScan) {
                // Use the basic getMergeRequests method and filter by state manually
                List<MergeRequest> mergeRequests = gitLabApi.getMergeRequestApi().getMergeRequests(
                    project.getId(),
                    page,
                    perPage
                );

                if (mergeRequests.isEmpty()) {
                    hasMore = false;
                } else {
                    // Filter by state, branch and date if specified
                    mergeRequests = mergeRequests.stream()
                        .filter(mr -> {
                            // Filter by state
                            if (state != null && !state.equalsIgnoreCase("all")) {
                                if (!mr.getState().toString().equalsIgnoreCase(state)) {
                                    return false;
                                }
                            }

                            // Filter by target branch if specified
                            if (branchName != null && !branchName.trim().isEmpty()) {
                                String targetBranch = mr.getTargetBranch();
                                if (targetBranch == null || !targetBranch.equals(branchName)) {
                                    return false;
                                }
                            }

                            // Filter by date
                            Date updatedAt = mr.getUpdatedAt();
                            if (updatedAt == null) return false;

                            if (sinceDate != null && updatedAt.before(sinceDate)) return false;
                            if (untilDate != null && updatedAt.after(untilDate)) return false;

                            return true;
                        })
                        .collect(Collectors.toList());

                    allMergeRequests.addAll(mergeRequests);
                    page++;

                    // Check if we've reached the last page
                    if (mergeRequests.size() < perPage) {
                        hasMore = false;
                    }
                }
            }

            String branchFilter = branchName != null ? " for branch '" + branchName + "'" : "";
            log.info("Fetched {} {} merge requests{} from GitLab repository {}", allMergeRequests.size(), state, branchFilter, projectPath);
            return allMergeRequests;

        } catch (GitLabApiException e) {
            log.error("Failed to fetch {} merge requests from GitLab repository {}: {}", state, projectPath, e.getMessage());
            throw e;
        }
    }

    /**
     * Fetches merge requests by state from a GitLab repository with date and branch filtering (backward compatibility)
     */
    public List<MergeRequest> fetchMergeRequestsByState(String owner, String repository, String branchName, String state,
                                                       String token, LocalDateTime since, LocalDateTime until) throws GitLabApiException {
        String defaultRepoUrl = defaultGitlabApiUrl + "/" + owner + "/" + repository;
        return fetchMergeRequestsByState(owner, repository, branchName, state, token, since, until, defaultRepoUrl);
    }

    /**
     * Fetches the latest merge requests from a GitLab repository with branch filtering
     */
    public List<MergeRequest> fetchLatestMergeRequests(String owner, String repository, String branchName,
                                                      String token, int limit, String repositoryUrl) throws GitLabApiException {
        GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);
        String projectPath = owner + "/" + repository;

        log.info("Starting to fetch latest {} merge requests from GitLab repository {} with branch filter: {}",
                limit, projectPath, branchName);

        try {
            // Check rate limit before making API calls
            rateLimitService.checkRateLimit("GitLab", token, projectPath, gitLabApi.getGitLabServerUrl());

            Project project = gitLabApi.getProjectApi().getProject(projectPath);
            List<MergeRequest> allMergeRequests = new ArrayList<>();

            int page = 1;
            int remainingLimit = Math.min(limit, maxMergeRequestsPerScan);

            while (remainingLimit > 0) {
                int currentPageSize = Math.min(100, remainingLimit); // GitLab API max per page is 100

                try {
                    log.debug("Fetching latest merge requests page {} for GitLab repository {} (page size: {})",
                             page, projectPath, currentPageSize);

                    // Use the simpler getMergeRequests method
                    List<MergeRequest> mergeRequests = gitLabApi.getMergeRequestApi().getMergeRequests(
                            project.getId(),
                            page,
                            currentPageSize
                    );
                    if (mergeRequests == null || mergeRequests.isEmpty()) {
                        log.debug("No more merge requests found on page {} for repository {}", page, projectPath);
                        break;
                    }

                    // Additional filtering by branch if specified
                    List<MergeRequest> filteredMergeRequests = mergeRequests.stream()
                            .filter(mr -> {
                                if (branchName != null && !branchName.isEmpty()) {
                                    return branchName.equals(mr.getSourceBranch()) || branchName.equals(mr.getTargetBranch());
                                }
                                return true;
                            })
                            .collect(Collectors.toList());

                    int mergeRequestsToAdd = Math.min(filteredMergeRequests.size(), remainingLimit);
                    allMergeRequests.addAll(filteredMergeRequests.subList(0, mergeRequestsToAdd));
                    remainingLimit -= mergeRequestsToAdd;

                    log.debug("Fetched {} merge requests from page {} (total so far: {})",
                             mergeRequestsToAdd, page, allMergeRequests.size());

                    // If we got fewer merge requests than requested, we've reached the end
                    if (mergeRequests.size() < currentPageSize) {
                        break;
                    }

                    page++;

                } catch (GitLabApiException e) {
                    if (e.getHttpStatus() == 429) {
                        log.warn("Rate limit hit while fetching latest merge requests from {}, page {}: {}",
                                projectPath, page, e.getMessage());
                        rateLimitService.checkRateLimit("GitLab", token, projectPath, gitLabApi.getGitLabServerUrl());
                        continue; // Retry the same page
                    } else {
                        log.error("API error while fetching latest merge requests from {} on page {}: {}",
                                 projectPath, page, e.getMessage());
                        throw e;
                    }
                }
            }

            log.info("Successfully fetched {} latest merge requests from GitLab repository {} with branch filter: {}",
                    allMergeRequests.size(), projectPath, branchName);
            return allMergeRequests;

        } catch (GitLabApiException e) {
            log.error("Failed to fetch latest merge requests from GitLab repository {} with branch filter {}: {} (HTTP Status: {})",
                     projectPath, branchName, e.getMessage(), e.getHttpStatus());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while fetching latest merge requests from GitLab repository {} with branch filter {}: {}",
                     projectPath, branchName, e.getMessage(), e);
            throw new GitLabApiException("Unexpected error during latest merge requests fetch", 503);
        }
    }

    /**
     * Fetches latest merge requests from a GitLab repository up to a specified limit (backward compatibility)
     */
    public List<MergeRequest> fetchLatestMergeRequests(String owner, String repository, String branchName,
                                                      String token, int limit) throws GitLabApiException {
        String defaultRepoUrl = defaultGitlabApiUrl + "/" + owner + "/" + repository;
        return fetchLatestMergeRequests(owner, repository, branchName, token, limit, defaultRepoUrl);
    }

    /**
     * Tests the connection to GitLab
     */
    public boolean testConnection(String token) throws GitLabApiException {
        try {
            GitLabApi gitLabApi = getGitLabClient(token);
            User currentUser = gitLabApi.getUserApi().getCurrentUser();
            log.info("GitLab connection test successful for user: {}", currentUser.getUsername());
            return true;
        } catch (GitLabApiException e) {
            log.error("GitLab connection test failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Gets the GitLab API URL (returns default)
     */
    public String getApiUrl() {
        return defaultGitlabApiUrl;
    }

    /**
     * Fetches commit diffs from a GitLab repository
     */
    public List<Diff> fetchCommitDiffs(String owner, String repository, String commitSha, String token, String repositoryUrl) throws GitLabApiException {
        GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);
        String projectPath = owner + "/" + repository;

        try {
            // Check rate limit before making API calls
            rateLimitService.checkRateLimit("GitLab", token, projectPath, gitLabApi.getGitLabServerUrl());

            Project project = gitLabApi.getProjectApi().getProject(projectPath);
            List<Diff> diffs = gitLabApi.getCommitsApi().getDiff(project.getId(), commitSha);

            log.debug("Fetched {} diffs for commit {} from GitLab repository {}",
                     diffs != null ? diffs.size() : 0, commitSha, projectPath);
            return diffs != null ? diffs : new ArrayList<>();

        } catch (GitLabApiException e) {
            log.warn("Failed to fetch diffs for commit {} from GitLab repository {}: {}", commitSha, projectPath, e.getMessage());
            return new ArrayList<>();
        }
    }


    /**
     * Fetches merge request changes from a GitLab repository
     */
    public List<Diff> fetchMergeRequestChanges(String owner, String repository, Long mergeRequestIid, String token, String repositoryUrl) throws GitLabApiException {
        GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);
        String projectPath = owner + "/" + repository;

        try {
            // Check rate limit before making API calls
            rateLimitService.checkRateLimit("GitLab", token, projectPath, gitLabApi.getGitLabServerUrl());

            Project project = gitLabApi.getProjectApi().getProject(projectPath);
            MergeRequest mergeRequestWithChanges = gitLabApi.getMergeRequestApi().getMergeRequestChanges(project.getId(), mergeRequestIid);
            List<Diff> changes = mergeRequestWithChanges.getChanges();

            log.debug("Fetched {} changes for merge request !{} from GitLab repository {}",
                     changes != null ? changes.size() : 0, mergeRequestIid, projectPath);
            return changes != null ? changes : new ArrayList<>();

        } catch (GitLabApiException e) {
            log.warn("Failed to fetch changes for merge request !{} from GitLab repository {}: {}", mergeRequestIid, projectPath, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Fetches merge request changes from a GitLab repository (backward compatibility)
     */
    public List<Diff> fetchMergeRequestChanges(String owner, String repository, Long mergeRequestIid, String token) throws GitLabApiException {
        String defaultRepoUrl = defaultGitlabApiUrl + "/" + owner + "/" + repository;
        return fetchMergeRequestChanges(owner, repository, mergeRequestIid, token, defaultRepoUrl);
    }

    /**
     * Fetches commits for a specific merge request
     */
    public List<Commit> fetchMergeRequestCommits(String owner, String repository, Long mergeRequestIid, String token, String repositoryUrl) throws GitLabApiException {
        GitLabApi gitLabApi = getGitLabClientFromRepoUrl(token, repositoryUrl);
        String projectPath = owner + "/" + repository;

        try {
            // Check rate limit before making API calls
            rateLimitService.checkRateLimit("GitLab", token, projectPath, gitLabApi.getGitLabServerUrl());

            Project project = gitLabApi.getProjectApi().getProject(projectPath);
            List<Commit> commits = gitLabApi.getMergeRequestApi().getCommits(project.getId(), mergeRequestIid);

            log.debug("Fetched {} commits for merge request !{} from GitLab repository {}",
                     commits != null ? commits.size() : 0, mergeRequestIid, projectPath);
            return commits != null ? commits : new ArrayList<>();

        } catch (GitLabApiException e) {
            log.warn("Failed to fetch commits for merge request !{} from GitLab repository {}: {}", mergeRequestIid, projectPath, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Fetches commits for a specific merge request (backward compatibility)
     */
    public List<Commit> fetchMergeRequestCommits(String owner, String repository, Long mergeRequestIid, String token) throws GitLabApiException {
        String defaultRepoUrl = defaultGitlabApiUrl + "/" + owner + "/" + repository;
        return fetchMergeRequestCommits(owner, repository, mergeRequestIid, token, defaultRepoUrl);
    }
}