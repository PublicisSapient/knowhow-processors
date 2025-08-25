package com.publicissapient.knowhow.processor.scm.service.core;

import com.publicissapient.knowhow.processor.scm.constants.ScmConstants;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.strategy.CommitDataFetchStrategy;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformService;
import com.publicissapient.knowhow.processor.scm.service.strategy.RestApiCommitDataFetchStrategy;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser.GitUrlInfo;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Core service for scanning Git repositories and collecting metadata.
 * 
 * This service orchestrates the scanning process by:
 * 1. Determining the appropriate strategy for data fetching
 * 2. Fetching commit data using the selected strategy
 * 3. Fetching merge request data using platform APIs
 * 4. Persisting the collected data to the database
 * 
 * Implements the Facade pattern to provide a simplified interface
 * for the complex scanning operations.
 */
@Service
public class GitScannerService {

    private static final Logger logger = LoggerFactory.getLogger(GitScannerService.class);

    private final PersistenceService persistenceService;
    private final Map<String, CommitDataFetchStrategy> commitStrategies;
    private final Map<String, GitPlatformService> platformServices;
    private final GitUrlParser gitUrlParser;

    @Value("${git.scanner.default-commit-strategy:jgit}")
    private String defaultCommitStrategy;

    @Value("${git.scanner.use-rest-api-for-commits:false}")
    private boolean useRestApiForCommits;

    @Value("${git.scanner.first-scan-from:6}")
    private int firstScanFromMonths;

    @Value("${git.scanner.pagination.max-merge-requests-per-scan:5000}")
    private int maxMergeRequestsPerScan;

    @Autowired
    public GitScannerService(PersistenceService persistenceService,
                           Map<String, CommitDataFetchStrategy> commitStrategies,
                           Map<String, GitPlatformService> platformServices,
                           GitUrlParser gitUrlParser) {
        this.persistenceService = persistenceService;
        this.commitStrategies = commitStrategies;
        this.platformServices = platformServices;
        this.gitUrlParser = gitUrlParser;

        // Log available strategies for debugging
        logger.info("GitScannerService initialized with {} commit strategies: {}",
                commitStrategies.size(), commitStrategies.keySet());
        logger.info("GitScannerService initialized with {} platform services: {}",
                platformServices.size(), platformServices.keySet());
    }

