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

package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.util.wrapper.BitbucketParser;
import com.publicissapient.knowhow.processor.scm.exception.GitScannerException;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bitbucket implementation of GitPlatformService. Supports both Bitbucket Cloud
 * (bitbucket.org) and Bitbucket Server (on-premise).
 */
@Service("bitbucketService")
@Slf4j
public class BitbucketService implements GitPlatformService {

	private final BitbucketClient bitbucketClient;

    private static final String PLATFORM_NAME = "Bitbucket";

	// ThreadLocal to store repository URL for the current request
	private final ThreadLocal<String> currentRepositoryUrl = new ThreadLocal<>();

	public BitbucketService(BitbucketClient bitbucketClient) {
		this.bitbucketClient = bitbucketClient;
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
	public List<ScmCommits> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
			String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
		try {
			log.info("Fetching commits from Bitbucket repository: {}/{}", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName());

			Credentials credentials = parseCredentials(token);
			String repositoryUrl = getRepositoryUrl(gitUrlInfo);

			List<BitbucketClient.BitbucketCommit> bitbucketCommits = bitbucketClient.fetchCommits(gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName().trim(), branchName, credentials.username(),
					credentials.appPassword(), since, repositoryUrl);

			List<ScmCommits> commitDetails = convertBitbucketCommitsToScmCommits(bitbucketCommits, toolConfigId,
					gitUrlInfo, credentials, repositoryUrl);

			log.info("Successfully converted {} Bitbucket commits to domain objects", commitDetails.size());
			return commitDetails;

		} catch (PlatformApiException e) {
			log.error("PlatformApiException fetching commits from Bitbucket: {}", e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			log.error("Error fetching commits from Bitbucket: {}", e.getMessage(), e);
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch commits from Bitbucket: " + e.getMessage(), e);
		}
	}

	@Override
	public List<ScmMergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo,
			String branchName, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
		try {
			log.info("Fetching pull requests from Bitbucket repository: {}/{}", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName());

			Credentials credentials = parseCredentials(token);
			String repositoryUrl = getRepositoryUrl(gitUrlInfo);

			List<BitbucketClient.BitbucketPullRequest> bitbucketPullRequests = bitbucketClient.fetchPullRequests(
					gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName().trim(), branchName, credentials.username(),
					credentials.appPassword(), since, repositoryUrl);

			List<ScmMergeRequests> mergeRequests = convertBitbucketPRsToScmMergeRequests(bitbucketPullRequests,
					toolConfigId, gitUrlInfo, credentials, repositoryUrl);

			log.info("Successfully converted {} Bitbucket pull requests to domain objects", mergeRequests.size());
			return mergeRequests;

		} catch (Exception e) {
			log.error("Error fetching pull requests from Bitbucket: {}", e.getMessage(), e);
			throw new PlatformApiException(PLATFORM_NAME,
					"Failed to fetch pull requests from Bitbucket: " + e.getMessage(), e);
		}
	}

	@Override
	public String getPlatformName() {
		return PLATFORM_NAME;
	}

