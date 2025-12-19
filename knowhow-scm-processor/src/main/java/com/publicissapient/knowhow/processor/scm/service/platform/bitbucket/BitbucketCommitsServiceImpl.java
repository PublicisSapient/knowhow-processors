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
import com.publicissapient.knowhow.processor.scm.exception.GitScannerException;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformCommitsService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.knowhow.processor.scm.util.wrapper.BitbucketParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class BitbucketCommitsServiceImpl implements GitPlatformCommitsService {

	private final BitbucketClient bitbucketClient;
	private final BitbucketCommonHelper commonHelper;

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
				gitUrlInfo.getOriginalUrl());

		List<ScmCommits> commitDetails = new ArrayList<>();
		for (BitbucketClient.BitbucketCommit bbCommit : bitbucketCommits) {
			try {
				ScmCommits commitDetail = convertToCommit(bbCommit, toolConfigId, gitUrlInfo.getOwner(),
						gitUrlInfo.getRepositoryName(), credentials.username(), credentials.password(),
						gitUrlInfo.getOriginalUrl());
				commitDetails.add(commitDetail);
			} catch (Exception e) {
				log.warn("Failed to convert Bitbucket commit: {}", e.getMessage());
			}
		}

		log.info("Successfully converted {} Bitbucket commits to domain objects", commitDetails.size());
		return commitDetails;

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

			setCommitAuthor(commitBuilder, bitbucketCommit.getAuthor());

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
			commitBuilder.commitTimestamp(Instant.parse(dateString).toEpochMilli());
		}
	}

	private void setCommitAuthor(ScmCommits.ScmCommitsBuilder commitBuilder, BitbucketClient.BitbucketUser author) {
		if (author != null) {
			BitbucketClient.BbUser authorUser = author.getUser();
			if (authorUser != null) {
				String username = authorUser.getUsername();
				String displayName = authorUser.getDisplayName();
				User user = commonHelper.createUser(username, displayName, null);
				commitBuilder.commitAuthor(user).authorName(username);
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

}
