
package com.publicissapient.knowhow.processor.scm.client.azuredevops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import org.azd.connection.Connection;
import org.azd.git.GitApi;
import org.azd.git.types.GitChange;
import org.azd.git.types.GitCommit;
import org.azd.git.types.GitCommitChanges;
import org.azd.git.types.GitCommitRefs;
import org.azd.git.types.GitCommits;
import org.azd.git.types.GitCommitsBatch;
import org.azd.git.types.GitPullRequest;
import org.azd.git.types.GitPullRequestQueryParameters;
import org.azd.git.types.GitRepository;
import org.azd.git.types.GitCommitRef;
import org.azd.enums.PullRequestStatus;
import org.azd.git.types.ResourceRefs;
import org.azd.interfaces.GitDetails;
import org.azd.utils.AzDClientApi;
import org.azd.wiki.types.GitVersionDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;;
import java.util.stream.Collectors;

/**
 * Azure DevOps API client for interacting with Azure Repos.
 * Handles authentication, rate limiting, and data fetching operations.
 */
@Component
public class AzureDevOpsClient {

    private static final Logger logger = LoggerFactory.getLogger(AzureDevOpsClient.class);
    private static final String PLATFORM_NAME = "Azure DevOps";

    @Value("${git.platforms.azure-devops.api-url:https://dev.azure.com}")
    private String azureDevOpsApiUrl;

    @Value("${git.scanner.pagination.max-merge-requests-per-scan:5000}")
    private int maxMergeRequestsPerScan;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public AzureDevOpsClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates and returns an authenticated Azure DevOps connection instance.
     *
     * @param token Azure DevOps personal access token
     * @param organization Azure DevOps organization name
     * @return Connection instance
     * @throws Exception if authentication fails
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

