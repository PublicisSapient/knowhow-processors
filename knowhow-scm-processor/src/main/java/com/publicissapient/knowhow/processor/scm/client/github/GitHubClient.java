package com.publicissapient.knowhow.processor.scm.client.github;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;

/**
 * GitHub API client for interacting with GitHub repositories.
 * Handles authentication, rate limiting, and data fetching operations.
 */
@Component
public class GitHubClient {

    private static final Logger logger = LoggerFactory.getLogger(GitHubClient.class);
    private static final String PLATFORM_NAME = "GitHub";

    @Value("${git.platforms.github.api-url:https://api.github.com}")
    private String githubApiUrl;

    @Value("${git.scanner.pagination.max-commits-per-scan:10000}")
    private int maxCommitsPerScan;

    @Value("${git.scanner.pagination.max-merge-requests-per-scan:5000}")
    private int maxMergeRequestsPerScan;

    @Autowired
    private RateLimitService rateLimitService;

    /**
     * Creates and returns an authenticated GitHub client instance.
     *
     * @param token GitHub personal access token
     * @return GitHub client instance
     * @throws IOException if authentication fails
     */
    public GitHub getGitHubClient(String token) throws IOException {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("GitHub token cannot be null or empty");
        }

        try {
            GitHub github = new GitHubBuilder()
                    .withEndpoint(githubApiUrl)
                    .withOAuthToken(token)
                    .build();

            // Test the connection
            github.checkApiUrlValidity();
            logger.debug("Successfully authenticated with GitHub API");
            return github;

        } catch (IOException e) {
            logger.error("Failed to authenticate with GitHub API: {}", e.getMessage());
            throw new IOException("GitHub authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets a GitHub repository instance.
     *
     * @param owner      Repository owner
     * @param repository Repository name
     * @param token      GitHub access token
     * @return GHRepository instance
     * @throws IOException if repository access fails
     */
    public GHRepository getRepository(String owner, String repository, String token) throws IOException {
        GitHub github = getGitHubClient(token);
        String repositoryName = owner + "/" + repository;

        try {
            GHRepository repo = github.getRepository(repositoryName);
            logger.debug("Successfully accessed GitHub repository: {}", repositoryName);
            return repo;
        } catch (IOException e) {
            logger.error("Failed to access GitHub repository {}: {}", repositoryName, e.getMessage());
            throw new IOException("Failed to access repository: " + repositoryName, e);
        }
    }

    /**
     * Fetches commits from a GitHub repository with pagination and date filtering.
     *
     * @param owner      Repository owner
     * @param repository Repository name
     * @param branchName Branch name (optional)
     * @param token      GitHub access token
     * @param since      Start date for filtering
     * @param until      End date for filtering
     * @return List of GitHub commits
     * @throws IOException if API call fails
     */
    public List<GHCommit> fetchCommits(String owner, String repository, String branchName,
                                      String token, LocalDateTime since, LocalDateTime until) throws IOException {
        String repositoryName = owner + "/" + repository;
        logger.debug("Fetching commits from GitHub repository: {} since: {} until: {}", repositoryName, since, until);

        try {
            // Check rate limit before making API calls
            rateLimitService.checkRateLimit(PLATFORM_NAME, token, repositoryName, null);

            GHRepository repo = getRepository(owner, repository, token);

            List<GHCommit> allCommits = new ArrayList<>();
            int totalFetched = 0;

            PagedIterable<GHCommit> commits;
            if (branchName != null && !branchName.isEmpty()) {
                commits = repo.queryCommits().from(branchName).list();
            } else {
                commits = repo.queryCommits().list();
            }

            for (GHCommit commit : commits) {
                if (totalFetched >= maxCommitsPerScan) {
                    logger.debug("Reached maximum commits limit: {}", maxCommitsPerScan);
                    break;
                }

                // Check rate limit periodically
                if (totalFetched % 100 == 0) {
                    rateLimitService.checkRateLimit(PLATFORM_NAME, token, repositoryName, null);
                }

                // Filter by date if specified
                if (commit.getCommitDate() != null) {
                    LocalDateTime commitDate = commit.getCommitDate().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();

                    boolean afterSince = since == null || !commitDate.isBefore(since);
                    boolean beforeUntil = until == null || !commitDate.isAfter(until);

                    if (afterSince && beforeUntil) {
                        allCommits.add(commit);
                        totalFetched++;
                    } else if (since != null && commitDate.isBefore(since)) {
                        // Since commits are usually ordered by date DESC, we can break early
                        logger.debug("Reached commits older than since date, stopping fetch");
                        break;
                    }
                }
            }

            logger.info("Successfully fetched {} commits from GitHub repository: {}", allCommits.size(), repositoryName);
            return allCommits;

        } catch (IOException e) {
            logger.error("Failed to fetch commits from GitHub repository {}: {}", repositoryName, e.getMessage());
            throw e;
        }
    }

    /**
     * Fetches the latest commits from a GitHub repository up to a specified limit.
     *
     * @param owner      Repository owner
     * @param repository Repository name
     * @param branchName Branch name (optional)
     * @param token      GitHub access token
     * @param limit      Maximum number of commits to fetch
     * @return List of GitHub commits
     * @throws IOException if API call fails
     */
    public List<GHCommit> fetchLatestCommits(String owner, String repository, String branchName,
                                            String token, int limit) throws IOException {
        String repositoryName = owner + "/" + repository;
        logger.debug("Fetching latest {} commits from GitHub repository: {}", limit, repositoryName);

        try {
            // Check rate limit before making API calls
            rateLimitService.checkRateLimit(PLATFORM_NAME, token, repositoryName, null);

            GHRepository repo = getRepository(owner, repository, token);

            List<GHCommit> commits = new ArrayList<>();
            int fetched = 0;

            PagedIterable<GHCommit> commitIterable;
            if (branchName != null && !branchName.isEmpty()) {
                commitIterable = repo.queryCommits().from(branchName).list();
            } else {
                commitIterable = repo.queryCommits().list();
            }

            for (GHCommit commit : commitIterable) {
                if (fetched >= limit) {
                    break;
                }
                commits.add(commit);
                fetched++;
            }

            logger.info("Successfully fetched {} latest commits from GitHub repository: {}", commits.size(), repositoryName);
            return commits;

        } catch (IOException e) {
            logger.error("Failed to fetch latest commits from GitHub repository {}: {}", repositoryName, e.getMessage());
            throw e;
        }
    }

    /**
     * Fetches pull requests from a GitHub repository with date filtering based on updated date
     *
     * @param owner      Repository owner
     * @param repository Repository name
     * @param token      GitHub access token
     * @param since      Start date for filtering (based on updated date)
     * @param until      End date for filtering (based on updated date)
     * @return List of GitHub pull requests
     * @throws IOException if API call fails
     */
    public List<GHPullRequest> fetchPullRequests(String owner, String repository, String token,
                                                LocalDateTime since, LocalDateTime until) throws IOException {
        logger.info("Fetching pull requests for {}/{} from {} to {} (based on updated date)", owner, repository, since, until);
        
        GitHub github = getGitHubClient(token);
        GHRepository repo = getRepository(owner, repository, token);
        
        List<GHPullRequest> allPullRequests = new ArrayList<>();
        int totalFetched = 0;
        
        try {
            // GitHub API uses updated date for sorting by default when state is 'all'
            PagedIterable<GHPullRequest> pullRequests = repo.queryPullRequests()
                .state(GHIssueState.ALL)
                .sort(GHPullRequestQueryBuilder.Sort.UPDATED)
                .direction(GHDirection.DESC)
                .list();
            
            for (GHPullRequest pr : pullRequests) {
                if (totalFetched >= maxMergeRequestsPerScan) {
                    logger.debug("Reached maximum pull requests limit: {}", maxMergeRequestsPerScan);
                    break;
                }
                
                // Check rate limit before processing each batch
                if (totalFetched % 100 == 0) {
                    rateLimitService.checkRateLimit("GitHub", token, owner + "/" + repository, null);
                }
                
                // Filter by updated date
                if (pr.getUpdatedAt() != null) {
                    LocalDateTime updatedAt = pr.getUpdatedAt().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();
                    
                    // Check if updated date is within the specified range
                    boolean afterSince = since == null || !updatedAt.isBefore(since);
                    boolean beforeUntil = until == null || !updatedAt.isAfter(until);
                    
                    if (afterSince && beforeUntil) {
                        allPullRequests.add(pr);
                        totalFetched++;
                    } else if (since != null && updatedAt.isBefore(since)) {
                        // Since we're sorting by updated date DESC, if we hit a PR older than 'since', we can stop
                        logger.debug("Reached pull requests older than since date, stopping fetch");
                        break;
                    }
                }
            }
            
            logger.info("Successfully fetched {} pull requests for {}/{} (based on updated date)", 
                allPullRequests.size(), owner, repository);
            return allPullRequests;
            
        } catch (IOException e) {
            logger.error("Failed to fetch pull requests for {}/{}: {}", owner, repository, e.getMessage());
            throw e;
        }
    }

    /**
     * Fetches pull requests by state with date filtering based on updated date
     *
     * @param owner      Repository owner
     * @param repository Repository name
     * @param state      Pull request state (open, closed, all)
     * @param token      GitHub access token
     * @param since      Start date for filtering (based on updated date)
     * @param until      End date for filtering (based on updated date)
     * @return List of GitHub pull requests
     * @throws IOException if API call fails
     */
    public List<GHPullRequest> fetchPullRequestsByState(String owner, String repository, String state,
                                                       String token, LocalDateTime since, LocalDateTime until) throws IOException {
        logger.info("Fetching {} pull requests for {}/{} from {} to {} (based on updated date)", 
            state, owner, repository, since, until);
        
        GitHub github = getGitHubClient(token);
        GHRepository repo = getRepository(owner, repository, token);
        
        List<GHPullRequest> allPullRequests = new ArrayList<>();
        int totalFetched = 0;
        
        try {
            GHIssueState ghState = parseGitHubState(state);
            
            PagedIterable<GHPullRequest> pullRequests = repo.queryPullRequests()
                .state(ghState)
                .sort(GHPullRequestQueryBuilder.Sort.UPDATED)
                .direction(GHDirection.DESC)
                .list();
            
            for (GHPullRequest pr : pullRequests) {
                if (totalFetched >= maxMergeRequestsPerScan) {
                    logger.debug("Reached maximum pull requests limit: {}", maxMergeRequestsPerScan);
                    break;
                }
                
                // Check rate limit before processing each batch
                if (totalFetched % 100 == 0) {
                    rateLimitService.checkRateLimit("GitHub", token, owner + "/" + repository, null);
                }
                
                // Filter by updated date
                if (pr.getUpdatedAt() != null) {
                    LocalDateTime updatedAt = pr.getUpdatedAt().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();
                    
                    // Check if updated date is within the specified range
                    boolean afterSince = since == null || !updatedAt.isBefore(since);
                    boolean beforeUntil = until == null || !updatedAt.isAfter(until);
                    
                    if (afterSince && beforeUntil) {
                        allPullRequests.add(pr);
                        totalFetched++;
                    } else if (since != null && updatedAt.isBefore(since)) {
                        // Since we're sorting by updated date DESC, if we hit a PR older than 'since', we can stop
                        logger.debug("Reached pull requests older than since date, stopping fetch");
                        break;
                    }
                }
            }
            
            logger.info("Successfully fetched {} {} pull requests for {}/{} (based on updated date)", 
                allPullRequests.size(), state, owner, repository);
            return allPullRequests;
            
        } catch (IOException e) {
            logger.error("Failed to fetch {} pull requests for {}/{}: {}", state, owner, repository, e.getMessage());
            throw e;
        }
    }

    /**
     * Fetches the latest pull requests from a GitHub repository up to a specified limit.
     *
     * @param owner      Repository owner
     * @param repository Repository name
     * @param token      GitHub access token
     * @param limit      Maximum number of pull requests to fetch
     * @return List of GitHub pull requests
     * @throws IOException if API call fails
     */
    public List<GHPullRequest> fetchLatestPullRequests(String owner, String repository, String token, int limit) throws IOException {
        String repositoryName = owner + "/" + repository;
        logger.debug("Fetching latest {} pull requests from GitHub repository: {}", limit, repositoryName);

        try {
            // Check rate limit before making API calls
            rateLimitService.checkRateLimit(PLATFORM_NAME, token, repositoryName, null);

            GHRepository repo = getRepository(owner, repository, token);

            List<GHPullRequest> pullRequests = new ArrayList<>();
            int fetched = 0;

            PagedIterable<GHPullRequest> prIterable = repo.queryPullRequests()
                .state(GHIssueState.ALL)
                .sort(GHPullRequestQueryBuilder.Sort.UPDATED)
                .direction(GHDirection.DESC)
                .list();

            for (GHPullRequest pr : prIterable) {
                if (fetched >= limit) {
                    break;
                }
                pullRequests.add(pr);
                fetched++;
            }

            logger.info("Successfully fetched {} latest pull requests from GitHub repository: {}", pullRequests.size(), repositoryName);
            return pullRequests;

        } catch (IOException e) {
            logger.error("Failed to fetch latest pull requests from GitHub repository {}: {}", repositoryName, e.getMessage());
            throw e;
        }
    }

    /**
     * Tests the connection to GitHub API.
     *
     * @param token GitHub access token
     * @return true if connection is successful
     * @throws IOException if connection fails
     */
    public boolean testConnection(String token) throws IOException {
        try {
            GitHub github = getGitHubClient(token);
            github.checkApiUrlValidity();
            logger.info("GitHub API connection test successful");
            return true;
        } catch (IOException e) {
            logger.error("GitHub API connection test failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Gets the GitHub API URL.
     *
     * @return GitHub API URL
     */
    public String getApiUrl() {
        return githubApiUrl;
    }

    /**
     * Helper method to parse GitHub state string to GitHub API enum
     */
    private GHIssueState parseGitHubState(String state) {
        if (state == null) return GHIssueState.ALL;
        
        switch (state.toLowerCase()) {
            case "open":
            case "opened":
                return GHIssueState.OPEN;
            case "closed":
                return GHIssueState.CLOSED;
            case "all":
            default:
                return GHIssueState.ALL;
        }
    }
}