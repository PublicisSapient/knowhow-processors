package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformMergeRequestService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                credentials.password(), since, String.valueOf(until));

        List<ScmMergeRequests> mergeRequests = new ArrayList<>();
        for (BitbucketClient.BitbucketPullRequest bbPr : bitbucketPRs) {
            try {
                ScmMergeRequests mergeRequest = convertToMergeRequest(bbPr, toolConfigId, gitUrlInfo.getOwner(),
                        gitUrlInfo.getRepositoryName());
                mergeRequests.add(mergeRequest);
            } catch (Exception e) {
                log.warn("Failed to convert Bitbucket pull request: {}", e.getMessage());
            }
        }

        log.info("Successfully converted {} Bitbucket pull requests to domain objects", mergeRequests.size());
        return mergeRequests;

    }

	private ScmMergeRequests convertToMergeRequest(BitbucketClient.BitbucketPullRequest bbPr, String toolConfigId,
			String owner, String repository) {
		Long id = bbPr.getId();
		String title = bbPr.getTitle();
		String description = bbPr.getDescription();
		String state = bbPr.getState();

		String source = bbPr.getSource().getBranch().getName();
		String destination = bbPr.getDestination().getBranch().getName();

		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder()
				.processorItemId(new ObjectId(toolConfigId)).repositoryName(owner + "/" + repository)
				.externalId(String.valueOf(id)).title(title).summary(description).fromBranch(source)
				.toBranch(destination);

		commonHelper.setPullRequestState(builder, state);
		setMergeRequestTimestamps(builder, bbPr);
		setMergeRequestAuthor(builder, bbPr);
		setMergeRequestUrl(builder, bbPr);

		return builder.build();
	}

	private void setMergeRequestTimestamps(ScmMergeRequests.ScmMergeRequestsBuilder builder,
			BitbucketClient.BitbucketPullRequest bbPr) {
		String createdOn = bbPr.getCreatedOn();
		String updatedOn = bbPr.getUpdatedOn();
		String closedOn = bbPr.getClosedOn();

		if (createdOn != null) {
			builder.createdDate(Instant.parse(createdOn).toEpochMilli());
		}
		if (updatedOn != null) {
			builder.updatedDate(Instant.parse(updatedOn).toEpochMilli());
		}

		Instant updatedInstant = updatedOn != null ? Instant.parse(updatedOn) : null;
		Instant closedInstant = closedOn != null ? Instant.parse(closedOn) : null;
		commonHelper.setMergeRequestTimestamps(builder, updatedInstant, closedInstant);
	}

	private void setMergeRequestAuthor(ScmMergeRequests.ScmMergeRequestsBuilder builder,
			BitbucketClient.BitbucketPullRequest bbPr) {
		BitbucketClient.BitbucketUser author = bbPr.getAuthor();
		if (author != null) {
			BitbucketClient.BbUser authorUser = author.getUser();
			if (authorUser != null) {
				String username = authorUser.getUsername();
				String displayName = authorUser.getDisplayName();
				User user = commonHelper.createUser(username, displayName, null);
				builder.authorId(user).authorUserId(username);
			}
		}
	}

	private void setMergeRequestUrl(ScmMergeRequests.ScmMergeRequestsBuilder builder,
			BitbucketClient.BitbucketPullRequest bbPr) {
		String link = bbPr.getSelfLink();
		if (link != null) {
			builder.mergeRequestUrl(link);
		}
	}

}
