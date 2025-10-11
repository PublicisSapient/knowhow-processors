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

package com.publicissapient.knowhow.processor.scm.service.platform.azuredevops;

import com.publicissapient.knowhow.processor.scm.client.azuredevops.AzureDevOpsClient;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformService;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Azure DevOps implementation of GitPlatformService.
 *
 * This service focuses on business logic and data transformation, delegating
 * all Azure DevOps API interactions to AzureDevOpsClient.
 */
@Service
@Slf4j
public class AzureDevOpsService implements GitPlatformService {

	private static final String PLATFORM_NAME = "Azure DevOps";
	private static final String REFS_HEADS_PREFIX = "refs/heads/";

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
			log.info("Fetching commits for Azure DevOps repository: {}/{}/{}", gitUrlInfo.getOrganization(),
					gitUrlInfo.getProject(), gitUrlInfo.getRepositoryName());

			// Check rate limit before making API calls
			rateLimitService.checkRateLimit(PLATFORM_NAME, token, gitUrlInfo.getRepositoryName(),
					gitUrlInfo.getOriginalUrl());

			// CHANGE: Extract repository details to reduce complexity
			RepositoryDetails repoDetails = extractRepositoryDetails(gitUrlInfo);

			List<GitCommitRef> azureCommits = azureDevOpsClient.fetchCommits(repoDetails.organization(),
					repoDetails.project(), repoDetails.repository(), branchName, token, since, until);

			// CHANGE: Extract commit conversion to separate method
			List<ScmCommits> commitDetails = convertAzureCommitsToScmCommits(azureCommits, toolConfigId, repoDetails,
					branchName);

