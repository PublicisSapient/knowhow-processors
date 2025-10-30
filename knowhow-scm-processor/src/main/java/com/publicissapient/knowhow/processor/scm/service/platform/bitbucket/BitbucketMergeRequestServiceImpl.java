package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.exception.GitScannerException;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformMergeRequestService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.knowhow.processor.scm.util.wrapper.BitbucketParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class BitbucketMergeRequestServiceImpl implements GitPlatformMergeRequestService {

	private final BitbucketClient bitbucketClient;
	private final BitbucketCommonHelper commonHelper;

	public BitbucketMergeRequestServiceImpl(BitbucketClient bitbucketClient, BitbucketCommonHelper commonHelper) {
		this.bitbucketClient = bitbucketClient;
		this.commonHelper = commonHelper;
	}

	@Override
	public List<ScmMergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo,
			String branchName, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
		log.info("Fetching merge requests for Bitbucket repository: {}/{} (branch: {})", gitUrlInfo.getOwner(),
				gitUrlInfo.getRepositoryName(), branchName != null ? branchName : "all");

		BitbucketCommonHelper.Credentials credentials = BitbucketCommonHelper.Credentials.parse(token);
		List<BitbucketClient.BitbucketPullRequest> bitbucketPRs = bitbucketClient.fetchPullRequests(
				gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName(), branchName, credentials.username(),
				credentials.password(), since, gitUrlInfo.getOriginalUrl());

		List<ScmMergeRequests> mergeRequests = new ArrayList<>();
		for (BitbucketClient.BitbucketPullRequest bbPr : bitbucketPRs) {
			try {
				ScmMergeRequests mergeRequest = convertToMergeRequest(bbPr, toolConfigId, gitUrlInfo.getOwner(),
						gitUrlInfo.getRepositoryName(), credentials.username(), credentials.password(),
						gitUrlInfo.getOriginalUrl());
				mergeRequests.add(mergeRequest);
			} catch (Exception e) {
				log.warn("Failed to convert Bitbucket pull request: {}", e.getMessage());
			}
		}

		log.info("Successfully converted {} Bitbucket pull requests to domain objects", mergeRequests.size());
		return mergeRequests;

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

			setMergeRequestParticipants(mrBuilder, bitbucketPr);

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
			BitbucketClient.BitbucketPullRequest bitbucketPr) {
		// Set author
		if (bitbucketPr.getAuthor() != null) {
			BitbucketClient.BbUser authorUser = bitbucketPr.getAuthor().getUser();
			if (authorUser != null) {
				String username = authorUser.getUsername();
				String displayName = authorUser.getDisplayName();
				User user = commonHelper.createUser(username, displayName, null);
				mrBuilder.authorId(user).authorUserId(username);
			}
		}

		// Set reviewers
		if (bitbucketPr.getReviewers() != null && !bitbucketPr.getReviewers().isEmpty()) {
			List<String> reviewerUserIds = bitbucketPr.getReviewers().stream().map(reviewer -> {
				BitbucketClient.BbUser authorUser = reviewer.getUser();
				String username = authorUser.getUsername();
				String displayName = authorUser.getDisplayName();
				return commonHelper.createUser(username, displayName, null);
			}).filter(Objects::nonNull).map(User::getUsername).toList();
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

}
