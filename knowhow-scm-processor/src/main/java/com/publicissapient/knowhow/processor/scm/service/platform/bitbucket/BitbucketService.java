package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.client.wrapper.BitbucketParser;
import com.publicissapient.kpidashboard.common.model.scm.CommitDetails;
import com.publicissapient.kpidashboard.common.model.scm.MergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformService;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bitbucket implementation of GitPlatformService.
 * Supports both Bitbucket Cloud (bitbucket.org) and Bitbucket Server (on-premise).
 */
@Service("bitbucketService")
public class BitbucketService implements GitPlatformService {

    private static final Logger logger = LoggerFactory.getLogger(BitbucketService.class);

    private final BitbucketClient bitbucketClient;
    private final RateLimitService rateLimitService;
    private final GitUrlParser gitUrlParser;

    @Value("${git-scanner.platforms.bitbucket.api-url:https://api.bitbucket.org/2.0}")
    private String defaultBitbucketApiUrl;

    // ThreadLocal to store repository URL for the current request
    private final ThreadLocal<String> currentRepositoryUrl = new ThreadLocal<>();

    public BitbucketService(BitbucketClient bitbucketClient, RateLimitService rateLimitService, GitUrlParser gitUrlParser) {
        this.bitbucketClient = bitbucketClient;
        this.rateLimitService = rateLimitService;
        this.gitUrlParser = gitUrlParser;
    }

    /**
     * Sets the repository URL context for the current thread.
     */
    public void setRepositoryUrlContext(String repositoryUrl) {
        currentRepositoryUrl.set(repositoryUrl);
    }

    /**
     * Clears the repository URL context for the current thread.
     */
    public void clearRepositoryUrlContext() {
        currentRepositoryUrl.remove();
    }

    @Override
    public List<CommitDetails> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
                                            String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            logger.info("Fetching commits from Bitbucket repository: {}/{}", gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName());
            // Extract username and app password from token (format: username:appPassword)
            String[] credentials = extractCredentials(token);
            String username = credentials[0];
            String appPassword = credentials[1];

            String repositoryUrl = currentRepositoryUrl.get();
            if (repositoryUrl == null) {
                repositoryUrl = gitUrlInfo.getOriginalUrl();
            }

            List<BitbucketClient.BitbucketCommit> bitbucketCommits = bitbucketClient.fetchCommits(
                    gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName(), branchName, username, appPassword,
                    since, until, repositoryUrl);

            List<CommitDetails> commitDetails = new ArrayList<>();
            for (BitbucketClient.BitbucketCommit bitbucketCommit : bitbucketCommits) {
                CommitDetails commitDetail = convertToCommit(bitbucketCommit, toolConfigId, gitUrlInfo.getOwner(),
                        gitUrlInfo.getRepositoryName(), username, appPassword, repositoryUrl);
                commitDetails.add(commitDetail);
            }

