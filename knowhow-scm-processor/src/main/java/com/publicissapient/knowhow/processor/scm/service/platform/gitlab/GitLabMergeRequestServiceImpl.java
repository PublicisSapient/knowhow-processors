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
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformMergeRequestService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.MergeRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class GitLabMergeRequestServiceImpl implements GitPlatformMergeRequestService {

	private static final String PLATFORM_NAME = "GitLab";
	private static final String PATH_SEPARATOR = "/";

	private final GitLabClient gitLabClient;
	private final GitLabCommonHelper commonHelper;

	@Value("${git.platforms.gitlab.api-url:https://gitlab.com}")
	private String defaultGitlabApiUrl;

	public GitLabMergeRequestServiceImpl(GitLabClient gitLabClient, GitLabCommonHelper commonHelper) {
		this.gitLabClient = gitLabClient;
		this.commonHelper = commonHelper;
	}

	@Override
	public List<ScmMergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo,
			String branchName, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
		try {
			log.info("Fetching merge requests for GitLab repository: {}/{} (branch: {})", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), branchName != null ? branchName : "all");

			String owner = gitUrlInfo.getOrganization() != null ? gitUrlInfo.getOrganization() : gitUrlInfo.getOwner();

			List<MergeRequest> gitlabMergeRequests = gitLabClient.fetchMergeRequests(owner,
					gitUrlInfo.getRepositoryName(), branchName, token, since, until, gitUrlInfo.getOriginalUrl());

			List<ScmMergeRequests> mergeRequests = convertMergeRequests(gitlabMergeRequests, toolConfigId, owner,
					gitUrlInfo.getRepositoryName(), token, gitUrlInfo.getOriginalUrl());

			log.info("Successfully converted {} GitLab merge requests to domain objects", mergeRequests.size());
			return mergeRequests;

		} catch (GitLabApiException e) {
			log.error("Failed to fetch merge requests from GitLab repository {}/{}: {}", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), e.getMessage());
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch merge requests from GitLab", e);
		}
	}

	private List<ScmMergeRequests> convertMergeRequests(List<MergeRequest> gitlabMergeRequests, String toolConfigId,
			String owner, String repositoryName, String token, String repositoryUrl) {
		List<ScmMergeRequests> mergeRequests = new ArrayList<>();

		for (MergeRequest gitlabMr : gitlabMergeRequests) {
			try {
				ScmMergeRequests mergeRequest = convertToMergeRequest(gitlabMr, toolConfigId, owner, repositoryName,
						token, repositoryUrl);
				mergeRequests.add(mergeRequest);
			} catch (Exception e) {
				log.warn("Failed to convert GitLab merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
			}
		}

		return mergeRequests;
	}

	private ScmMergeRequests convertToMergeRequest(MergeRequest gitlabMr, String toolConfigId, String owner,
			String repository, String token, String repositoryUrl) throws GitLabApiException {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder()
				.processorItemId(new ObjectId(toolConfigId)).repositoryName(owner + PATH_SEPARATOR + repository)
				.externalId(gitlabMr.getIid().toString()).title(gitlabMr.getTitle()).summary(gitlabMr.getDescription())
				.state(gitlabMr.getState().toLowerCase()).fromBranch(gitlabMr.getSourceBranch())
				.toBranch(gitlabMr.getTargetBranch()).createdDate(gitlabMr.getCreatedAt().toInstant().toEpochMilli())
				.updatedDate(gitlabMr.getUpdatedAt().toInstant().toEpochMilli());

		commonHelper.setMergeRequestState(builder, gitlabMr);
		commonHelper.setMergeRequestTimestamps(builder, gitlabMr);
		commonHelper.setMergeRequestAuthor(builder, gitlabMr);

		if (gitlabMr.getWebUrl() != null) {
			builder.mergeRequestUrl(gitlabMr.getWebUrl());
		}
		if (gitlabMr.getWorkInProgress() != null) {
			builder.isDraft(gitlabMr.getWorkInProgress());
		}

		long pickUpTime = gitLabClient.getPrPickUpTimeStamp(owner, repository, token, repositoryUrl, gitlabMr.getIid());
		builder.pickedForReviewOn(pickUpTime);

		MergeRequestStats mrStats = extractMergeRequestStats(gitlabMr, owner, repository, token, repositoryUrl);
		builder.linesChanged(mrStats.getLinesChanged()).commitCount(mrStats.getCommitCount())
				.filesChanged(mrStats.getFilesChanged()).addedLines(mrStats.getAddedLines())
				.removedLines(mrStats.getRemovedLines());

		return builder.build();
	}

	private MergeRequestStats extractMergeRequestStats(MergeRequest gitlabMr, String owner, String repository,
			String token, String repositoryUrl) {
		try {
			MergeRequestStatsBuilder statsBuilder = new MergeRequestStatsBuilder();

			processMergeRequestChanges(statsBuilder, gitlabMr, owner, repository, token, repositoryUrl);
			processMergeRequestCommits(statsBuilder, gitlabMr, owner, repository, token, repositoryUrl);
			processFallbackChangesCount(statsBuilder, gitlabMr);

			return statsBuilder.build();
		} catch (Exception e) {
			log.warn("Failed to extract stats from merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
			return new MergeRequestStats(0, 0, 0, 0, 0);
		}
	}

	private void processMergeRequestChanges(MergeRequestStatsBuilder statsBuilder, MergeRequest gitlabMr, String owner,
			String repository, String token, String repositoryUrl) {
		try {
			List<Diff> changes = gitLabClient.fetchMergeRequestChanges(owner, repository, gitlabMr.getIid(), token,
					repositoryUrl);
			for (Diff diff : changes) {
				if (diff.getDiff() != null) {
					GitLabCommonHelper.DiffStats diffStats = commonHelper.parseDiffContent(diff.getDiff());
					statsBuilder.addAddedLines(diffStats.getAddedLines());
					statsBuilder.addRemovedLines(diffStats.getRemovedLines());
					statsBuilder.incrementFilesChanged();
				}
			}
		} catch (Exception e) {
			log.debug("Could not fetch detailed changes for merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
		}
	}

	private void processMergeRequestCommits(MergeRequestStatsBuilder statsBuilder, MergeRequest gitlabMr, String owner,
			String repository, String token, String repositoryUrl) {
		try {
			List<Commit> commits = gitLabClient.fetchMergeRequestCommits(owner, repository, gitlabMr.getIid(), token,
					repositoryUrl);
			statsBuilder.setCommitCount(commits.size());
		} catch (Exception e) {
			log.debug("Could not fetch commits for merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
		}
	}

	private void processFallbackChangesCount(MergeRequestStatsBuilder statsBuilder, MergeRequest gitlabMr) {
		if (statsBuilder.getLinesChanged() == 0 && gitlabMr.getChangesCount() != null) {
			try {
				statsBuilder.setLinesChanged(Integer.parseInt(gitlabMr.getChangesCount()));
			} catch (NumberFormatException e) {
				log.debug("Could not parse changes count '{}' for merge request !{}", gitlabMr.getChangesCount(),
						gitlabMr.getIid());
			}
		}
	}

	@Getter
	private static class MergeRequestStats {
		private final int linesChanged;
		private final int commitCount;
		private final int filesChanged;
		private final int addedLines;
		private final int removedLines;

		public MergeRequestStats(int linesChanged, int commitCount, int filesChanged, int addedLines,
				int removedLines) {
			this.linesChanged = linesChanged;
			this.commitCount = commitCount;
			this.filesChanged = filesChanged;
			this.addedLines = addedLines;
			this.removedLines = removedLines;
		}
	}

	private static class MergeRequestStatsBuilder {
		@Getter
		@Setter
		private int linesChanged = 0;
		@Setter
		private int commitCount = 0;
		private int filesChanged = 0;
		private int addedLines = 0;
		private int removedLines = 0;

		public void addAddedLines(int lines) {
			this.addedLines += lines;
			this.linesChanged += lines;
		}

		public void addRemovedLines(int lines) {
			this.removedLines += lines;
			this.linesChanged += lines;
		}

		public void incrementFilesChanged() {
			this.filesChanged++;
		}

		public MergeRequestStats build() {
			return new MergeRequestStats(linesChanged, commitCount, filesChanged, addedLines, removedLines);
		}
	}
}