            logger.debug("Successfully authenticated with Azure DevOps API for organization: {}", organization);
            return connection;

        } catch (Exception e) {
            logger.error("Failed to authenticate with Azure DevOps API: {}", e.getMessage());
            throw new Exception("Azure DevOps authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets an Azure DevOps repository instance.
     *
     * @param organization Azure DevOps organization
     * @param project Azure DevOps project
     * @param repository Repository name
     * @param token Azure DevOps access token
     * @return GitRepository instance
     * @throws Exception if repository access fails
     */
    public GitRepository getRepository(String organization, String project, String repository, String token) throws Exception {
        Connection connection = getAzureDevOpsConnection(token, organization, project);
        GitApi gitApi = new GitApi(connection);

        try {
            GitRepository repo = gitApi.getRepository(repository);
            logger.debug("Successfully accessed Azure DevOps repository: {}/{}/{}", organization, project, repository);
            return repo;
        } catch (Exception e) {
            logger.error("Failed to access Azure DevOps repository {}/{}/{}: {}", organization, project, repository, e.getMessage());
            throw new Exception("Failed to access repository: " + organization + "/" + project + "/" + repository, e);
        }
    }

    /**
     * Fetches commits from an Azure DevOps repository with pagination and date filtering.
     *
     * @param organization Azure DevOps organization
     * @param project Azure DevOps project
     * @param repository Repository name
     * @param branchName Branch name to fetch commits from
     * @param token Azure DevOps access token
     * @param since Start date for commit filtering
     * @param until End date for commit filtering
     * @return List of GitCommit objects
     * @throws Exception if fetching commits fails
     */
    public List<GitCommitRef> fetchCommits(String organization, String project, String repository,
                                       String branchName, String token, LocalDateTime since, LocalDateTime until) throws Exception {
        Connection connection = getAzureDevOpsConnection(token, organization, project);
        GitApi gitApi = new GitApi(connection);

        List<GitCommitRef> allCommits = new ArrayList<>();

        try {
            logger.info("Fetching commits from Azure DevOps repository: {}/{}/{} (branch: {})",
                       organization, project, repository, branchName != null ? branchName : "default");

            // Fetch commits with pagination
            int skip = 0;
            int top = 100; // Azure DevOps API limit is typically 1000 per request
            boolean hasMore = true;
            GitVersionDescriptor versionDescriptor = new GitVersionDescriptor();
            versionDescriptor.version = branchName;
            GitCommitsBatch gitCommitsBatch = new GitCommitsBatch();
            gitCommitsBatch.top = top;
            gitCommitsBatch.skip = skip;
            gitCommitsBatch.fromDate = since.toString();
            gitCommitsBatch.showOldestCommitsFirst = false;
            gitCommitsBatch.itemVersion = versionDescriptor;

            while (hasMore) {
                try {
                    GitCommitRefs commitRefs = gitApi.getCommitsBatch(repository, gitCommitsBatch);
                    List<GitCommitRef> commitRefsList = commitRefs.getGitCommitRefs();
                    if (commitRefsList == null || commitRefsList.isEmpty()) {
                        hasMore = false;
                        break;
                    }

                    // Filter by date range if specified
                    if (since != null || until != null) {
                        commitRefsList = commitRefsList.stream()
                            .filter(commit -> {
                                if (commit.getCommitter() == null || commit.getCommitter().getDate() == null) {
                                    return true; // Include commits without date info
                                }

                                try {
                                    // Parse the date string to LocalDateTime
                                    String dateStr = commit.getCommitter().getDate();
                                    LocalDateTime commitDate = LocalDateTime.parse(dateStr.substring(0, 19));

                                    boolean afterSince = since == null || !commitDate.isBefore(since);
                                    boolean beforeUntil = until == null || !commitDate.isAfter(until);

                                    return afterSince && beforeUntil;
                                } catch (Exception e) {
                                    logger.warn("Failed to parse commit date: {}", e.getMessage());
                                    return true; // Include commits with unparseable dates
                                }
                            })
                            .collect(Collectors.toList());
                    }

                    allCommits.addAll(commitRefsList);

                    // Check if we've reached the end or our limit
                    if (commitRefsList.size() < top) {
                        hasMore = false;
                    } else {
                        skip += top;
                    }
                    gitCommitsBatch.skip = skip;

                    logger.debug("Fetched {} commits (total: {}) from Azure DevOps repository: {}/{}/{}",
                               commitRefsList.size(), allCommits.size(), organization, project, repository);

                } catch (Exception e) {
                    logger.warn("Failed to fetch commits batch (skip: {}, top: {}) from Azure DevOps: {}", skip, top, e.getMessage());
                    hasMore = false;
                }
            }

            logger.info("Successfully fetched {} commits from Azure DevOps repository: {}/{}/{}",
                       allCommits.size(), organization, project, repository);
            return allCommits;

        } catch (Exception e) {
            logger.error("Failed to fetch commits from Azure DevOps repository {}/{}/{}: {}",
                        organization, project, repository, e.getMessage());
            throw new Exception("Failed to fetch commits from Azure DevOps", e);
        }
    }

    /**
     * Fetches pull requests from an Azure DevOps repository with date filtering.
     *
     * @param organization Azure DevOps organization
     * @param project Azure DevOps project
     * @param repository Repository name
     * @param token Azure DevOps access token
     * @param since Start date for pull request filtering
     * @param until End date for pull request filtering
     * @return List of GitPullRequest objects
     * @throws Exception if fetching pull requests fails
     */
    public List<GitPullRequest> fetchPullRequests(String organization, String project, String repository,
                                                 String token, LocalDateTime since, String branch) throws Exception {
        Connection connection = getAzureDevOpsConnection(token, organization, project);
        GitApi gitApi = new GitApi(connection);

        List<GitPullRequest> allPullRequests = new ArrayList<>();

        try {
            logger.info("Fetching pull requests from Azure DevOps repository: {}/{}/{}", organization, project, repository);

            // Fetch pull requests with pagination
            int skip = 0;
            int top = 100;
            boolean hasMore = true;
            GitPullRequestQueryParameters gitPullRequestQueryParameters = new GitPullRequestQueryParameters();
            gitPullRequestQueryParameters.top = top;
            gitPullRequestQueryParameters.skip = skip;
            gitPullRequestQueryParameters.status = PullRequestStatus.ALL; // Fetch all pull requests
            gitPullRequestQueryParameters.targetRefName = "refs/heads/" + branch;
//            gitPullRequestQueryParameters.minTime = since.toLocalDate().toString();

            while (hasMore) {
                try {
                    var pullRequestsResponse = gitApi.getPullRequests(repository, gitPullRequestQueryParameters);
                    List<GitPullRequest> pullRequests = pullRequestsResponse.getPullRequests();

                    if (pullRequests == null || pullRequests.isEmpty()) {
                        hasMore = false;
                        break;
                    }

                    // Filter by date range if specified
                    if (since != null) {
                        pullRequests = pullRequests.stream()
                            .filter(pr -> {
                                if (pr.getCreationDate() == null) {
                                    return true; // Include PRs without date info
                                }

                                try {
                                    // Parse the date string to LocalDateTime
                                    String dateStr = pr.getCreationDate();
                                    LocalDateTime prDate = LocalDateTime.parse(dateStr.substring(0, 19));

                                    boolean afterSince = since == null || !prDate.isBefore(since);

                                    return afterSince;
                                } catch (Exception e) {
                                    logger.warn("Failed to parse pull request date: {}", e.getMessage());
                                    return true; // Include PRs with unparseable dates
                                }
                            })
                            .collect(Collectors.toList());
                    }

                    allPullRequests.addAll(pullRequests);

                    // Check if we've reached the end or our limit
                    if (pullRequests.size() < top) {
                        hasMore = false;
                    } else {
                        skip += top;
                        gitPullRequestQueryParameters.skip = skip;
                    }

                    logger.debug("Fetched {} pull requests (total: {}) from Azure DevOps repository: {}/{}/{}",
                               pullRequests.size(), allPullRequests.size(), organization, project, repository);

                } catch (Exception e) {
                    logger.warn("Failed to fetch pull requests batch (skip: {}, top: {}) from Azure DevOps: {}", skip, top, e.getMessage());
                    hasMore = false;
                }
            }

            logger.info("Successfully fetched {} pull requests from Azure DevOps repository: {}/{}/{}",
                       allPullRequests.size(), organization, project, repository);
            return allPullRequests;

        } catch (Exception e) {
            logger.error("Failed to fetch pull requests from Azure DevOps repository {}/{}/{}: {}",
                        organization, project, repository, e.getMessage());
            throw new Exception("Failed to fetch pull requests from Azure DevOps", e);
        }
    }

    public long getPullRequestPickupTime(String organization, String project, String repository,
                                       String token, GitPullRequest azurePrId)  {
        long prPickupTime = 0L;
        try {
            // PAT authentication requires Basic auth with empty username and PAT as password
            String credentials = "Basic " + Base64.getEncoder().encodeToString((":"+token).getBytes());
            int bufferSize = 1024 * 1024;

            WebClient webClient = webClientBuilder
                    .baseUrl(azureDevOpsApiUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, credentials)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(bufferSize))
                    .build();

            String creationDateStr = azurePrId.getCreationDate();

            if (creationDateStr == null || creationDateStr.isEmpty()) {
                logger.warn("PR creation date is null for PR ID: {}", azurePrId);
                return prPickupTime;
            }

            // Get the PR threads to find comments
            String threadsUrl = String.format("/%s/%s/_apis/git/repositories/%s/pullrequests/%s/threads?api-version=7.1",
                    organization, project, repository, azurePrId.getPullRequestId().toString());

            String threadsResponse = webClient.get()
                    .uri(threadsUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode rootNode = objectMapper.readTree(threadsResponse);
            JsonNode threadsArray = rootNode.path("value");

            if (threadsArray.isMissingNode() || threadsArray.isEmpty()) {
                logger.debug("No threads found for PR ID: {}", azurePrId);
                return prPickupTime;
            }

            // Convert PR creation time to LocalDateTime
            LocalDateTime creationTime = LocalDateTime.parse(creationDateStr.substring(0, 19));
            LocalDateTime firstReviewTime = null;

            // Process threads to find first activity
            for (JsonNode thread : threadsArray) {
                JsonNode comments = thread.path("comments");
                if (comments.isMissingNode() || comments.isEmpty()) {
                    continue;
                }

                for (JsonNode comment : comments) {
                    // Skip comments by PR creator

                    // Parse comment time
                    String commentDateStr = comment.path("publishedDate").asText();
                    if (commentDateStr == null || commentDateStr.isEmpty()) continue;

                    try {
                        LocalDateTime commentTime = LocalDateTime.parse(commentDateStr.substring(0, 19));

                        // Check if this is the earliest review activity
                        if (commentTime.isAfter(creationTime) &&
                                (firstReviewTime == null || commentTime.isBefore(firstReviewTime))) {
                            firstReviewTime = commentTime;
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse comment date: {}", commentDateStr);
                    }
                }
            }

            // Calculate pickup time in milliseconds
            if (firstReviewTime != null) {
                prPickupTime = firstReviewTime.toInstant(ZoneOffset.UTC).toEpochMilli();

                logger.debug("PR pickup time for PR #{}: {} ms", azurePrId, prPickupTime);
            } else {
                logger.debug("No review activity found for PR #{}", azurePrId);
            }

        } catch (Exception e) {
            logger.error("Failed to fetch pull request pickup time from Azure DevOps repository {}/{}/{}: {}",
                    organization, project, repository, e.getMessage());
        }
        return prPickupTime;
    }

    public ScmCommits.FileChange getCommitDiffStats(String organization, String project, String repository,
            String commitId, String token)  {

        ScmCommits.FileChange fileChange = new ScmCommits.FileChange();
        try {
            Connection connection = getAzureDevOpsConnection(token, organization, project);
            GitApi gitApi = new GitApi(connection);
            GitCommitChanges commit = gitApi.getChanges(repository, commitId);
            List<GitChange> fileChanges = commit.getChanges();

            // Get parent commit for old content
            GitCommit commitRef = gitApi.getCommit(repository, commitId);
            List<String> parents = List.of(commitRef.getParents());

            String parentCommitId = (parents != null && !parents.isEmpty()) ? parents.get(0) : null;

//            for (GitChange change : fileChanges) {
//                String filePath = change.getItem().getPath();
//
//                String oldContent = "";
//                if (parentCommitId != null) {
//                    oldContent = gitApi.getBlobContent(repository, parentCommitId, false, filePath, false);
//                }
//                String newContent = gitApi.getBlobContent(repository, commitId, false, filePath, false);
//
//                // Use java-diff-utils for line-level diff
//                List<String> original = Arrays.asList(oldContent.split("\n"));
//                List<String> revised = Arrays.asList(newContent.split("\n"));
//                Patch<String> patch = DiffUtils.diff(original, revised);
//
//                int added = 0, deleted = 0;
//                for (AbstractDelta<String> delta : patch.getDeltas()) {
//                    switch (delta.getType()) {
//                        case INSERT:
//                            added += delta.getTarget().size();
//                            break;
//                        case DELETE:
//                            deleted += delta.getSource().size();
//                            break;
//                        case CHANGE:
//                            added += delta.getTarget().size();
//                            deleted += delta.getSource().size();
//                            break;
//                    }
//                }
//                fileChange.setAdded(added);
//                fileChange.setDeleted(deleted);
//                // You can also collect per-file stats in a list if needed
//            }

            if (commit == null || commit.getChangeCounts() == null) {
                logger.warn("No change counts found for commit: {}", commitId);
                return new ScmCommits.FileChange();
            }
//            fileChange.setAdded(commit.getChangeCounts().getAdd());
//            fileChange.setModified(commit.getChangeCounts().getEdit());
//            fileChange.setDeleted(commit.getChangeCounts().getDelete());

            logger.debug("Fetched diff stats for commit {}: {}", commitId, fileChange);

        } catch (Exception e) {
            logger.error("Failed to fetch diff stats for commit {}: {}", commitId, e.getMessage());
//            throw new Exception("Failed to fetch commit diff stats", e);
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