            logger.info("Successfully converted {} Bitbucket commits to domain objects", commitDetails.size());
            return commitDetails;

        } catch (PlatformApiException e) {
            logger.error("PlatformApiException fetching commits from Bitbucket: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Error fetching commits from Bitbucket: {}", e.getMessage(), e);
            throw new PlatformApiException("Bitbucket", "Failed to fetch commits from Bitbucket: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
                                                  String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            logger.info("Fetching pull requests from Bitbucket repository: {}/{}", gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName());

            // Extract username and app password from token (format: username:appPassword)
            String[] credentials = extractCredentials(token);
            String username = credentials[0];
            String appPassword = credentials[1];

            String repositoryUrl = currentRepositoryUrl.get();
            if (repositoryUrl == null) {
                repositoryUrl = gitUrlInfo.getOriginalUrl();
            }

            List<BitbucketClient.BitbucketPullRequest> bitbucketPullRequests = bitbucketClient.fetchPullRequests(
                    gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName(), branchName, username, appPassword,
                    since, until, repositoryUrl);

            List<MergeRequests> mergeRequests = new ArrayList<>();
            for (BitbucketClient.BitbucketPullRequest bitbucketPr : bitbucketPullRequests) {
                MergeRequests mergeRequest = convertToMergeRequest(bitbucketPr, toolConfigId, gitUrlInfo.getOwner(),
                        gitUrlInfo.getRepositoryName(), username, appPassword, repositoryUrl);
                mergeRequests.add(mergeRequest);
            }

            logger.info("Successfully converted {} Bitbucket pull requests to domain objects", mergeRequests.size());
            return mergeRequests;

        } catch (Exception e) {
            logger.error("Error fetching pull requests from Bitbucket: {}", e.getMessage(), e);
            throw new PlatformApiException("Bitbucket", "Failed to fetch pull requests from Bitbucket: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MergeRequests> fetchMergeRequestsByState(String toolConfigId, String owner, String repository, String branchName,
                                                         String state, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            logger.info("Fetching pull requests by state '{}' from Bitbucket repository: {}/{}", state, owner, repository);

            // Extract username and app password from token (format: username:appPassword)
            String[] credentials = extractCredentials(token);
            String username = credentials[0];
            String appPassword = credentials[1];

            String repositoryUrl = currentRepositoryUrl.get();

            List<BitbucketClient.BitbucketPullRequest> bitbucketPullRequests = bitbucketClient.fetchPullRequestsByState(
                    owner, repository, branchName, state, username, appPassword, since, until, repositoryUrl);

            List<MergeRequests> mergeRequests = new ArrayList<>();
            for (BitbucketClient.BitbucketPullRequest bitbucketPr : bitbucketPullRequests) {
                MergeRequests mergeRequest = convertToMergeRequest(bitbucketPr, toolConfigId, owner, repository,
                        username, appPassword, repositoryUrl);
                mergeRequests.add(mergeRequest);
            }

            logger.info("Successfully converted {} Bitbucket pull requests by state to domain objects", mergeRequests.size());
            return mergeRequests;

        } catch (Exception e) {
            logger.error("Error fetching pull requests by state from Bitbucket: {}", e.getMessage(), e);
            throw new PlatformApiException("Bitbucket", "Failed to fetch pull requests by state from Bitbucket: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MergeRequests> fetchLatestMergeRequests(String toolConfigId, String owner, String repository, String branchName,
                                                        String token, int limit) throws PlatformApiException {
        try {
            logger.info("Fetching latest {} pull requests from Bitbucket repository: {}/{}", limit, owner, repository);

            // Extract username and app password from token (format: username:appPassword)
            String[] credentials = extractCredentials(token);
            String username = credentials[0];
            String appPassword = credentials[1];

            String repositoryUrl = currentRepositoryUrl.get();

            List<BitbucketClient.BitbucketPullRequest> bitbucketPullRequests = bitbucketClient.fetchLatestPullRequests(
                    owner, repository, branchName, username, appPassword, limit, repositoryUrl);

            List<MergeRequests> mergeRequests = new ArrayList<>();
            for (BitbucketClient.BitbucketPullRequest bitbucketPr : bitbucketPullRequests) {
                MergeRequests mergeRequest = convertToMergeRequest(bitbucketPr, toolConfigId, owner, repository,
                        username, appPassword, repositoryUrl);
                mergeRequests.add(mergeRequest);
            }

            logger.info("Successfully converted {} latest Bitbucket pull requests to domain objects", mergeRequests.size());
            return mergeRequests;

        } catch (Exception e) {
            logger.error("Error fetching latest pull requests from Bitbucket: {}", e.getMessage(), e);
            throw new PlatformApiException("Bitbucket", "Failed to fetch latest pull requests from Bitbucket: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean testConnection(String token) {
        try {
            // Extract username and app password from token (format: username:appPassword)
            String[] credentials = extractCredentials(token);
            String username = credentials[0];
            String appPassword = credentials[1];

            return bitbucketClient.testConnection(username, appPassword);
        } catch (Exception e) {
            logger.error("Failed to test Bitbucket connection: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getPlatformName() {
        return "Bitbucket";
    }

    @Override
    public String getApiBaseUrl() {
        return defaultBitbucketApiUrl;
    }

    /**
     * Converts a Bitbucket commit to domain Commit object.
     */
    private CommitDetails convertToCommit(BitbucketClient.BitbucketCommit bitbucketCommit, String toolConfigId, String owner,
                                          String repository, String username, String appPassword, String repositoryUrl) {
        try {
            CommitDetails.CommitBuilder commitBuilder = CommitDetails.builder()
                    .sha(bitbucketCommit.getHash())
                    .commitMessage(bitbucketCommit.getMessage())
                    .toolConfigId(new ObjectId(toolConfigId))
                    .repositoryName(repository);

            // Set commit date
            if (bitbucketCommit.getDate() != null) {
                try {
                    LocalDateTime commitDate = LocalDateTime.parse(bitbucketCommit.getDate(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    commitBuilder.commitTimestamp(commitDate.toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
                } catch (Exception e) {
                    logger.warn("Failed to parse commit date: {}", bitbucketCommit.getDate());
                }
            }

            // Set author information
            if (bitbucketCommit.getAuthor() != null) {
                User author = extractUser(bitbucketCommit.getAuthor(), repository);
                commitBuilder.commitAuthor(author);
                commitBuilder.commitAuthorId(String.valueOf(author.getId()));
                commitBuilder.authorName(author.getUsername());
            }

            // Set commit statistics
            if (bitbucketCommit.getStats() != null) {
                BitbucketClient.BitbucketCommitStats stats = bitbucketCommit.getStats();
                commitBuilder.addedLines(stats.getAdditions() != null ? stats.getAdditions() : 0);
                commitBuilder.removedLines(stats.getDeletions() != null ? stats.getDeletions() : 0);
                commitBuilder.changedLines(stats.getTotal() != null ? stats.getTotal() : 0);
            }

            // Try to fetch diff information for file changes
            try {
                String diffContent = bitbucketClient.fetchCommitDiffs(owner, repository, bitbucketCommit.getHash(),
                        username, appPassword, repositoryUrl);
                BitbucketParser bitbucketParser = bitbucketClient.getBitbucketParser(repositoryUrl);
                List<CommitDetails.FileChange> fileChanges = bitbucketParser.parseDiffToFileChanges(diffContent);
                commitBuilder.fileChanges(fileChanges);
            } catch (Exception e) {
                logger.warn("Failed to fetch diff for commit {}: {}", bitbucketCommit.getHash(), e);
                commitBuilder.fileChanges(new ArrayList<>());
            }

            return commitBuilder.build();

        } catch (Exception e) {
            logger.error("Error converting Bitbucket commit to domain object: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert Bitbucket commit", e);
        }
    }

    /**
     * Converts a Bitbucket pull request to domain MergeRequest object.
     */
    private MergeRequests convertToMergeRequest(BitbucketClient.BitbucketPullRequest bitbucketPr, String toolConfigId,
                                                String owner, String repository, String username, String appPassword, String repositoryUrl) {
        try {
            MergeRequests.MergeRequestsBuilder mrBuilder = MergeRequests.builder()
                    .externalId(bitbucketPr.getId().toString())
                    .title(bitbucketPr.getTitle())
                    .summary(bitbucketPr.getDescription())
                    .state(convertPullRequestState(bitbucketPr.getState()).name())
                    .processorItemId(new ObjectId(toolConfigId))
                    .repositoryName(repository);

            // Set dates
            if (bitbucketPr.getCreatedOn() != null) {
                try {
                    LocalDateTime createdDate = LocalDateTime.parse(bitbucketPr.getCreatedOn(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    mrBuilder.createdDate(createdDate.toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
                } catch (Exception e) {
                    logger.warn("Failed to parse created date: {}", bitbucketPr.getCreatedOn());
                }
            }

            if (bitbucketPr.getUpdatedOn() != null) {
                try {
                    LocalDateTime updatedDate = LocalDateTime.parse(bitbucketPr.getUpdatedOn(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    mrBuilder.updatedDate(updatedDate.toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
                } catch (Exception e) {
                    logger.warn("Failed to parse updated date: {}", bitbucketPr.getUpdatedOn());
                }
            }

            // Set author
            if (bitbucketPr.getAuthor() != null) {
                User author = extractUser(bitbucketPr.getAuthor(), repository);
                mrBuilder.authorUserId(String.valueOf(author.getId()));
                mrBuilder.authorId(author);
            }

            // Set reviewers
            if (bitbucketPr.getReviewers() != null && !bitbucketPr.getReviewers().isEmpty()) {
                List<String> reviewerUserIds = bitbucketPr.getReviewers().stream()
                        .map(reviewer -> extractUser(reviewer, repository).getId().toString())
                        .collect(Collectors.toList());
                mrBuilder.reviewerUserIds(reviewerUserIds);
            }

            // Set reviewers
            if (bitbucketPr.getReviewers() != null && !bitbucketPr.getReviewers().isEmpty()) {
                List<String> reviewerUserIds = bitbucketPr.getReviewers().stream()
                        .map(reviewer -> extractUser(reviewer, repository).getId().toString())
                        .collect(Collectors.toList());
                mrBuilder.reviewerUserIds(reviewerUserIds);
            }

            // Set branch information
            if (bitbucketPr.getSource() != null && bitbucketPr.getSource().getBranch() != null) {
                mrBuilder.fromBranch(bitbucketPr.getSource().getBranch().getName());
            }

            if (bitbucketPr.getDestination() != null && bitbucketPr.getDestination().getBranch() != null) {
                mrBuilder.toBranch(bitbucketPr.getDestination().getBranch().getName());
            }

            if(bitbucketPr.getPickedUpOn() != null) {
                try {
                    LocalDateTime pickedUpDate = LocalDateTime.parse(bitbucketPr.getPickedUpOn(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    mrBuilder.pickedForReviewOn(pickedUpDate);
                } catch (Exception e) {
                    logger.warn("Failed to parse picked up date: {}", bitbucketPr.getPickedUpOn());
                }
            }

            // Try to fetch diff statistics
            try {
                String diffContent = bitbucketClient.fetchPullRequestDiffs(owner, repository, bitbucketPr.getId(),
                        username, appPassword, repositoryUrl);
                BitbucketParser bitbucketParser = bitbucketClient.getBitbucketParser(repositoryUrl);
                MergeRequests.PullRequestStats stats = bitbucketParser.parsePRDiffToFileChanges(diffContent);
                mrBuilder.addedLines(stats.getAddedLines());
                mrBuilder.removedLines(stats.getRemovedLines());
                mrBuilder.filesChanged(stats.getChangedFiles());
            } catch (Exception e) {
                logger.warn("Failed to fetch diff for pull request {}: {}", bitbucketPr.getId(), e.getMessage());
                mrBuilder.addedLines(0);
                mrBuilder.removedLines(0);
                mrBuilder.filesChanged(0);
            }

            return mrBuilder.build();

        } catch (Exception e) {
            logger.error("Error converting Bitbucket pull request to domain object: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert Bitbucket pull request", e);
        }
    }

    /**
     * Extracts user information from Bitbucket user object.
     */
    private User extractUser(BitbucketClient.BitbucketUser bitbucketUser, String repositoryName) {
        if (bitbucketUser == null) {
            return null;
        }

        if (bitbucketUser.getUser() == null) {
            return User.builder()
                    .repositoryName(repositoryName)
                    .username(bitbucketUser.getName())
                    .displayName(bitbucketUser.getDisplayName() != null ? bitbucketUser.getDisplayName() : bitbucketUser.getName())
                    .externalId(bitbucketUser.getUuid() != null ? bitbucketUser.getUuid() : null)
                    .active(true)
                    .bot("team".equals(bitbucketUser.getType()))
                    .build();
        }
        return User.builder()
                .repositoryName(repositoryName)
                .username(bitbucketUser.getUser().getUsername() != null ? bitbucketUser.getUser().getUsername() : bitbucketUser.getUser().getNickname())
                .displayName(bitbucketUser.getUser().getDisplayName())
                .externalId(bitbucketUser.getUser().getUuid() != null ? bitbucketUser.getUser().getUuid() : bitbucketUser.getUser().getAccountId())
                .active(true)
                .bot("team".equals(bitbucketUser.getType()))
                .build();
    }

    /**
     * Converts Bitbucket pull request state to domain MergeRequest state.
     */
    private MergeRequests.MergeRequestState convertPullRequestState(String bitbucketState) {
        if (bitbucketState == null) {
            return MergeRequests.MergeRequestState.OPEN;
        }

        switch (bitbucketState.toUpperCase()) {
            case "OPEN":
                return MergeRequests.MergeRequestState.OPEN;
            case "MERGED":
                return MergeRequests.MergeRequestState.MERGED;
            case "DECLINED":
            case "SUPERSEDED":
                return MergeRequests.MergeRequestState.CLOSED;
            default:
                logger.warn("Unknown Bitbucket pull request state: {}", bitbucketState);
                return MergeRequests.MergeRequestState.OPEN;
        }
    }

    /**
     * Extracts credentials from token string.
     */
    private String[] extractCredentials(String token) {
        if (token == null || !token.contains(":")) {
            throw new IllegalArgumentException("Bitbucket token must be in format 'username:appPassword'");
        }

        String[] parts = token.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Bitbucket token must be in format 'username:appPassword'");
        }

        return parts;
    }


}