	private List<ScmCommits> convertBitbucketCommitsToScmCommits(List<BitbucketClient.BitbucketCommit> bitbucketCommits,
			String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, Credentials credentials, String repositoryUrl) {

		List<ScmCommits> commitDetails = new ArrayList<>();
		for (BitbucketClient.BitbucketCommit bitbucketCommit : bitbucketCommits) {
			ScmCommits commitDetail = convertToCommit(bitbucketCommit, toolConfigId, gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), credentials.username(), credentials.appPassword(), repositoryUrl);
			commitDetails.add(commitDetail);
		}
		return commitDetails;
	}

	private List<ScmMergeRequests> convertBitbucketPRsToScmMergeRequests(
			List<BitbucketClient.BitbucketPullRequest> bitbucketPullRequests, String toolConfigId,
			GitUrlParser.GitUrlInfo gitUrlInfo, Credentials credentials, String repositoryUrl) {

		List<ScmMergeRequests> mergeRequests = new ArrayList<>();
		for (BitbucketClient.BitbucketPullRequest bitbucketPr : bitbucketPullRequests) {
			ScmMergeRequests mergeRequest = convertToMergeRequest(bitbucketPr, toolConfigId, gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), credentials.username(), credentials.appPassword(), repositoryUrl);
			mergeRequests.add(mergeRequest);
		}
		return mergeRequests;
	}

	private String getRepositoryUrl(GitUrlParser.GitUrlInfo gitUrlInfo) {
		String repositoryUrl = currentRepositoryUrl.get();
		if (repositoryUrl == null) {
			repositoryUrl = gitUrlInfo.getOriginalUrl();
		}
		return repositoryUrl;
	}

	/**
	 * Converts a Bitbucket commit to domain Commit object.
	 */
	private ScmCommits convertToCommit(BitbucketClient.BitbucketCommit bitbucketCommit, String toolConfigId,
			String owner, String repository, String username, String appPassword, String repositoryUrl) {
		try {
			ScmCommits.ScmCommitsBuilder commitBuilder = ScmCommits.builder().sha(bitbucketCommit.getHash())
					.commitMessage(bitbucketCommit.getMessage()).processorItemId(new ObjectId(toolConfigId))
					.repoSlug(repository);

			setCommitDate(commitBuilder, bitbucketCommit.getDate());

			setCommitAuthor(commitBuilder, bitbucketCommit.getAuthor(), repository);

			if (bitbucketCommit.getParents() != null && bitbucketCommit.getParents().size() > 1) {
				commitBuilder.isMergeCommit(true);
			}

			setCommitStats(commitBuilder, bitbucketCommit.getStats());

			fetchAndSetCommitDiff(commitBuilder, owner, repository, bitbucketCommit, username, appPassword,
					repositoryUrl);

			return commitBuilder.build();

		} catch (Exception e) {
			log.error("Error converting Bitbucket commit to domain object: {}", e.getMessage(), e);
			throw new GitScannerException("Failed to convert Bitbucket commit", e);
		}
	}

	private void setCommitDate(ScmCommits.ScmCommitsBuilder commitBuilder, String dateString) {
		if (dateString != null) {
			parseAndSetDate(dateString, "commit date").ifPresent(
					date -> commitBuilder.commitTimestamp(date.toInstant(java.time.ZoneOffset.UTC).toEpochMilli()));
		}
	}

	private void setCommitAuthor(ScmCommits.ScmCommitsBuilder commitBuilder, BitbucketClient.BitbucketUser authorData,
			String repository) {
		if (authorData != null) {
			User author = extractUser(authorData, repository);
			if (author != null) {
				commitBuilder.commitAuthor(author);
				commitBuilder.authorName(author.getUsername());
			}
		}
	}

	private void setCommitStats(ScmCommits.ScmCommitsBuilder commitBuilder,
			BitbucketClient.BitbucketCommitStats stats) {
		if (stats != null) {
			commitBuilder.addedLines(stats.getAdditions() != null ? stats.getAdditions() : 0);
			commitBuilder.removedLines(stats.getDeletions() != null ? stats.getDeletions() : 0);
			commitBuilder.changedLines(stats.getTotal() != null ? stats.getTotal() : 0);
		}
	}

	private void fetchAndSetCommitDiff(ScmCommits.ScmCommitsBuilder commitBuilder, String owner, String repository,
			BitbucketClient.BitbucketCommit bitbucketCommit, String username, String appPassword,
			String repositoryUrl) {
		try {
			String diffContent = bitbucketClient.fetchCommitDiffs(owner, repository, bitbucketCommit.getHash(),
					username, appPassword, repositoryUrl);
			BitbucketParser bitbucketParser = bitbucketClient
					.getBitbucketParser(repositoryUrl.contains("bitbucket.org"));
			List<ScmCommits.FileChange> fileChanges = bitbucketParser.parseDiffToFileChanges(diffContent);
			commitBuilder.fileChanges(fileChanges);

			if (bitbucketCommit.getStats() == null && !fileChanges.isEmpty()) {
				int addedLines = fileChanges.stream().mapToInt(ScmCommits.FileChange::getAddedLines).sum();
				int removedLines = fileChanges.stream().mapToInt(ScmCommits.FileChange::getRemovedLines).sum();
				commitBuilder.addedLines(addedLines);
				commitBuilder.removedLines(removedLines);
			}
		} catch (Exception e) {
			log.warn("Failed to fetch diff for commit {}: {}", bitbucketCommit.getHash(), e.getMessage());
			commitBuilder.fileChanges(new ArrayList<>());
		}
	}

	/**
	 * Converts a Bitbucket pull request to domain MergeRequest object.
	 */
	private ScmMergeRequests convertToMergeRequest(BitbucketClient.BitbucketPullRequest bitbucketPr,
			String toolConfigId, String owner, String repository, String username, String appPassword,
			String repositoryUrl) {
		try {
			String mrState = convertPullRequestState(bitbucketPr.getState()).name();

			ScmMergeRequests.ScmMergeRequestsBuilder mrBuilder = ScmMergeRequests.builder()
					.externalId(bitbucketPr.getId().toString()).title(bitbucketPr.getTitle())
					.summary(bitbucketPr.getDescription()).state(mrState).processorItemId(new ObjectId(toolConfigId))
					.repoSlug(repository);

			setMergeRequestDates(mrBuilder, bitbucketPr, mrState);

			setMergeRequestParticipants(mrBuilder, bitbucketPr, repository);

			setMergeRequestBranches(mrBuilder, bitbucketPr);

			mrBuilder.mergeRequestUrl(bitbucketPr.getSelfLink());

			fetchAndSetMergeRequestStats(mrBuilder, owner, repository, bitbucketPr, username, appPassword,
					repositoryUrl);

			return mrBuilder.build();

		} catch (Exception e) {
			log.error("Error converting Bitbucket pull request to domain object: {}", e.getMessage(), e);
			throw new GitScannerException("Failed to convert Bitbucket pull request", e);
		}
	}

	private void setMergeRequestDates(ScmMergeRequests.ScmMergeRequestsBuilder mrBuilder,
			BitbucketClient.BitbucketPullRequest bitbucketPr, String mrState) {

		// Set created date
		if (bitbucketPr.getCreatedOn() != null) {
			parseAndSetDate(bitbucketPr.getCreatedOn(), "created date")
					.ifPresent(date -> mrBuilder.createdDate(date.toInstant(java.time.ZoneOffset.UTC).toEpochMilli()));
		}

		// Set updated date
		if (bitbucketPr.getUpdatedOn() != null) {
			parseAndSetDate(bitbucketPr.getUpdatedOn(), "updated date")
					.ifPresent(date -> mrBuilder.updatedDate(date.toInstant(java.time.ZoneOffset.UTC).toEpochMilli()));
		}

		// Set closed/merged dates
		if (bitbucketPr.getClosedOn() != null) {
			parseAndSetDate(bitbucketPr.getClosedOn(), "closed date").ifPresent(closedDate -> {
				long closedDateEpoch = closedDate.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();

				if (mrState.equalsIgnoreCase(ScmMergeRequests.MergeRequestState.MERGED.toString())) {
					mrBuilder.mergedAt(DateUtil.convertMillisToLocalDateTime(closedDateEpoch));
				}

				if (mrState.equalsIgnoreCase(ScmMergeRequests.MergeRequestState.MERGED.toString())
						|| mrState.equalsIgnoreCase(ScmMergeRequests.MergeRequestState.CLOSED.toString())) {
					mrBuilder.isClosed(true);
					mrBuilder.closedDate(closedDateEpoch);
				}
			});
		}

		// Set open status if not closed
		if (!mrState.equalsIgnoreCase(ScmMergeRequests.MergeRequestState.MERGED.toString())
				&& !mrState.equalsIgnoreCase(ScmMergeRequests.MergeRequestState.CLOSED.toString())) {
			mrBuilder.isOpen(true);
		}

		// Set picked up date
		if (bitbucketPr.getPickedUpOn() != null) {
			parseAndSetDate(bitbucketPr.getPickedUpOn(), "picked up date").ifPresent(
					date -> mrBuilder.pickedForReviewOn(date.toInstant(java.time.ZoneOffset.UTC).toEpochMilli()));
		}
	}

	private void setMergeRequestParticipants(ScmMergeRequests.ScmMergeRequestsBuilder mrBuilder,
			BitbucketClient.BitbucketPullRequest bitbucketPr, String repository) {

		// Set author
		if (bitbucketPr.getAuthor() != null) {
			User author = extractUser(bitbucketPr.getAuthor(), repository);
			if (author != null) {
				mrBuilder.authorUserId(author.getUsername());
				mrBuilder.authorId(author);
			}
		}

		// Set reviewers
		if (bitbucketPr.getReviewers() != null && !bitbucketPr.getReviewers().isEmpty()) {
			List<String> reviewerUserIds = bitbucketPr.getReviewers().stream()
					.map(reviewer -> extractUser(reviewer, repository)).filter(Objects::nonNull)
					.map(User::getUsername).toList();
			mrBuilder.reviewerUserIds(reviewerUserIds);
		}
	}

	private void setMergeRequestBranches(ScmMergeRequests.ScmMergeRequestsBuilder mrBuilder,
			BitbucketClient.BitbucketPullRequest bitbucketPr) {

		if (bitbucketPr.getSource() != null && bitbucketPr.getSource().getBranch() != null) {
			mrBuilder.fromBranch(bitbucketPr.getSource().getBranch().getName());
		}

		if (bitbucketPr.getDestination() != null && bitbucketPr.getDestination().getBranch() != null) {
			mrBuilder.toBranch(bitbucketPr.getDestination().getBranch().getName());
		}
	}

	private void fetchAndSetMergeRequestStats(ScmMergeRequests.ScmMergeRequestsBuilder mrBuilder, String owner,
			String repository, BitbucketClient.BitbucketPullRequest bitbucketPr, String username, String appPassword,
			String repositoryUrl) {
		try {
			String diffContent = bitbucketClient.fetchPullRequestDiffs(owner, repository, bitbucketPr.getId(), username,
					appPassword, repositoryUrl);
			BitbucketParser bitbucketParser = bitbucketClient
					.getBitbucketParser(repositoryUrl.contains("bitbucket.org"));
			ScmMergeRequests.PullRequestStats stats = bitbucketParser.parsePRDiffToFileChanges(diffContent);
			mrBuilder.addedLines(stats.getAddedLines());
			mrBuilder.removedLines(stats.getRemovedLines());
			mrBuilder.filesChanged(stats.getChangedFiles());
		} catch (Exception e) {
			log.warn("Failed to fetch diff for pull request {}: {}", bitbucketPr.getId(), e.getMessage());
			mrBuilder.addedLines(0);
			mrBuilder.removedLines(0);
			mrBuilder.filesChanged(0);
		}
	}

	private Optional<LocalDateTime> parseAndSetDate(String dateString, String dateType) {
		try {
			return Optional.of(LocalDateTime.parse(dateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
		} catch (Exception e) {
			log.warn("Failed to parse {}: {}", dateType, dateString);
			return Optional.empty();
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
			return createUserFromBasicInfo(bitbucketUser, repositoryName);
		} else {
			return createUserFromDetailedInfo(bitbucketUser, repositoryName);
		}
	}

	private User createUserFromBasicInfo(BitbucketClient.BitbucketUser bitbucketUser, String repositoryName) {
		return User.builder().repositoryName(repositoryName).username(bitbucketUser.getName())
				.email(bitbucketUser.getEmailAddress())
				.displayName(bitbucketUser.getDisplayName() != null ? bitbucketUser.getDisplayName()
						: bitbucketUser.getName())
				.externalId(bitbucketUser.getUuid()).active(true).bot("team".equals(bitbucketUser.getType())).build();
	}

	private User createUserFromDetailedInfo(BitbucketClient.BitbucketUser bitbucketUser, String repositoryName) {
		BitbucketClient.BbUser userDetails = bitbucketUser.getUser();
		String username = userDetails.getUsername() != null ? userDetails.getUsername() : userDetails.getNickname();
		String externalId = userDetails.getUuid() != null ? userDetails.getUuid() : userDetails.getAccountId();

		return User.builder().repositoryName(repositoryName).username(username)
				.displayName(userDetails.getDisplayName()).externalId(externalId).active(true)
				.bot("team".equals(bitbucketUser.getType())).build();
	}

	/**
	 * Converts Bitbucket pull request state to domain MergeRequest state.
	 */
	private ScmMergeRequests.MergeRequestState convertPullRequestState(String bitbucketState) {
		if (bitbucketState == null) {
			return ScmMergeRequests.MergeRequestState.OPEN;
		}

		return switch (bitbucketState.toUpperCase()) {
		case "OPEN" -> ScmMergeRequests.MergeRequestState.OPEN;
		case "MERGED" -> ScmMergeRequests.MergeRequestState.MERGED;
		case "DECLINED", "SUPERSEDED" -> ScmMergeRequests.MergeRequestState.CLOSED;
		default -> {
			log.warn("Unknown Bitbucket pull request state: {}", bitbucketState);
			yield ScmMergeRequests.MergeRequestState.OPEN;
		}
		};
	}

	/**
	 * Extracts credentials from token string. CHANGE: Renamed from
	 * extractCredentials to parseCredentials and returns a record
	 */
	private Credentials parseCredentials(String token) {
		if (token == null || token.isBlank()) {
			throw new IllegalArgumentException("Bitbucket token cannot be null or empty");
		}

		if (!token.contains(":")) {
			throw new IllegalArgumentException("Bitbucket token must be in format 'username:appPassword'");
		}

		String[] parts = token.split(":", 2);
		if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
			throw new IllegalArgumentException("Bitbucket token must contain valid username and app password");
		}

		return new Credentials(parts[0].trim(), parts[1].trim());
	}

	private record Credentials(String username, String appPassword) {
	}
}
