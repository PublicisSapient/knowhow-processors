package com.publicissapient.knowhow.processor.scm.service.platform.azuredevops;

import com.publicissapient.knowhow.processor.scm.client.azuredevops.AzureDevOpsClient;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformService;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.azd.git.types.GitCommit;
import org.azd.git.types.GitCommitRef;
import org.azd.git.types.GitPullRequest;
import org.azd.git.types.GitUserDate;
import org.azd.enums.PullRequestStatus;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Azure DevOps implementation of GitPlatformService.
 * 
 * This service focuses on business logic and data transformation,
 * delegating all Azure DevOps API interactions to AzureDevOpsClient.
 */
@Service
public class AzureDevOpsService implements GitPlatformService {

    private static final Logger log = LoggerFactory.getLogger(AzureDevOpsService.class);
    private static final String PLATFORM_NAME = "Azure DevOps";

    private final AzureDevOpsClient azureDevOpsClient;
    private final RateLimitService rateLimitService;

    @Value("${git.platforms.azure-devops.api-url:https://dev.azure.com}")
    private String azureDevOpsApiUrl;

    @Autowired
    public AzureDevOpsService(AzureDevOpsClient azureDevOpsClient, RateLimitService rateLimitService) {
        this.azureDevOpsClient = azureDevOpsClient;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public List<ScmCommits> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
                                        String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            log.info("Fetching commits for Azure DevOps repository: {}/{}/{}", 
                    gitUrlInfo.getOrganization(), gitUrlInfo.getProject(), gitUrlInfo.getRepositoryName());
            
            // Check rate limit before making API calls
            rateLimitService.checkRateLimit(PLATFORM_NAME, token, gitUrlInfo.getRepositoryName(), gitUrlInfo.getOriginalUrl());
            
            String organization = gitUrlInfo.getOrganization();
            String project = gitUrlInfo.getProject() != null ? gitUrlInfo.getProject() : gitUrlInfo.getRepositoryName();
            String repository = gitUrlInfo.getRepositoryName();
            
            List<GitCommitRef> azureCommits = azureDevOpsClient.fetchCommits(
                organization, project, repository, branchName, token, since, until);
            
            List<ScmCommits> commitDetails = new ArrayList<>();
            
            for (GitCommitRef azureCommit : azureCommits) {
                try {
                    ScmCommits commitDetail = convertToCommit(azureCommit, toolConfigId, organization, project, repository, branchName);
                    commitDetails.add(commitDetail);
                } catch (Exception e) {
                    log.warn("Failed to convert Azure DevOps commit {}: {}", azureCommit.getCommitId(), e.getMessage());
                }
            }
            
            log.info("Successfully converted {} Azure DevOps commits to domain objects", commitDetails.size());
            return commitDetails;
            
        } catch (Exception e) {
            log.error("Failed to fetch commits from Azure DevOps repository {}/{}/{}: {}", 
                     gitUrlInfo.getOrganization(), gitUrlInfo.getProject(), gitUrlInfo.getRepositoryName(), e.getMessage());
            throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch commits from Azure DevOps", e);
        }
    }

    @Override
    public List<ScmMergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
                                                    String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        try {
            log.info("Fetching pull requests for Azure DevOps repository: {}/{}/{} (branch: {})", 
                    gitUrlInfo.getOrganization(), gitUrlInfo.getProject(), gitUrlInfo.getRepositoryName(), 
                    branchName != null ? branchName : "all");

            // Check rate limit before making API calls
            rateLimitService.checkRateLimit(PLATFORM_NAME, token, gitUrlInfo.getRepositoryName(), azureDevOpsApiUrl);

            String organization = gitUrlInfo.getOrganization();
            String project = gitUrlInfo.getProject() != null ? gitUrlInfo.getProject() : gitUrlInfo.getRepositoryName();
            String repository = gitUrlInfo.getRepositoryName();

            List<GitPullRequest> azurePullRequests = azureDevOpsClient.fetchPullRequests(
                organization, project, repository, token, since, branchName);
            
            List<ScmMergeRequests> mergeRequests = new ArrayList<>();

            // Filter by target branch if specified
            if (branchName != null && !branchName.trim().isEmpty()) {
                azurePullRequests = azurePullRequests.stream()
                    .filter(pr -> {
                        try {
                            String targetBranch = pr.getTargetRefName();
                            if (targetBranch != null) {
                                // Remove refs/heads/ prefix if present
                                targetBranch = targetBranch.replace("refs/heads/", "");
                                return targetBranch.equals(branchName);
                            }
                            return false;
                        } catch (Exception e) {
                            log.warn("Failed to get target branch for PR #{}: {}", pr.getPullRequestId(), e.getMessage());
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
            }

            for (GitPullRequest azurePr : azurePullRequests) {
                try {
                    ScmMergeRequests mergeRequest = convertToMergeRequest(azurePr, toolConfigId, organization, project, repository, token);
                    mergeRequests.add(mergeRequest);
                } catch (Exception e) {
                    log.warn("Failed to convert Azure DevOps pull request #{}: {}", azurePr.getPullRequestId(), e.getMessage());
                }
            }

            log.info("Successfully converted {} Azure DevOps pull requests to domain objects", mergeRequests.size());
            return mergeRequests;

        } catch (Exception e) {
            log.error("Failed to fetch pull requests from Azure DevOps repository {}/{}/{}: {}", 
                     gitUrlInfo.getOrganization(), gitUrlInfo.getProject(), gitUrlInfo.getRepositoryName(), e.getMessage());
            throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch pull requests from Azure DevOps", e);
        }
    }

    @Override
    public List<ScmMergeRequests> fetchMergeRequestsByState(String toolConfigId, String owner, String repository, 
                                                           String branchName, String state, String token, 
                                                           LocalDateTime since, LocalDateTime until) throws PlatformApiException {
        // For Azure DevOps, we'll use the GitUrlInfo approach
        GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(
            null, owner, repository, owner, repository, null
        );
        
        List<ScmMergeRequests> allMergeRequests = fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);
        
        // Filter by state if specified
        if (state != null && !state.trim().isEmpty() && !"all".equalsIgnoreCase(state)) {
            return allMergeRequests.stream()
                .filter(mr -> {
                    String mrState = mr.getState();
                    return mrState != null && mrState.equalsIgnoreCase(state);
                })
                .collect(Collectors.toList());
        }
        
        return allMergeRequests;
    }

    @Override
    public List<ScmMergeRequests> fetchLatestMergeRequests(String toolConfigId, String owner, String repository, 
                                                          String branchName, String token, int limit) throws PlatformApiException {
        GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(
            null, owner, repository, owner, repository, null
        );
        
        List<ScmMergeRequests> allMergeRequests = fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, null, null);
        
        // Sort by creation date (most recent first) and limit
        return allMergeRequests.stream()
            .sorted((mr1, mr2) -> {
                if (mr1.getCreatedDate() == null && mr2.getCreatedDate() == null) return 0;
                if (mr1.getCreatedDate() == null) return 1;
                if (mr2.getCreatedDate() == null) return -1;
                return mr2.getCreatedDate().compareTo(mr1.getCreatedDate());
            })
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public boolean testConnection(String token) {
        try {
            // We need an organization to test the connection
            // For now, we'll try to create a connection without making API calls
            if (token == null || token.trim().isEmpty()) {
                return false;
            }
            
            // Basic token validation - Azure DevOps PATs are typically base64 encoded
            return token.length() > 10; // Basic length check
            
        } catch (Exception e) {
            log.warn("Azure DevOps connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getPlatformName() {
        return PLATFORM_NAME;
    }

    @Override
    public String getApiBaseUrl() {
        return azureDevOpsApiUrl;
    }

    /**
     * Converts Azure DevOps GitCommit to ScmCommits domain object.
     */
    private ScmCommits convertToCommit(GitCommitRef azureCommit, String toolConfigId, String organization, String project, String repository, String branchName) {
        ScmCommits commit = new ScmCommits();
        
        // Basic commit information
        commit.setId(new ObjectId());
        commit.setProcessorItemId(new ObjectId(toolConfigId));
        commit.setSha(azureCommit.getCommitId());
        commit.setCommitLog(azureCommit.getComment());
        commit.setRepositoryName(organization + "/" + project + "/" + repository);

        // Author information
        if (azureCommit.getAuthor() != null) {
            User user = User.builder()
                    .displayName(azureCommit.getAuthor().getName())
                    .email(azureCommit.getAuthor().getEmail())
                    .username(azureCommit.getAuthor().getName())
                    .build();
            commit.setCommitAuthor(user);
            commit.setAuthor(azureCommit.getAuthor().getName());
            commit.setAuthorEmail(azureCommit.getAuthor().getEmail());
            commit.setAuthorName(azureCommit.getAuthor().getName());
            if (azureCommit.getAuthor().getDate() != null) {
                commit.setCommitTimestamp(Instant.parse(azureCommit.getAuthor().getDate()).toEpochMilli());
            }
        }
        
        // Committer information
        if (azureCommit.getAuthor() != null) {
            commit.setCommitAuthorId(azureCommit.getAuthor().getName());
            commit.setCommitterEmail(azureCommit.getAuthor().getEmail());
        }
        
        // URL
        if (azureCommit.getUrl() != null) {
            commit.setUrl(azureCommit.getUrl());
        }
        
        // Set default values for required fields
        // commit.setType("commit"); // Removing setType as it doesn't exist in the model
        commit.setBranch(branchName); // Default branch, could be enhanced to get actual branch
        
        return commit;
    }

    /**
     * Converts Azure DevOps GitPullRequest to ScmMergeRequests domain object.
     */
    private ScmMergeRequests convertToMergeRequest(GitPullRequest azurePr, String toolConfigId, String organization, String project, String repository, String token) {
        ScmMergeRequests mergeRequest = new ScmMergeRequests();
        
        // Basic merge request information
        mergeRequest.setId(new ObjectId());
        mergeRequest.setProcessorItemId(new ObjectId(toolConfigId));
        mergeRequest.setExternalId(String.valueOf(azurePr.getPullRequestId()));
        mergeRequest.setTitle(azurePr.getTitle());
        mergeRequest.setSummary(azurePr.getDescription());
        mergeRequest.setRepositoryName(organization + "/" + project + "/" + repository);
        
        // State mapping
        if (azurePr.getStatus() != null) {
            mergeRequest.setState(mapPullRequestStatus(azurePr.getStatus()));
        }

        if(azurePr.getUrl() != null) {
            mergeRequest.setMergeRequestUrl(azurePr.getUrl());
            String commitUrl = azurePr.getUrl() + "/commits";
        }
        
        // Author information
        if (azurePr.getCreatedBy() != null) {
            User.UserBuilder userBuilder = User.builder()
                    .displayName(azurePr.getCreatedBy().getDisplayName())
                    .username(azurePr.getCreatedBy().getUniqueName())
                    .email(azurePr.getCreatedBy().getUniqueName());
            mergeRequest.setAuthorId(userBuilder.build());
            mergeRequest.setAuthorUserId(azurePr.getCreatedBy().getUniqueName());
        }
        
        // Dates
        if (azurePr.getCreationDate() != null) {
            String creationDateStr = azurePr.getCreationDate();
            Instant creationInstant = Instant.parse(creationDateStr);
            mergeRequest.setCreatedDate(creationInstant.toEpochMilli());
            mergeRequest.setUpdatedDate(creationInstant.toEpochMilli());
        }

        long pickedUpOnReviewOn = azureDevOpsClient.getPullRequestPickupTime(organization, project, repository, token, azurePr);
        mergeRequest.setPickedForReviewOn(pickedUpOnReviewOn);

        if(pickedUpOnReviewOn>0L)
            mergeRequest.setUpdatedDate(pickedUpOnReviewOn);

        if (azurePr.getClosedDate() != null) {
            String closedDateStr = azurePr.getClosedDate();
            Instant closedInstant = Instant.parse(closedDateStr);
            mergeRequest.setClosedDate(closedInstant.toEpochMilli());
            mergeRequest.setUpdatedDate(closedInstant.toEpochMilli());
            if(azurePr.getStatus().name().equalsIgnoreCase(PullRequestStatus.COMPLETED.name())) {
                mergeRequest.setMergedAt(LocalDateTime.ofInstant(closedInstant, ZoneId.systemDefault()));
            }
            mergeRequest.setClosed(true);
        } else {
            mergeRequest.setOpen(true);
        }

        // Branch information
        if (azurePr.getSourceRefName() != null) {
            mergeRequest.setFromBranch(azurePr.getSourceRefName().replace("refs/heads/", ""));
        }
        if (azurePr.getTargetRefName() != null) {
            mergeRequest.setToBranch(azurePr.getTargetRefName().replace("refs/heads/", ""));
        }
        
        // URL
        if (azurePr.getUrl() != null) {
            mergeRequest.setMergeRequestUrl(azurePr.getUrl());
        }
        
        // Set default values - removing setType as it doesn't exist in the model
        // mergeRequest.setType("pullrequest");
        
        return mergeRequest;
    }

    /**
     * Maps Azure DevOps PullRequestStatus to string representation.
     */
    private String mapPullRequestStatus(PullRequestStatus status) {
        if (status == null) {
            return "unknown";
        }
        
        switch (status) {
            case ACTIVE:
                return "open";
            case COMPLETED:
                return "merged";
            case ABANDONED:
                return "closed";
            default:
                return status.toString().toLowerCase();
        }
    }
}