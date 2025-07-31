package com.publicissapient.knowhow.processor.scm.service.platform;

import com.publicissapient.kpidashboard.common.model.scm.CommitDetails;
import com.publicissapient.kpidashboard.common.model.scm.MergeRequests;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Interface for Git platform-specific services.
 * 
 * This interface defines the contract for interacting with different Git platforms
 * (GitHub, GitLab, Azure DevOps, Bitbucket) through their REST APIs.
 * 
 * Each platform implementation should handle:
 * - Authentication and authorization using provided tokens
 * - Rate limiting and retry logic
 * - Platform-specific API differences
 * - Data mapping to common domain models
 * 
 * Implements the Strategy pattern to allow switching between different platforms.
 */
public interface GitPlatformService {

    /**
     * Fetches commits from a repository.
     * 
     * @param toolConfigId the tool configuration ID
     * @param owner the repository owner
     * @param repository the repository name
     * @param branchName the branch name (optional, null for default branch)
     * @param token the access token for authentication
     * @param since the start date for commits (optional)
     * @param until the end date for commits (optional)
     * @return list of commits
     * @throws PlatformApiException if API call fails
     */
    List<CommitDetails> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlParser, String branchName,
                                     String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException;

    /**
     * Fetches merge requests from a repository.
     *
     * @param toolConfigId the tool configuration ID
     * @param owner the repository owner
     * @param repository the repository name
     * @param branchName the target branch name to filter merge requests (optional, null for all branches)
     * @param token the access token for authentication
     * @param since the start date for merge requests (optional)
     * @param until the end date for merge requests (optional)
     * @return list of merge requests
     * @throws PlatformApiException if API call fails
     */
    List<MergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
                                           String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException;

    /**
     * Fetches merge requests with a specific state.
     *
     * @param toolConfigId the tool configuration ID
     * @param owner the repository owner
     * @param repository the repository name
     * @param branchName the target branch name to filter merge requests (optional, null for all branches)
     * @param state the merge request state (open, closed, merged, all)
     * @param token the access token for authentication
     * @param since the start date for merge requests (optional)
     * @param until the end date for merge requests (optional)
     * @return list of merge requests
     * @throws PlatformApiException if API call fails
     */
    List<MergeRequests> fetchMergeRequestsByState(String toolConfigId, String owner, String repository, String branchName, String state,
                                                  String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException;

    /**
     * Fetches a specific number of latest merge requests.
     *
     * @param toolConfigId the tool configuration ID
     * @param owner the repository owner
     * @param repository the repository name
     * @param branchName the target branch name to filter merge requests (optional, null for all branches)
     * @param token the access token for authentication
     * @param limit the maximum number of merge requests to fetch
     * @return list of latest merge requests
     * @throws PlatformApiException if API call fails
     */
    List<MergeRequests> fetchLatestMergeRequests(String toolConfigId, String owner, String repository, String branchName,
                                                 String token, int limit) throws PlatformApiException;

    /**
     * Tests the connection to the platform API.
     * 
     * @param token the access token for authentication
     * @return true if connection is successful, false otherwise
     */
    boolean testConnection(String token);

    /**
     * Gets the platform name.
     * 
     * @return the platform name (e.g., "GitHub", "GitLab")
     */
    String getPlatformName();

    /**
     * Gets the API base URL for the platform.
     * 
     * @return the API base URL
     */
    String getApiBaseUrl();

    /**
     * Data class for rate limit information.
     */
    class RateLimitInfo {
        private final int limit;
        private final int remaining;
        private final LocalDateTime resetTime;
        private final boolean isLimited;

        public RateLimitInfo(int limit, int remaining, LocalDateTime resetTime, boolean isLimited) {
            this.limit = limit;
            this.remaining = remaining;
            this.resetTime = resetTime;
            this.isLimited = isLimited;
        }

        public int getLimit() { return limit; }
        public int getRemaining() { return remaining; }
        public LocalDateTime getResetTime() { return resetTime; }
        public boolean isLimited() { return isLimited; }

        @Override
        public String toString() {
            return String.format("RateLimitInfo{limit=%d, remaining=%d, resetTime=%s, isLimited=%s}",
                    limit, remaining, resetTime, isLimited);
        }
    }
}