			log.info("Successfully converted {} Azure DevOps commits to domain objects", commitDetails.size());
			return commitDetails;

		} catch (Exception e) {
			log.error("Failed to fetch commits from Azure DevOps repository {}/{}/{}: {}", gitUrlInfo.getOrganization(),
					gitUrlInfo.getProject(), gitUrlInfo.getRepositoryName(), e.getMessage());
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch commits from Azure DevOps", e);
		}
	}

	@Override
	public List<ScmMergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo,
			String branchName, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
		try {
			log.info("Fetching pull requests for Azure DevOps repository: {}/{}/{} (branch: {})",
					gitUrlInfo.getOrganization(), gitUrlInfo.getProject(), gitUrlInfo.getRepositoryName(),
					branchName != null ? branchName : "all");

			// Check rate limit before making API calls
			rateLimitService.checkRateLimit(PLATFORM_NAME, token, gitUrlInfo.getRepositoryName(), azureDevOpsApiUrl);

			// CHANGE: Extract repository details to reduce complexity
			RepositoryDetails repoDetails = extractRepositoryDetails(gitUrlInfo);

			List<GitPullRequest> azurePullRequests = azureDevOpsClient.fetchPullRequests(repoDetails.organization(),
					repoDetails.project(), repoDetails.repository(), token, since, branchName);

			// CHANGE: Extract filtering logic to separate method
			List<GitPullRequest> filteredPullRequests = filterPullRequestsByBranch(azurePullRequests, branchName);

			// CHANGE: Extract PR conversion to separate method
			List<ScmMergeRequests> mergeRequests = convertAzurePRsToScmMergeRequests(filteredPullRequests, toolConfigId,
					repoDetails, token);

			log.info("Successfully converted {} Azure DevOps pull requests to domain objects", mergeRequests.size());
			return mergeRequests;

		} catch (Exception e) {
			log.error("Failed to fetch pull requests from Azure DevOps repository {}/{}/{}: {}",
					gitUrlInfo.getOrganization(), gitUrlInfo.getProject(), gitUrlInfo.getRepositoryName(),
					e.getMessage());
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch pull requests from Azure DevOps", e);
		}
	}

	@Override
	public String getPlatformName() {
		return PLATFORM_NAME;
	}

	// CHANGE: New method to extract repository details
	private RepositoryDetails extractRepositoryDetails(GitUrlParser.GitUrlInfo gitUrlInfo) {
		String organization = gitUrlInfo.getOrganization();
		String project = gitUrlInfo.getProject() != null ? gitUrlInfo.getProject() : gitUrlInfo.getRepositoryName();
		String repository = gitUrlInfo.getRepositoryName();
		return new RepositoryDetails(organization, project, repository);
	}

	// CHANGE: New method to convert commits list
	private List<ScmCommits> convertAzureCommitsToScmCommits(List<GitCommitRef> azureCommits, String toolConfigId,
			RepositoryDetails repoDetails, String branchName) {
		List<ScmCommits> commitDetails = new ArrayList<>();

		for (GitCommitRef azureCommit : azureCommits) {
			try {
				ScmCommits commitDetail = convertToCommit(azureCommit, toolConfigId, repoDetails.organization(),
						repoDetails.project(), repoDetails.repository(), branchName);
				commitDetails.add(commitDetail);
			} catch (Exception e) {
				log.warn("Failed to convert Azure DevOps commit {}: {}", azureCommit.getCommitId(), e.getMessage());
			}
		}

		return commitDetails;
	}

	// CHANGE: New method to filter pull requests by branch
	private List<GitPullRequest> filterPullRequestsByBranch(List<GitPullRequest> pullRequests, String branchName) {
		if (branchName == null || branchName.trim().isEmpty()) {
			return pullRequests;
		}

		return pullRequests.stream().filter(pr -> matchesTargetBranch(pr, branchName)).toList();
	}

	// CHANGE: New method to check if PR matches target branch
	private boolean matchesTargetBranch(GitPullRequest pr, String branchName) {
		try {
			String targetBranch = pr.getTargetRefName();
			if (targetBranch != null) {
				// CHANGE: Use utility method for branch normalization
				targetBranch = normalizeBranchName(targetBranch);
				return targetBranch.equals(branchName);
			}
			return false;
		} catch (Exception e) {
			log.warn("Failed to get target branch for PR #{}: {}", pr.getPullRequestId(), e.getMessage());
			return false;
		}
	}

	// CHANGE: New method to convert PRs list
	private List<ScmMergeRequests> convertAzurePRsToScmMergeRequests(List<GitPullRequest> pullRequests,
			String toolConfigId, RepositoryDetails repoDetails, String token) {
		List<ScmMergeRequests> mergeRequests = new ArrayList<>();

		for (GitPullRequest azurePr : pullRequests) {
			try {
				ScmMergeRequests mergeRequest = convertToMergeRequest(azurePr, toolConfigId, repoDetails.organization(),
						repoDetails.project(), repoDetails.repository(), token);
				mergeRequests.add(mergeRequest);
			} catch (Exception e) {
				log.warn("Failed to convert Azure DevOps pull request #{}: {}", azurePr.getPullRequestId(),
						e.getMessage());
			}
		}

		return mergeRequests;
	}

	private String normalizeBranchName(String branchName) {
		if (branchName == null) {
			return null;
		}
		return branchName.replace(REFS_HEADS_PREFIX, "");
	}

	/**
	 * Converts Azure DevOps GitCommit to ScmCommits domain object.
	 */
	private ScmCommits convertToCommit(GitCommitRef azureCommit, String toolConfigId, String organization,
			String project, String repository, String branchName) {
		ScmCommits commit = new ScmCommits();

		// Basic commit information
		commit.setId(new ObjectId());
		commit.setProcessorItemId(new ObjectId(toolConfigId));
		commit.setSha(azureCommit.getCommitId());
		commit.setCommitLog(azureCommit.getComment());
		commit.setRepositoryName(organization + "/" + project + "/" + repository);

		// CHANGE: Extract author information setting to reduce complexity
		setCommitAuthorInfo(commit, azureCommit);

		// URL
		if (azureCommit.getUrl() != null) {
			commit.setUrl(azureCommit.getUrl());
		}

		// Set branch
		commit.setBranch(branchName);

		return commit;
	}

	// CHANGE: New method to set commit author information
	private void setCommitAuthorInfo(ScmCommits commit, GitCommitRef azureCommit) {
		if (azureCommit.getAuthor() == null) {
			return;
		}

		GitUserDate author = azureCommit.getAuthor();

		// Create user object
		User user = User.builder().displayName(author.getName()).email(author.getEmail()).username(author.getName())
				.build();

		commit.setCommitAuthor(user);
		commit.setAuthor(author.getName());
		commit.setAuthorEmail(author.getEmail());
		commit.setAuthorName(author.getName());
		commit.setCommitAuthorId(author.getName());
		commit.setCommitterEmail(author.getEmail());

		// Set timestamp
		if (author.getDate() != null) {
			parseAndSetTimestamp(author.getDate()).ifPresent(commit::setCommitTimestamp);
		}
	}

	/**
	 * Converts Azure DevOps GitPullRequest to ScmMergeRequests domain object.
	 */
	private ScmMergeRequests convertToMergeRequest(GitPullRequest azurePr, String toolConfigId, String organization,
			String project, String repository, String token) {
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

		if (azurePr.getUrl() != null) {
			mergeRequest.setMergeRequestUrl(azurePr.getUrl());
		}

		// CHANGE: Extract author information setting
		setMergeRequestAuthor(mergeRequest, azurePr);

		// CHANGE: Extract date handling
		setMergeRequestDates(mergeRequest, azurePr, organization, project, repository, token);

		// CHANGE: Extract branch information setting
		setMergeRequestBranches(mergeRequest, azurePr);

		return mergeRequest;
	}

	// CHANGE: New method to set merge request author
	private void setMergeRequestAuthor(ScmMergeRequests mergeRequest, GitPullRequest azurePr) {
		if (azurePr.getCreatedBy() == null) {
			return;
		}

		User.UserBuilder userBuilder = User.builder().displayName(azurePr.getCreatedBy().getDisplayName())
				.username(azurePr.getCreatedBy().getUniqueName()).email(azurePr.getCreatedBy().getUniqueName());

		mergeRequest.setAuthorId(userBuilder.build());
		mergeRequest.setAuthorUserId(azurePr.getCreatedBy().getUniqueName());
	}

	// CHANGE: New method to handle merge request dates
	private void setMergeRequestDates(ScmMergeRequests mergeRequest, GitPullRequest azurePr, String organization,
			String project, String repository, String token) {

		// Creation date
		if (azurePr.getCreationDate() != null) {
			parseAndSetTimestamp(azurePr.getCreationDate()).ifPresent(timestamp -> {
				mergeRequest.setCreatedDate(timestamp);
				mergeRequest.setUpdatedDate(timestamp);
			});
		}

		// Pickup time
		long pickedUpOnReviewOn = azureDevOpsClient.getPullRequestPickupTime(organization, project, repository, token,
				azurePr);
		mergeRequest.setPickedForReviewOn(pickedUpOnReviewOn);

		if (pickedUpOnReviewOn > 0L) {
			mergeRequest.setUpdatedDate(pickedUpOnReviewOn);
		}

		// Closed date
		if (azurePr.getClosedDate() != null) {
			handleClosedPullRequest(mergeRequest, azurePr);
		} else {
			mergeRequest.setOpen(true);
		}
	}

	// CHANGE: New method to handle closed pull request dates
	private void handleClosedPullRequest(ScmMergeRequests mergeRequest, GitPullRequest azurePr) {
		parseAndSetTimestamp(azurePr.getClosedDate()).ifPresent(closedTimestamp -> {
			mergeRequest.setClosedDate(closedTimestamp);
			mergeRequest.setUpdatedDate(closedTimestamp);

			if (azurePr.getStatus() != null
					&& azurePr.getStatus().name().equalsIgnoreCase(PullRequestStatus.COMPLETED.name())) {
				LocalDateTime mergedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(closedTimestamp),
						ZoneId.systemDefault());
				mergeRequest.setMergedAt(mergedAt);
			}

			mergeRequest.setClosed(true);
		});
	}

	// CHANGE: New method to set merge request branches
	private void setMergeRequestBranches(ScmMergeRequests mergeRequest, GitPullRequest azurePr) {
		if (azurePr.getSourceRefName() != null) {
			mergeRequest.setFromBranch(normalizeBranchName(azurePr.getSourceRefName()));
		}

		if (azurePr.getTargetRefName() != null) {
			mergeRequest.setToBranch(normalizeBranchName(azurePr.getTargetRefName()));
		}
	}

	// CHANGE: New utility method for date parsing
	private Optional<Long> parseAndSetTimestamp(String dateString) {
		try {
			if (dateString != null) {
				Instant instant = Instant.parse(dateString);
				return Optional.of(instant.toEpochMilli());
			}
		} catch (Exception e) {
			log.warn("Failed to parse date: {}", dateString, e);
		}
		return Optional.empty();
	}

	/**
	 * Maps Azure DevOps PullRequestStatus to string representation.
	 */
	private String mapPullRequestStatus(PullRequestStatus status) {
		if (status == null) {
			return "unknown";
		}

		// CHANGE: Use switch expression for cleaner code
		return switch (status) {
		case ACTIVE -> "open";
		case COMPLETED -> "merged";
		case ABANDONED -> "closed";
		default -> status.toString().toLowerCase();
		};
	}

	// CHANGE: New record to hold repository details (use inner class for Java < 14)
	private record RepositoryDetails(String organization, String project, String repository) {
	}
}