    /**
     * Scans a repository asynchronously and collects all metadata.
     * 
     * @param scanRequest the scan request containing repository information
     * @return CompletableFuture with scan results
     */
    @Async
    public CompletableFuture<ScanResult> scanRepositoryAsync(ScanRequest scanRequest) {
        logger.info("Starting async scan for repository: {}", scanRequest.getRepositoryUrl());
        
        try {
            ScanResult result = scanRepository(scanRequest);
            logger.info("Completed async scan for repository: {}", scanRequest.getRepositoryUrl());
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            logger.error("Error during async scan for repository {}: {}", 
                    scanRequest.getRepositoryUrl(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Scans a repository synchronously and collects all metadata.
     *
     * @param scanRequest the scan request containing repository information
     * @return scan results
     * @throws DataProcessingException if scanning fails
     */
    public ScanResult scanRepository(ScanRequest scanRequest) throws DataProcessingException {
        logger.info("Starting scan for repository: {} ({})", scanRequest.getRepositoryName(), scanRequest.getRepositoryUrl());

        ScanResult result = null;
        long startTime = System.currentTimeMillis();
        ScanResult.Builder resultBuilder = ScanResult.builder()
                .repositoryUrl(scanRequest.getRepositoryUrl())
                .repositoryName(scanRequest.getRepositoryName())
                .startTime(LocalDateTime.now());

        try {
            // Fetch commits
            List<ScmCommits> commitDetails = fetchCommits(scanRequest);
            resultBuilder.commitsFound(commitDetails.size());

            // Process and populate users from commits
            Set<User> usersFromCommits = extractUsersFromCommits(commitDetails, scanRequest.getRepositoryName());

            // Fetch merge requests
            List<ScmMergeRequests> mergeRequests = fetchMergeRequests(scanRequest);
            resultBuilder.mergeRequestsFound(mergeRequests.size());

            // Process and populate users from merge requests
            Set<User> usersFromMergeRequests = extractUsersFromMergeRequests(mergeRequests, scanRequest.getRepositoryName());

            // Combine all users and persist them first
            Set<User> allUsers = new HashSet<>();
            allUsers.addAll(usersFromCommits);
            allUsers.addAll(usersFromMergeRequests);

            Map<String, User> userMap = new HashMap<>();
            if (!allUsers.isEmpty()) {
                for (User user : allUsers) {
                    if(user.getUsername() != null && user.getEmail() != null) {
                        User savedUser = persistenceService.saveUser(user);
                        userMap.put(savedUser.getUsername(), savedUser);
                    }
                }
                logger.info("Processed {} unique users for repository: {} ({})",
                        allUsers.size(), scanRequest.getRepositoryName(), scanRequest.getRepositoryUrl());
            }

            // Update commits with user references and repository name
            updateCommitsWithUserReferences(commitDetails, userMap, scanRequest.getRepositoryName());

            // Persist commits
            if (!commitDetails.isEmpty()) {
                commitDetails.forEach(commit -> commit.setProcessorItemId(scanRequest.getToolConfigId()));
                persistenceService.saveCommits(commitDetails);
                logger.info("Persisted {} commits for repository: {} ({})",
                        commitDetails.size(), scanRequest.getRepositoryName(), scanRequest.getRepositoryUrl());
            }

            // Update merge requests with user references and repository name
            updateMergeRequestsWithUserReferences(mergeRequests, userMap, scanRequest.getRepositoryName());

            // Persist merge requests
            if (!mergeRequests.isEmpty()) {
                persistenceService.saveMergeRequests(mergeRequests);
                logger.info("Persisted {} merge requests for repository: {} ({})",
                        mergeRequests.size(), scanRequest.getRepositoryName(), scanRequest.getRepositoryUrl());
            }

            long duration = System.currentTimeMillis() - startTime;
            result = resultBuilder
                    .endTime(LocalDateTime.now())
                    .durationMs(duration)
                    .success(true)
                    .usersFound(allUsers.size())
                    .build();

            logger.info("Successfully completed scan for repository: {} ({}) in {}ms",
                    scanRequest.getRepositoryName(), scanRequest.getRepositoryUrl(), duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
             result = resultBuilder
                    .endTime(LocalDateTime.now())
                    .durationMs(duration)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();

            logger.error("Failed to scan repository: {} ({})",
                    scanRequest.getRepositoryName(), scanRequest.getRepositoryUrl(), e);
            throw new DataProcessingException("Repository scan failed", e);
        }
        return result;
    }

    /**
     * Fetches commits for a repository using the appropriate strategy.
     * 
     * @param scanRequest the scan request
     * @return list of commits
     * @throws DataProcessingException if fetching fails
     */
    private List<ScmCommits> fetchCommits(ScanRequest scanRequest) throws DataProcessingException {
        logger.debug("Fetching commits for repository: {} ({})",
                scanRequest.getRepositoryName(), scanRequest.getRepositoryUrl());

        CommitDataFetchStrategy strategy = determineCommitStrategy(scanRequest);

        if (strategy == null) {
            throw new DataProcessingException("No suitable commit fetch strategy found for repository: "
                    + scanRequest.getRepositoryUrl());
        }

        logger.debug("Using {} strategy for commit fetching", strategy.getStrategyName());

        // Get the platform service based on toolType from the request
        GitPlatformService platformService = getPlatformService(scanRequest);
        if (platformService == null) {
            throw new DataProcessingException("No platform service found for toolType: " + scanRequest.getToolType());
        }

        CommitDataFetchStrategy.RepositoryCredentials credentials =
                CommitDataFetchStrategy.RepositoryCredentials.builder()
                        .username(scanRequest.getUsername())
                        .token(scanRequest.getToken())
                        .build();

        // Set repository URL context for GitLab service before calling strategy
        if (platformService instanceof com.publicissapient.knowhow.processor.scm.service.platform.gitlab.GitLabService) {
            com.publicissapient.knowhow.processor.scm.service.platform.gitlab.GitLabService gitLabService =
                (com.publicissapient.knowhow.processor.scm.service.platform.gitlab.GitLabService) platformService;
            gitLabService.setRepositoryUrlContext(scanRequest.getRepositoryUrl());
            gitLabService.setRepositoryUrlContext(scanRequest.getRepositoryUrl());
        }

        // Determine the date range for commits based on lastScanFrom and firstScanFrom logic
        LocalDateTime commitsSince = null;
        GitUrlParser.GitUrlInfo urlInfo = gitUrlParser.parseGitUrl(scanRequest.getRepositoryUrl(), scanRequest.getToolType(), credentials.getUsername(), scanRequest.getRepositoryName());

        // Extract repository information
        if (urlInfo == null) {
            throw new DataProcessingException("Invalid repository URL: " + scanRequest.getRepositoryName());
        }

        if (scanRequest.getLastScanFrom() != null && scanRequest.getLastScanFrom() != 0L) {
            // If lastScanFrom is provided, use it as the start date for commits
            commitsSince = LocalDateTime.ofEpochSecond(scanRequest.getLastScanFrom() / 1000, 0, java.time.ZoneOffset.UTC);
            logger.debug("Using lastScanFrom timestamp for commits: {}", commitsSince);
        } else if (scanRequest.getSince() != null) {
            // Use the provided since date
            commitsSince = scanRequest.getSince();
        } else {
            // Use firstScanFromMonths to calculate the start date
            commitsSince = LocalDateTime.now().minusMonths(firstScanFromMonths);
            logger.debug("Using firstScanFrom ({} months) for commits: {}", firstScanFromMonths, commitsSince);
        }

        return strategy.fetchCommits(
                scanRequest.toolType,
                scanRequest.toolConfigId.toString(),
                urlInfo,
                scanRequest.getBranchName(),
                credentials,
                commitsSince
        );
    }

    /**
     * Fetches merge requests from the platform service with optimized logic.
     *
     * This method implements a dual-fetch strategy:
     * 1. Fetches new merge requests based on lastScanFrom (incremental)
     * 2. Fetches updates for existing open merge requests in the database
     * 3. Combines and deduplicates the results for optimal performance
     *
     * @param scanRequest the scan request containing repository and authentication details
     * @return list of merge requests to be processed
     * @throws PlatformApiException if there's an error fetching merge requests
     */
    private List<ScmMergeRequests> fetchMergeRequests(ScanRequest scanRequest) throws PlatformApiException {
        GitPlatformService platformService = getPlatformService(scanRequest);
        GitUrlInfo urlInfo = gitUrlParser.parseGitUrl(scanRequest.getRepositoryUrl(), scanRequest.toolType, scanRequest.getUsername(), scanRequest.repositoryName);

        String identifier;
       if (scanRequest.getToolType() != null) {
            identifier = scanRequest.getToolType();
        } else {
            identifier = scanRequest.getRepositoryName();
        }

        logger.info("Starting optimized merge request fetch for identifier: {}", identifier);

        // Step 1: Fetch new merge requests using lastScanFrom (incremental logic)
        List<ScmMergeRequests> newMergeRequests = fetchNewMergeRequests(scanRequest, platformService, urlInfo, identifier);
        logger.info("Fetched {} new merge requests", newMergeRequests.size());

        // Step 2: Fetch updates for existing open merge requests
        List<ScmMergeRequests> updatedOpenMergeRequests = fetchUpdatesForOpenMergeRequests(scanRequest, platformService, urlInfo, identifier);
        logger.info("Fetched {} updated open merge requests", updatedOpenMergeRequests.size());

        // Step 3: Combine and deduplicate results
        List<ScmMergeRequests> combinedMergeRequests = combineAndDeduplicateMergeRequests(newMergeRequests, updatedOpenMergeRequests);
        logger.info("Combined total: {} unique merge requests after deduplication", combinedMergeRequests.size());

        return combinedMergeRequests;
    }

    /**
     * Fetches new merge requests based on lastScanFrom logic (incremental scanning).
     * Uses updated date for filtering to capture all merge requests that have been modified.
     */
    private List<ScmMergeRequests> fetchNewMergeRequests(ScanRequest scanRequest, GitPlatformService platformService,
                                                      GitUrlInfo urlInfo, String identifier) throws PlatformApiException {

        // Determine the date range for new merge requests based on lastScanFrom and firstScanFrom logic
        // Using updated date instead of created date to capture all modified merge requests
        LocalDateTime mergeRequestsSince = null;
        LocalDateTime mergeRequestsUntil = scanRequest.getUntil();

        if (scanRequest.getLastScanFrom() != null && scanRequest.getLastScanFrom() != 0L) {
            // If lastScanFrom is provided, use it as the start date for merge requests (based on updated date)
            mergeRequestsSince = LocalDateTime.ofEpochSecond(scanRequest.getLastScanFrom() / 1000, 0, java.time.ZoneOffset.UTC);
            logger.debug("Using lastScanFrom timestamp for merge requests (updated date filter): {}", mergeRequestsSince);
        } else if (scanRequest.getSince() != null) {
            // Use the provided since date if available (based on updated date)
            mergeRequestsSince = scanRequest.getSince();
            logger.debug("Using provided since date for merge requests (updated date filter): {}", mergeRequestsSince);
        } else {
            // Use firstScanFromMonths as fallback (based on updated date)
            mergeRequestsSince = LocalDateTime.now().minusMonths(firstScanFromMonths);
            logger.debug("Using firstScanFrom ({} months) for merge requests (updated date filter): {}", firstScanFromMonths, mergeRequestsSince);
        }

        logger.info("Fetching merge requests updated since: {} for identifier: {}", mergeRequestsSince, identifier);

        // Create final copies for lambda usage
        final LocalDateTime finalMergeRequestsSince = mergeRequestsSince;
        final LocalDateTime finalMergeRequestsUntil = mergeRequestsUntil;

        String token;
        if(scanRequest.getToolType().equalsIgnoreCase(ScmConstants.BITBUCKET)) {
            token = scanRequest.getUsername()+":"+scanRequest.getToken();
        } else {
            token = scanRequest.getToken();
        }

        // Fetch merge requests from the platform using updated date filtering
        return callPlatformServiceWithContext(platformService, scanRequest, () ->
            platformService.fetchMergeRequests(
                    scanRequest.getToolConfigId().toString(),
                    urlInfo,
                    scanRequest.getBranchName(),
                    token,
                    finalMergeRequestsSince,
                    finalMergeRequestsUntil
            )
        );
    }

    /**
     * Fetches updates for existing open merge requests in the database.
     * This ensures that open MRs get updated with their latest status (closed, merged, etc.).
     */
    /**
     * Fetches updates for existing open merge requests in the database.
     * This ensures that open MRs get updated with their latest status (closed, merged, etc.).
     * Uses updated date filtering to capture all recent changes.
     */
    private List<ScmMergeRequests> fetchUpdatesForOpenMergeRequests(ScanRequest scanRequest, GitPlatformService platformService,
                                                                 GitUrlInfo urlInfo, String identifier) throws PlatformApiException {

        // Get existing open merge requests from database
        List<ScmMergeRequests> existingOpenMRs = getExistingOpenMergeRequests(identifier);

        if (existingOpenMRs.isEmpty()) {
            logger.debug("No existing open merge requests found for identifier: {}", identifier);
            return List.of();
        }

        logger.info("Found {} existing open merge requests to update", existingOpenMRs.size());

        // Fetch updates for these open merge requests from the platform
        // We'll fetch all MRs updated within a reasonable time window to capture status changes
        LocalDateTime updatesSince = calculateUpdateWindowStart(existingOpenMRs);

        logger.debug("Fetching MR updates since: {} for {} existing open MRs (based on updated date)",
            updatesSince, existingOpenMRs.size());

        String token;
        if(scanRequest.getToolType().equalsIgnoreCase(ScmConstants.BITBUCKET)) {
            token = scanRequest.getUsername()+":"+scanRequest.getToken();
        } else {
            token = scanRequest.getToken();
        }
        // Fetch merge requests updated since the calculated time to capture state changes
        List<ScmMergeRequests> allRecentMRs = callPlatformServiceWithContext(platformService, scanRequest, () ->
            platformService.fetchMergeRequests(
                    scanRequest.getToolConfigId().toString(),
                    urlInfo,
                    scanRequest.getBranchName(),
                    token,
                    updatesSince,
                    null // No end date limit for updates
            )
        );

        // Filter to only return MRs that correspond to our existing open MRs
        return filterRelevantUpdates(allRecentMRs, existingOpenMRs);
    }

    /**
     * Gets existing open merge requests from the database for the given identifier.
     */
    private List<ScmMergeRequests> getExistingOpenMergeRequests(String identifier) {
        try {
            // Use pagination to handle large datasets efficiently
            Pageable pageable = PageRequest.of(0, maxMergeRequestsPerScan);
            Page<ScmMergeRequests> openMRsPage = persistenceService.findMergeRequestsByToolConfigIdAndState(
                    new ObjectId(identifier),
                    ScmMergeRequests.MergeRequestState.OPEN,
                    pageable
            );

            List<ScmMergeRequests> allOpenMRs = new ArrayList<>(openMRsPage.getContent());

            // If there are more pages, fetch them (but limit to reasonable amount)
            int maxPages = 10; // Limit to prevent excessive memory usage
            int currentPage = 1;

            while (openMRsPage.hasNext() && currentPage < maxPages) {
                pageable = PageRequest.of(currentPage, maxMergeRequestsPerScan);
                openMRsPage = persistenceService.findMergeRequestsByToolConfigIdAndState(
                        new ObjectId(identifier),
                        ScmMergeRequests.MergeRequestState.OPEN,
                        pageable
                );
                allOpenMRs.addAll(openMRsPage.getContent());
                currentPage++;
            }

            return allOpenMRs;
        } catch (Exception e) {
            logger.warn("Failed to fetch existing open merge requests for identifier {}: {}", identifier, e.getMessage());
            return List.of();
        }
    }

    /**
     * Calculates the start date for fetching updates based on existing open MRs.
     * Uses the oldest creation date among open MRs, with a reasonable minimum window.
     */
    private LocalDateTime calculateUpdateWindowStart(List<ScmMergeRequests> existingOpenMRs) {
        // Find the oldest creation date among open MRs
        LocalDateTime oldestCreationDate = existingOpenMRs.stream()
                .map(ScmMergeRequests::getUpdatedOn)
                .filter(date -> date != null)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusMonths(3)); // Default to 3 months if no dates found

        // Ensure we don't go back more than 6 months for performance reasons
        LocalDateTime maxLookback = LocalDateTime.now().minusMonths(6);

        return oldestCreationDate.isBefore(maxLookback) ? maxLookback : oldestCreationDate;
    }

    /**
     * Filters the recent MRs to only include those that correspond to existing open MRs.
     */
    private List<ScmMergeRequests> filterRelevantUpdates(List<ScmMergeRequests> allRecentMRs, List<ScmMergeRequests> existingOpenMRs) {
        // Create a set of external IDs from existing open MRs for efficient lookup
        Set<String> existingOpenMRIds = existingOpenMRs.stream()
                .map(ScmMergeRequests::getExternalId)
                .collect(Collectors.toSet());

        // Filter recent MRs to only include those that match existing open MRs
        return allRecentMRs.stream()
                .filter(mr -> existingOpenMRIds.contains(mr.getExternalId()))
                .collect(Collectors.toList());
    }

    /**
     * Combines new merge requests and updated open merge requests, removing duplicates.
     * Priority is given to the updated versions over new versions.
     */
    private List<ScmMergeRequests> combineAndDeduplicateMergeRequests(List<ScmMergeRequests> newMergeRequests,
                                                                   List<ScmMergeRequests> updatedOpenMergeRequests) {

        // Use a map to deduplicate by external ID, giving priority to updated versions
        Map<String, ScmMergeRequests> mergeRequestMap = new HashMap<>();

        // First add new merge requests
        for (ScmMergeRequests mr : newMergeRequests) {
            if (mr.getExternalId() != null) {
                mergeRequestMap.put(mr.getExternalId(), mr);
            }
        }

        // Then add/overwrite with updated open merge requests (these take priority)
        for (ScmMergeRequests mr : updatedOpenMergeRequests) {
            if (mr.getExternalId() != null) {
                mergeRequestMap.put(mr.getExternalId(), mr);
                logger.debug("Updated MR #{} with latest status: {}", mr.getExternalId(), mr.getState());
            }
        }

        return new ArrayList<>(mergeRequestMap.values());
    }

    /**
     * Determines the appropriate commit fetch strategy based on configuration and request.
     * 
     * @param scanRequest the scan request
     * @return the selected strategy
     */
    private CommitDataFetchStrategy determineCommitStrategy(ScanRequest scanRequest) {
        logger.debug("Determining commit strategy for repository: {}, isCloneEnabled: {}",
                scanRequest.getRepositoryUrl(), scanRequest.isCloneEnabled());

        // Check if a specific strategy is requested
        if (scanRequest.getCommitFetchStrategy() != null) {
            CommitDataFetchStrategy strategy = commitStrategies.get(scanRequest.getCommitFetchStrategy());
            if (strategy != null && supportsStrategy(strategy, scanRequest)) {
                logger.debug("Using explicitly requested strategy: {}", scanRequest.getCommitFetchStrategy());
                return strategy;
            }
        }

        // Use the cloneEnabled flag to determine strategy
        String strategyName = scanRequest.isCloneEnabled() ? "jGitCommitDataFetchStrategy" : "restApiCommitDataFetchStrategy";
        logger.debug("Selected strategy based on isCloneEnabled flag: {}", strategyName);

        CommitDataFetchStrategy strategy = commitStrategies.get(strategyName);

        if (strategy != null && supportsStrategy(strategy, scanRequest)) {
            logger.debug("Successfully found and validated strategy: {}", strategyName);
            return strategy;
        } else {
            logger.warn("Strategy {} not found or doesn't support repository URL: {}", strategyName, scanRequest.getRepositoryUrl());
        }

        // Fallback to any available strategy
        logger.debug("Falling back to any available strategy");
        return commitStrategies.values().stream()
                .filter(s -> supportsStrategy(s, scanRequest))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if a strategy supports the given scan request.
     * Uses toolType when available, falls back to URL-based checking.
     *
     * @param strategy the strategy to check
     * @param scanRequest the scan request
     * @return true if supported, false otherwise
     */
    private boolean supportsStrategy(CommitDataFetchStrategy strategy, ScanRequest scanRequest) {
        // For RestApiCommitDataFetchStrategy, try to use toolType first
        if (strategy instanceof RestApiCommitDataFetchStrategy && scanRequest.getToolType() != null) {
            RestApiCommitDataFetchStrategy restApiStrategy = (RestApiCommitDataFetchStrategy) strategy;
            if (restApiStrategy.supportsByToolType(scanRequest.getToolType())) {
                return true;
            }
        }

        // Fallback to URL-based support checking
        return strategy.supports(scanRequest.getRepositoryUrl(), scanRequest.getToolType());
    }

    /**
     * Gets the platform service for a repository URL (fallback method).
     *
     * @param scanRequest the repository URL
     * @return the platform service or null if not found
     */
    private GitPlatformService getPlatformService(ScanRequest scanRequest) {

        String serviceName = mapPlatformToServiceName(scanRequest.getToolType().toLowerCase());
        GitPlatformService service = platformServices.get(serviceName);

        if (service != null) {
            logger.debug("Found platform service '{}' for toolType: {}", serviceName, scanRequest.getToolType());
        } else {
            logger.warn("No platform service found for toolType: {}. Available services: {}",
                       scanRequest.getToolType(), platformServices.keySet());
        }

        return service;
    }

    /**
     * Gets the platform service for a repository URL (fallback method).
     *
     * @param repositoryUrl the repository URL
     * @return the platform service or null if not found
     */
    private GitPlatformService getPlatformServiceByUrl(String repositoryUrl) {
        if (repositoryUrl == null) {
            return null;
        }

        String platform = determinePlatform(repositoryUrl);
        if (platform == null) {
            return null;
        }

        String serviceName = mapPlatformToServiceName(platform.toLowerCase());
        return platformServices.get(serviceName);
    }

    /**
     * Maps platform names to their corresponding Spring service bean names.
     * This handles the discrepancy between generated keys and actual bean names.
     *
     * @param platform the platform name (lowercase)
     * @return the corresponding service bean name
     */
    private String mapPlatformToServiceName(String platform) {
        switch (platform) {
            case "github":
                return "gitHubService";
            case "gitlab":
                return "gitLabService";
            case "azurerepository":
                return "azureDevOpsService";
            case "bitbucket":
                return "bitbucketService";
            default:
                // Fallback to the original pattern for any new platforms
                return platform + "Service";
        }
    }

    /**
     * Determines the platform from a repository URL.
     *
     * @param repositoryUrl the repository URL
     * @return the platform name or null if not recognized
     */
    private String determinePlatform(String repositoryUrl) {
        String lowerUrl = repositoryUrl.toLowerCase();

        if (lowerUrl.contains("github.com")) {
            return "github";
        } else if (lowerUrl.contains("gitlab.com") || lowerUrl.contains("gitlab")) {
            return "gitlab";
        } else if (lowerUrl.contains("dev.azure.com") || lowerUrl.contains("visualstudio.com")) {
            return "azure";
        } else if (lowerUrl.contains("bitbucket.org") || lowerUrl.contains("bitbucket")) {
            return "bitbucket";
        }

        return null;
    }

    /**
     * Data class for scan requests.
     */
    public static class ScanRequest {
        private String repositoryUrl;
        private String repositoryName;
        private String branchName;
        private String username;
        private String token;
        private String toolType;
        private org.bson.types.ObjectId toolConfigId;
        private boolean cloneEnabled;
        private LocalDateTime since;
        private LocalDateTime until;
        private int limit;
        private String commitFetchStrategy;
        private Long lastScanFrom;  // Added lastScanFrom field

        private ScanRequest(Builder builder) {
            this.repositoryUrl = builder.repositoryUrl;
            this.repositoryName = builder.repositoryName;
            this.branchName = builder.branchName;
            this.username = builder.username;
            this.token = builder.token;
            this.toolType = builder.toolType;
            this.toolConfigId = builder.toolConfigId;  // Added toolConfigId assignment
            this.cloneEnabled = builder.cloneEnabled;
            this.since = builder.since;
            this.until = builder.until;
            this.limit = builder.limit;
            this.commitFetchStrategy = builder.commitFetchStrategy;
            this.lastScanFrom = builder.lastScanFrom;  // Added lastScanFrom assignment
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public String getRepositoryUrl() { return repositoryUrl; }
        public String getRepositoryName() { return repositoryName; }
        public String getBranchName() { return branchName; }
        public String getUsername() { return username; }
        public String getToken() { return token; }
        public String getToolType() { return toolType; }
        public org.bson.types.ObjectId getToolConfigId() { return toolConfigId; }  // Added toolConfigId getter
        public boolean isCloneEnabled() { return cloneEnabled; }
        public LocalDateTime getSince() { return since; }
        public LocalDateTime getUntil() { return until; }
        public int getLimit() { return limit; }
        public String getCommitFetchStrategy() { return commitFetchStrategy; }
        public Long getLastScanFrom() { return lastScanFrom; }  // Added lastScanFrom getter

        public static class Builder {
            private String repositoryUrl;
            private String repositoryName;
            private String branchName;
            private String username;
            private String token;
            private String toolType;
            private ObjectId toolConfigId;  // Added toolConfigId field
            private boolean cloneEnabled;
            private LocalDateTime since;
            private LocalDateTime until;
            private int limit;
            private String commitFetchStrategy;
            private Long lastScanFrom;  // Added lastScanFrom field

            public Builder repositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; return this; }
            public Builder repositoryName(String repositoryName) { this.repositoryName = repositoryName; return this; }
            public Builder branchName(String branchName) { this.branchName = branchName; return this; }
            public Builder username(String username) { this.username = username; return this; }
            public Builder token(String token) { this.token = token; return this; }
            public Builder toolType(String toolType) { this.toolType = toolType; return this; }
            public Builder toolConfigId(org.bson.types.ObjectId toolConfigId) { this.toolConfigId = toolConfigId; return this; }  // Added toolConfigId builder method
            public Builder cloneEnabled(boolean cloneEnabled) { this.cloneEnabled = cloneEnabled; return this; }
            public Builder since(LocalDateTime since) { this.since = since; return this; }
            public Builder until(LocalDateTime until) { this.until = until; return this; }
            public Builder limit(int limit) { this.limit = limit; return this; }
            public Builder commitFetchStrategy(String commitFetchStrategy) { this.commitFetchStrategy = commitFetchStrategy; return this; }
            public Builder lastScanFrom(Long lastScanFrom) { this.lastScanFrom = lastScanFrom; return this; }  // Added lastScanFrom builder method

            public ScanRequest build() {
                return new ScanRequest(this);
            }
        }
    }

    /**
     * Data class for scan results.
     */
    public static class ScanResult {
        private final String repositoryUrl;
        private final String repositoryName;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final long durationMs;
        private final int commitsFound;
        private final int mergeRequestsFound;
        private final int usersFound;
        private final boolean success;
        private final String errorMessage;

        private ScanResult(Builder builder) {
            this.repositoryUrl = builder.repositoryUrl;
            this.repositoryName = builder.repositoryName;
            this.startTime = builder.startTime;
            this.endTime = builder.endTime;
            this.durationMs = builder.durationMs;
            this.commitsFound = builder.commitsFound;
            this.mergeRequestsFound = builder.mergeRequestsFound;
            this.usersFound = builder.usersFound;
            this.success = builder.success;
            this.errorMessage = builder.errorMessage;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public String getRepositoryUrl() { return repositoryUrl; }
        public String getRepositoryName() { return repositoryName; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public long getDurationMs() { return durationMs; }
        public int getCommitsFound() { return commitsFound; }
        public int getMergeRequestsFound() { return mergeRequestsFound; }
        public int getUsersFound() { return usersFound; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }

        public static class Builder {
            private String repositoryUrl;
            private String repositoryName;
            private LocalDateTime startTime;
            private LocalDateTime endTime;
            private long durationMs;
            private int commitsFound;
            private int mergeRequestsFound;
            private int usersFound;
            private boolean success;
            private String errorMessage;

            public Builder repositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; return this; }
            public Builder repositoryName(String repositoryName) { this.repositoryName = repositoryName; return this; }
            public Builder startTime(LocalDateTime startTime) { this.startTime = startTime; return this; }
            public Builder endTime(LocalDateTime endTime) { this.endTime = endTime; return this; }
            public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
            public Builder commitsFound(int commitsFound) { this.commitsFound = commitsFound; return this; }
            public Builder mergeRequestsFound(int mergeRequestsFound) { this.mergeRequestsFound = mergeRequestsFound; return this; }
            public Builder usersFound(int usersFound) { this.usersFound = usersFound; return this; }
            public Builder success(boolean success) { this.success = success; return this; }
            public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

            public ScanResult build() {
                return new ScanResult(this);
            }
        }
    }

    // Helper methods for user processing

    /**
     * Extracts unique users from commits.
     *
     * @param commitDetails the list of commits
     * @param repositoryName the repository name
     * @return set of unique users
     */
    private Set<User> extractUsersFromCommits(List<ScmCommits> commitDetails, String repositoryName) {
        Set<User> users = new HashSet<>();

        for (ScmCommits commitDetail : commitDetails) {

            if(commitDetail.getCommitAuthor() != null) {
                User commitAuthor = commitDetail.getCommitAuthor();
                commitAuthor.setRepositoryName(repositoryName);
                commitAuthor.setActive(true);
                users.add(commitAuthor);
            }
        }

        return users;
    }

    /**
     * Extracts unique users from merge requests.
     *
     * @param mergeRequests the list of merge requests
     * @param repositoryName the repository name
     * @return set of unique users
     */
    private Set<User> extractUsersFromMergeRequests(List<ScmMergeRequests> mergeRequests, String repositoryName) {
        Set<User> users = new HashSet<>();

        for (ScmMergeRequests mr : mergeRequests) {
            // Extract author
            if (mr.getAuthorId() != null) {
                User author = mr.getAuthorId();
                author.setRepositoryName(repositoryName);
                author.setActive(true);
                users.add(author);
            }

            // Extract reviewers
            if (mr.getReviewers() != null) {
                for (String reviewer : mr.getReviewers()) {
                    User user = User.builder()
                        .repositoryName(repositoryName)
                        .username(reviewer)
                        .displayName(reviewer)
                        .active(true)
                        .build();
                    users.add(user);
                }
            }
        }

        return users;
    }

    /**
     * Updates commits with user references and repository name.
     *
     * @param commitDetails the list of commits to update
     * @param userMap the map of users by username/email
     * @param repositoryName the repository name
     */
    private void updateCommitsWithUserReferences(List<ScmCommits> commitDetails, Map<String, User> userMap, String repositoryName) {
        for (ScmCommits commitDetail : commitDetails) {
            commitDetail.setRepositoryName(repositoryName);

            // Set author reference
            if (commitDetail.getAuthorName() != null) {
                User author = userMap.get(commitDetail.getAuthorName());
                if (author == null && commitDetail.getAuthorName() != null) {
                    author = userMap.get(commitDetail.getAuthorName());
                }
                if (author != null) {
                    commitDetail.setCommitAuthor(author);
                    commitDetail.setCommitAuthorId(String.valueOf(author.getId()));
                }
            }

            // Set committer reference
            if (commitDetail.getCommitterName() != null) {
                User committer = userMap.get(commitDetail.getCommitterName());
                if (committer == null && commitDetail.getCommitterName() != null) {
                    committer = userMap.get(commitDetail.getCommitterName());
                }
                if (committer != null) {
                    commitDetail.setCommitter(committer);
                    commitDetail.setCommitterId(String.valueOf(committer.getId()));
                }
            }
        }
    }

    /**
     * Updates merge requests with user references and repository name.
     *
     * @param mergeRequests the list of merge requests to update
     * @param userMap the map of users by username/email
     * @param repositoryName the repository name
     */
    private void updateMergeRequestsWithUserReferences(List<ScmMergeRequests> mergeRequests, Map<String, User> userMap, String repositoryName) {
        for (ScmMergeRequests mr : mergeRequests) {
            mr.setRepositoryName(repositoryName);

            // Set author reference
            if (mr.getAuthorId() != null) {
                User author = userMap.get(mr.getAuthorUserId());
                if (author != null) {
                    mr.setAuthorId(author);
                    mr.setAuthorUserId(String.valueOf(author.getId())); // Set the new authorUserId field
                } else {
                    User user = persistenceService.findOrCreateUser(repositoryName, mr.getAuthorUserId(), mr.getAuthorId().getEmail(), mr.getAuthorId().getDisplayName());
                    mr.setAuthorId(user);
                    mr.setAuthorUserId(String.valueOf(user.getId()));
                }
            }

            // Set reviewer references if available
            if (mr.getReviewers() != null && !mr.getReviewers().isEmpty()) {
                List<User> reviewerUsers = new ArrayList<>();
                List<String> reviewerUserIds = new ArrayList<>();

                for (String reviewerName : mr.getReviewers()) {
                    User reviewerUser = userMap.get(reviewerName);
                    if (reviewerUser != null) {
                        reviewerUsers.add(reviewerUser);
                        reviewerUserIds.add(String.valueOf(reviewerUser.getId()));
                    }
                }

                if (!reviewerUsers.isEmpty()) {
                    mr.setReviewerUsers(reviewerUsers);
                    mr.setReviewerUserIds(reviewerUserIds);
                }
            }
        }
    }

    /**
     * Wrapper method to handle GitLab repository URL context setting.
     * This ensures that GitLab service uses the correct base URL for on-premise instances.
     *
     * @param platformService the platform service to call
     * @param scanRequest the scan request containing repository URL
     * @param serviceCall the service call to execute
     * @param <T> the return type of the service call
     * @return the result of the service call
     * @throws PlatformApiException if the service call fails
     */
    private <T> T callPlatformServiceWithContext(GitPlatformService platformService, ScanRequest scanRequest,
                                               PlatformServiceCall<T> serviceCall) throws PlatformApiException {
        // Check if this is a GitLab service and set repository URL context
        if (platformService instanceof com.publicissapient.knowhow.processor.scm.service.platform.gitlab.GitLabService) {
            com.publicissapient.knowhow.processor.scm.service.platform.gitlab.GitLabService gitLabService =
                (com.publicissapient.knowhow.processor.scm.service.platform.gitlab.GitLabService) platformService;

            try {
                // Set the repository URL context for this thread
                gitLabService.setRepositoryUrlContext(scanRequest.getRepositoryUrl());
                logger.debug("Set GitLab repository URL context: {}", scanRequest.getRepositoryUrl());

                // Execute the service call
                return serviceCall.call();

            } finally {
                // Always clear the context after the call
                gitLabService.clearRepositoryUrlContext();
                logger.debug("Cleared GitLab repository URL context");
            }
        } else {
            // For non-GitLab services, just execute the call directly
            return serviceCall.call();
        }
    }

    /**
     * Functional interface for platform service calls that can throw PlatformApiException.
     */
    @FunctionalInterface
    private interface PlatformServiceCall<T> {
        T call() throws PlatformApiException;
    }
}
