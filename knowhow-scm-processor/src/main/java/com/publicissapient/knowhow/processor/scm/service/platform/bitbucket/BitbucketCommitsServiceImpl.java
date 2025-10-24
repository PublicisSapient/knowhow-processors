package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformCommitsService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@NoArgsConstructor
public class BitbucketCommitsServiceImpl implements GitPlatformCommitsService {

	private BitbucketClient bitbucketClient;
	private BitbucketCommonHelper commonHelper;

	public BitbucketCommitsServiceImpl(BitbucketClient bitbucketClient, BitbucketCommonHelper commonHelper) {
		this.bitbucketClient = bitbucketClient;
		this.commonHelper = commonHelper;
	}

	@Override
	public List<ScmCommits> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
			String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {

		log.info("Fetching commits for Bitbucket repository: {}/{}", gitUrlInfo.getOwner(),
				gitUrlInfo.getRepositoryName());

		BitbucketCommonHelper.Credentials credentials = BitbucketCommonHelper.Credentials.parse(token);
		List<BitbucketClient.BitbucketCommit> bitbucketCommits = bitbucketClient.fetchCommits(gitUrlInfo.getOwner(),
				gitUrlInfo.getRepositoryName(), branchName, credentials.username(), credentials.password(), since,
				String.valueOf(until));

		List<ScmCommits> commitDetails = new ArrayList<>();
		for (BitbucketClient.BitbucketCommit bbCommit : bitbucketCommits) {
			try {
				ScmCommits commitDetail = convertToCommit(bbCommit, toolConfigId, gitUrlInfo.getOwner(),
						gitUrlInfo.getRepositoryName());
				commitDetails.add(commitDetail);
			} catch (Exception e) {
				log.warn("Failed to convert Bitbucket commit: {}", e.getMessage());
			}
		}

		log.info("Successfully converted {} Bitbucket commits to domain objects", commitDetails.size());
		return commitDetails;

	}

	private ScmCommits convertToCommit(BitbucketClient.BitbucketCommit bbCommit, String toolConfigId, String owner,
			String repository) {
		String hash = bbCommit.getHash();
		String message = bbCommit.getMessage();
		ScmCommits.ScmCommitsBuilder builder = ScmCommits.builder().processorItemId(new ObjectId(toolConfigId))
				.repositoryName(owner + "/" + repository).sha(hash).commitMessage(message);

		setCommitAuthor(builder, bbCommit);
		setCommitTimestamp(builder, bbCommit);
		setCommitParentInfo(builder, bbCommit);
		setCommitStats(builder, bbCommit);

		return builder.build();
	}

	private void setCommitAuthor(ScmCommits.ScmCommitsBuilder builder, BitbucketClient.BitbucketCommit bbCommit) {
		BitbucketClient.BitbucketUser author = bbCommit.getAuthor();
		if (author != null) {
			BitbucketClient.BbUser authorUser = author.getUser();
			if (authorUser != null) {
				String username = authorUser.getUsername();
				String displayName = authorUser.getDisplayName();
				User user = commonHelper.createUser(username, displayName, null);
				builder.commitAuthor(user).authorName(username);
			}
		}
	}

	private void setCommitTimestamp(ScmCommits.ScmCommitsBuilder builder, BitbucketClient.BitbucketCommit bbCommit) {
		String date = bbCommit.getDate();
		if (date != null) {
			builder.commitTimestamp(java.time.Instant.parse(date).toEpochMilli());

		}
	}

	private void setCommitParentInfo(ScmCommits.ScmCommitsBuilder builder, BitbucketClient.BitbucketCommit bbCommit) {
		List<String> parents = bbCommit.getParents();
		if (parents != null && !parents.isEmpty()) {
			builder.parentShas(parents).isMergeCommit(parents.size() > 1);
		}
	}

	private void setCommitStats(ScmCommits.ScmCommitsBuilder builder, BitbucketClient.BitbucketCommit bbCommit) {
		Integer additions = bbCommit.getStats().getAdditions();
		Integer deletions = bbCommit.getStats().getDeletions();
		builder.addedLines(additions != null ? additions : 0).removedLines(deletions != null ? deletions : 0)
				.changedLines((additions != null ? additions : 0) + (deletions != null ? deletions : 0));
	}

}
