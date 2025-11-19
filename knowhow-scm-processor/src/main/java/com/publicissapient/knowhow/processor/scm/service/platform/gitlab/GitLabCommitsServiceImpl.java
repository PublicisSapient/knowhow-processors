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

package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.knowhow.processor.scm.client.gitlab.GitLabClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformCommitsService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.Diff;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class GitLabCommitsServiceImpl implements GitPlatformCommitsService {

	private static final String PLATFORM_NAME = "GitLab";
	private static final String PATH_SEPARATOR = "/";

	private final GitLabClient gitLabClient;
	private final GitLabCommonHelper commonHelper;

	public GitLabCommitsServiceImpl(GitLabClient gitLabClient, GitLabCommonHelper commonHelper) {
		this.gitLabClient = gitLabClient;
		this.commonHelper = commonHelper;
	}

	@Override
	public List<ScmCommits> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
			String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
		try {
			log.info("Fetching commits for GitLab repository: {}/{}", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName());

			String repositoryUrl = gitUrlInfo.getOriginalUrl();
			String owner = gitUrlInfo.getOrganization() != null ? gitUrlInfo.getOrganization() : gitUrlInfo.getOwner();

			List<Commit> gitlabCommits = gitLabClient.fetchCommits(owner, gitUrlInfo.getRepositoryName(), branchName,
					token, since, until, repositoryUrl);

			List<ScmCommits> commitDetails = convertCommits(gitlabCommits, toolConfigId, owner,
					gitUrlInfo.getRepositoryName(), token, repositoryUrl);

			log.info("Successfully converted {} GitLab commits to domain objects", commitDetails.size());
			return commitDetails;

		} catch (GitLabApiException e) {
			log.error("Failed to fetch commits from GitLab repository {}/{}: {}", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), e.getMessage());
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch commits from GitLab", e);
		}
	}

	private List<ScmCommits> convertCommits(List<Commit> gitlabCommits, String toolConfigId, String owner,
			String repositoryName, String token, String repositoryUrl) {
		List<ScmCommits> commitDetails = new ArrayList<>();

		for (Commit gitlabCommit : gitlabCommits) {
			try {
				ScmCommits commitDetail = convertToCommit(gitlabCommit, toolConfigId, owner, repositoryName, token,
						repositoryUrl);
				commitDetails.add(commitDetail);
			} catch (Exception e) {
				log.warn("Failed to convert GitLab commit {}: {}", gitlabCommit.getId(), e.getMessage());
			}
		}

		return commitDetails;
	}

	private ScmCommits convertToCommit(Commit gitlabCommit, String toolConfigId, String owner, String repository,
			String token, String repositoryUrl) {
		ScmCommits.ScmCommitsBuilder builder = ScmCommits.builder().processorItemId(new ObjectId(toolConfigId))
				.repositoryName(owner + PATH_SEPARATOR + repository).sha(gitlabCommit.getId())
				.commitMessage(gitlabCommit.getMessage())
				.commitTimestamp(gitlabCommit.getCreatedAt().toInstant().toEpochMilli());

		setCommitAuthorInfo(builder, gitlabCommit);
		setCommitTimestamps(builder, gitlabCommit);
		setCommitParentInfo(builder, gitlabCommit);
		setCommitDiffStats(builder, gitlabCommit, owner, repository, token, repositoryUrl);

		return builder.build();
	}

	private void setCommitAuthorInfo(ScmCommits.ScmCommitsBuilder builder, Commit gitlabCommit) {
		if (gitlabCommit.getAuthorName() != null) {
			builder.commitAuthorId(gitlabCommit.getAuthorName()).authorName(gitlabCommit.getAuthorName())
					.authorEmail(gitlabCommit.getAuthorEmail());
			User user = commonHelper.createUser(gitlabCommit.getAuthorName(), gitlabCommit.getAuthorEmail(),
					gitlabCommit.getCommitterName());
			builder.commitAuthor(user);
		}

		if (gitlabCommit.getCommitterName() != null) {
			builder.committerId(gitlabCommit.getCommitterName()).committerName(gitlabCommit.getCommitterName())
					.committerEmail(gitlabCommit.getCommitterEmail());
		}
	}

	private void setCommitTimestamps(ScmCommits.ScmCommitsBuilder builder, Commit gitlabCommit) {
		if (gitlabCommit.getCommittedDate() != null) {
			builder.commitTimestamp(gitlabCommit.getCommittedDate().toInstant().toEpochMilli());
		}
	}

	private void setCommitParentInfo(ScmCommits.ScmCommitsBuilder builder, Commit gitlabCommit) {
		if (gitlabCommit.getParentIds() != null && !gitlabCommit.getParentIds().isEmpty()) {
			builder.parentShas(gitlabCommit.getParentIds()).isMergeCommit(gitlabCommit.getParentIds().size() > 1);
		}
	}

	private void setCommitDiffStats(ScmCommits.ScmCommitsBuilder builder, Commit gitlabCommit, String owner,
			String repository, String token, String repositoryUrl) {
		try {
			if (gitlabCommit.getStats() != null) {
				int additions = gitlabCommit.getStats().getAdditions();
				int deletions = gitlabCommit.getStats().getDeletions();
				int total = gitlabCommit.getStats().getTotal();

				List<ScmCommits.FileChange> fileChanges = extractFileChanges(gitlabCommit, owner, repository, token,
						repositoryUrl);
				builder.addedLines(additions).removedLines(deletions).changedLines(total)
						.filesChanged(fileChanges.size()).fileChanges(fileChanges);
			} else {
				List<ScmCommits.FileChange> fileChanges = extractFileChanges(gitlabCommit, owner, repository, token,
						repositoryUrl);
				int totalAdditions = fileChanges.stream()
						.mapToInt(fc -> fc.getAddedLines() != null ? fc.getAddedLines() : 0).sum();
				int totalDeletions = fileChanges.stream()
						.mapToInt(fc -> fc.getRemovedLines() != null ? fc.getRemovedLines() : 0).sum();
				builder.addedLines(totalAdditions).removedLines(totalDeletions)
						.changedLines(totalAdditions + totalDeletions).filesChanged(fileChanges.size())
						.fileChanges(fileChanges);
			}
		} catch (Exception e) {
			log.warn("Failed to extract diff stats from commit {}: {}", gitlabCommit.getId(), e.getMessage());
			builder.addedLines(0).removedLines(0).changedLines(0).filesChanged(0).fileChanges(new ArrayList<>());
		}
	}

	private List<ScmCommits.FileChange> extractFileChanges(Commit gitlabCommit, String owner, String repository,
			String token, String repositoryUrl) {
		List<ScmCommits.FileChange> fileChanges = new ArrayList<>();
		try {
			List<Diff> diffs = gitLabClient.fetchCommitDiffs(owner, repository, gitlabCommit.getId(), token,
					repositoryUrl);
			for (Diff diff : diffs) {
				ScmCommits.FileChange fileChange = commonHelper.convertDiffToFileChange(diff);
				if (fileChange != null)
					fileChanges.add(fileChange);
			}
		} catch (Exception e) {
			log.debug("Could not extract detailed file changes for commit {}: {}", gitlabCommit.getId(),
					e.getMessage());
		}
		return fileChanges;
	}

}
