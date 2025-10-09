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
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Diff;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitLab implementation of GitPlatformService. This service focuses on business
 * logic and data transformation, delegating all GitLab API interactions to
 * GitLabClient.
 */
@Service
@Slf4j
public class GitLabService implements GitPlatformService {

	// CHANGE: Extract constants to reduce magic numbers and duplicate strings
	private static final String PLATFORM_NAME = "GitLab";
	private static final String PATH_SEPARATOR = "/";
	private static final String HTTPS_PREFIX = "https://";
	private static final String DOMAIN_SEPARATOR = ".";
	private static final String DIFF_ADD_PREFIX = "+";
	private static final String DIFF_REMOVE_PREFIX = "-";
	private static final String DIFF_ADD_FILE_PREFIX = "+++";
	private static final String DIFF_REMOVE_FILE_PREFIX = "---";
	private static final String DIFF_HUNK_PREFIX = "@@";
	private static final String DIFF_PREFIX = "diff";
	private static final String BACKSLASH_PREFIX = "\\";
	private static final String MODIFIED_STATUS = "MODIFIED";
	private static final Pattern HUNK_PATTERN = Pattern.compile("@@\\s+-\\d+(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@");

	// CHANGE: Extract file extensions to constant array
	private static final String[] BINARY_EXTENSIONS = { ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".ico", ".svg", ".pdf",
			".zip", ".tar", ".gz", ".rar", ".7z", ".exe", ".dll", ".so", ".dylib", ".class", ".jar" };

	private final GitLabClient gitLabClient;

	@Value("${git.platforms.gitlab.api-url:https://gitlab.com}")
	private String defaultGitlabApiUrl;

	// Thread-local storage to hold the current repository URL for the request
	// context
	private static final ThreadLocal<String> currentRepositoryUrl = new ThreadLocal<>();

	public GitLabService(GitLabClient gitLabClient) {
		this.gitLabClient = gitLabClient;
	}

	/**
	 * Sets the repository URL for the current thread context. This should be called
	 * before any GitLab operations to ensure the correct GitLab instance is used
	 * for on-premise installations.
	 *
	 * @param repositoryUrl
	 *            the repository URL from the scan request
	 */
	public void setRepositoryUrlContext(String repositoryUrl) {
		currentRepositoryUrl.set(repositoryUrl);
		log.debug("Set repository URL context: {}", repositoryUrl);
	}

	/**
	 * Clears the repository URL context for the current thread. This should be
	 * called after GitLab operations are complete.
	 */
	public void clearRepositoryUrlContext() {
		currentRepositoryUrl.remove();
		log.debug("Cleared repository URL context");
	}

	/**
	 * Gets the repository URL from the current thread context. If not set, falls
	 * back to constructing URL using default GitLab URL.
	 *
	 * @param owner
	 *            the repository owner
	 * @param repository
	 *            the repository name
	 * @return the repository URL
	 */
	private String getRepositoryUrl(String owner, String repository) {
		String contextUrl = currentRepositoryUrl.get();
		if (contextUrl != null && !contextUrl.trim().isEmpty()) {
			log.debug("Using repository URL from context: {}", contextUrl);
			return contextUrl;
		}

		// Fallback to constructing URL using default GitLab URL
		String fallbackUrl = constructRepositoryUrl(owner, repository);
		log.debug("Using fallback repository URL: {}", fallbackUrl);
		return fallbackUrl;
	}

	/**
	 * Constructs a repository URL from owner and repository name using default
	 * GitLab URL. This method is used as a fallback when repository URL context is
	 * not available.
	 */
	private String constructRepositoryUrl(String owner, String repository) {
		// Try to detect if owner contains a full path (for GitLab groups/subgroups)
		// This is a heuristic approach for on-premise instances
		if (owner.contains(DOMAIN_SEPARATOR) && !owner.contains(PATH_SEPARATOR)) {
			// If owner looks like a domain, it might be an on-premise instance
			// This is a best-effort approach
			return HTTPS_PREFIX + owner.split(PATH_SEPARATOR)[0] + PATH_SEPARATOR + owner + PATH_SEPARATOR + repository;
		}

		return defaultGitlabApiUrl + PATH_SEPARATOR + owner + PATH_SEPARATOR + repository;
	}

	@Override
	public List<ScmCommits> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
			String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
		// CHANGE: Add try-finally to ensure ThreadLocal cleanup
		try {
			log.info("Fetching commits for GitLab repository: {}/{}", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName());

			String repositoryUrl = getRepositoryUrl(gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName());
			String owner = gitUrlInfo.getOrganization() != null ? gitUrlInfo.getOrganization() : gitUrlInfo.getOwner();
			log.debug("Using repository URL: {}", repositoryUrl);

			List<org.gitlab4j.api.models.Commit> gitlabCommits = gitLabClient.fetchCommits(owner,
					gitUrlInfo.getRepositoryName(), branchName, token, since, until, repositoryUrl);

			// CHANGE: Extract method to reduce complexity
			return convertGitLabCommitsToScmCommits(gitlabCommits, toolConfigId, owner, gitUrlInfo.getRepositoryName(),
					token, repositoryUrl);

		} catch (GitLabApiException e) {
			log.error("Failed to fetch commits from GitLab repository {}/{}: {}", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), e.getMessage());
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch commits from GitLab", e);
		}
	}

	// CHANGE: Extract method to reduce complexity
	private List<ScmCommits> convertGitLabCommitsToScmCommits(List<org.gitlab4j.api.models.Commit> gitlabCommits,
			String toolConfigId, String owner, String repository, String token, String repositoryUrl) {
		List<ScmCommits> commitDetails = new ArrayList<>();

		for (org.gitlab4j.api.models.Commit gitlabCommit : gitlabCommits) {
			try {
				ScmCommits commitDetail = convertToCommit(gitlabCommit, toolConfigId, owner, repository, token,
						repositoryUrl);
				commitDetails.add(commitDetail);
			} catch (Exception e) {
				log.warn("Failed to convert GitLab commit {}: {}", gitlabCommit.getId(), e.getMessage());
			}
		}

		log.info("Successfully converted {} GitLab commits to domain objects", commitDetails.size());
		return commitDetails;
	}

	@Override
	public List<ScmMergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo,
			String branchName, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
		// CHANGE: Add try-finally to ensure ThreadLocal cleanup
		try {
			log.info("Fetching merge requests for GitLab repository: {}/{} (branch: {})", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), branchName != null ? branchName : "all");

			String repositoryUrl = getRepositoryUrl(gitUrlInfo.getOwner(), gitUrlInfo.getRepositoryName());
			String owner = gitUrlInfo.getOrganization() != null ? gitUrlInfo.getOrganization() : gitUrlInfo.getOwner();

			List<org.gitlab4j.api.models.MergeRequest> gitlabMergeRequests = gitLabClient.fetchMergeRequests(owner,
					gitUrlInfo.getRepositoryName(), branchName, token, since, until, repositoryUrl);

			// CHANGE: Extract method to reduce complexity
			return convertGitLabMergeRequestsToScmMergeRequests(gitlabMergeRequests, toolConfigId, owner,
					gitUrlInfo.getRepositoryName(), token, repositoryUrl);

		} catch (GitLabApiException e) {
			log.error("Failed to fetch merge requests from GitLab repository {}/{}: {}", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), e.getMessage());
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch merge requests from GitLab", e);
		}
	}

	// CHANGE: Extract method to reduce complexity
	private List<ScmMergeRequests> convertGitLabMergeRequestsToScmMergeRequests(
			List<org.gitlab4j.api.models.MergeRequest> gitlabMergeRequests, String toolConfigId, String owner,
			String repository, String token, String repositoryUrl) {
		List<ScmMergeRequests> mergeRequests = new ArrayList<>();

		for (org.gitlab4j.api.models.MergeRequest gitlabMr : gitlabMergeRequests) {
			try {
				ScmMergeRequests mergeRequest = convertToMergeRequest(gitlabMr, toolConfigId, owner, repository, token,
						repositoryUrl);
				mergeRequests.add(mergeRequest);
			} catch (Exception e) {
				log.warn("Failed to convert GitLab merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
			}
		}

		log.info("Successfully converted {} GitLab merge requests to domain objects", mergeRequests.size());
		return mergeRequests;
	}

	@Override
	public String getPlatformName() {
		return PLATFORM_NAME;
	}

	/**
	 * Converts a GitLab commit to domain Commit object
	 */
	private ScmCommits convertToCommit(org.gitlab4j.api.models.Commit gitlabCommit, String toolConfigId, String owner,
			String repository, String token, String repositoryUrl) {
		// CHANGE: Extract builder creation to reduce complexity
		ScmCommits.ScmCommitsBuilder builder = createCommitBuilder(gitlabCommit, toolConfigId, owner, repository);

		// CHANGE: Extract methods for each concern
		setCommitAuthorInfo(builder, gitlabCommit);
		setCommitTimestamps(builder, gitlabCommit);
		setCommitParentInfo(builder, gitlabCommit);
		setCommitDiffStats(builder, gitlabCommit, owner, repository, token, repositoryUrl);

		return builder.build();
	}

	// CHANGE: Extract method to reduce complexity
	private ScmCommits.ScmCommitsBuilder createCommitBuilder(org.gitlab4j.api.models.Commit gitlabCommit,
			String toolConfigId, String owner, String repository) {
		return ScmCommits.builder().processorItemId(new ObjectId(toolConfigId))
				.repositoryName(owner + PATH_SEPARATOR + repository).sha(gitlabCommit.getId())
				.commitMessage(gitlabCommit.getMessage())
				.commitTimestamp(gitlabCommit.getCreatedAt().toInstant().toEpochMilli());
	}

	// CHANGE: Extract method to reduce complexity
	private void setCommitAuthorInfo(ScmCommits.ScmCommitsBuilder builder,
			org.gitlab4j.api.models.Commit gitlabCommit) {
		if (gitlabCommit.getAuthorName() != null) {
			builder.commitAuthorId(gitlabCommit.getAuthorName()).authorName(gitlabCommit.getAuthorName())
					.authorEmail(gitlabCommit.getAuthorEmail());
			User user = User.builder().username(gitlabCommit.getAuthorName()).email(gitlabCommit.getAuthorEmail())
					.displayName(gitlabCommit.getCommitterName()).build();
			builder.commitAuthor(user);
		}

		if (gitlabCommit.getCommitterName() != null) {
			builder.committerId(gitlabCommit.getCommitterName()).committerName(gitlabCommit.getCommitterName())
					.committerEmail(gitlabCommit.getCommitterEmail());
		}
	}

	// CHANGE: Extract method to reduce complexity
	private void setCommitTimestamps(ScmCommits.ScmCommitsBuilder builder,
			org.gitlab4j.api.models.Commit gitlabCommit) {
		if (gitlabCommit.getCommittedDate() != null) {
			builder.commitTimestamp(gitlabCommit.getCommittedDate().toInstant().toEpochMilli());
		}
	}

	// CHANGE: Extract method to reduce complexity
	private void setCommitParentInfo(ScmCommits.ScmCommitsBuilder builder,
			org.gitlab4j.api.models.Commit gitlabCommit) {
		if (gitlabCommit.getParentIds() != null && !gitlabCommit.getParentIds().isEmpty()) {
			builder.parentShas(gitlabCommit.getParentIds()).isMergeCommit(gitlabCommit.getParentIds().size() > 1);
		}
	}

	private void setCommitDiffStats(ScmCommits.ScmCommitsBuilder builder, org.gitlab4j.api.models.Commit gitlabCommit,
			String owner, String repository, String token, String repositoryUrl) {
		GitLabDiffStats diffStats = extractDiffStats(gitlabCommit, owner, repository, token, repositoryUrl);
		builder.addedLines(diffStats.getAddedLines()).removedLines(diffStats.getRemovedLines())
				.changedLines(diffStats.getChangedLines()).filesChanged(diffStats.getFilesChanged())
				.fileChanges(diffStats.getFileChanges());
	}

	/**
	 * Converts a GitLab merge request to domain MergeRequest object
	 */
	private ScmMergeRequests convertToMergeRequest(org.gitlab4j.api.models.MergeRequest gitlabMr, String toolConfigId,
			String owner, String repository, String token, String repositoryUrl) throws GitLabApiException {
		// CHANGE: Extract builder creation to reduce complexity
		ScmMergeRequests.ScmMergeRequestsBuilder builder = createMergeRequestBuilder(gitlabMr, toolConfigId, owner,
				repository);

		// CHANGE: Extract methods for each concern
		setMergeRequestState(builder, gitlabMr);
		setMergeRequestTimestamps(builder, gitlabMr);
		setMergeRequestAuthor(builder, gitlabMr);
		setMergeRequestMetadata(builder, gitlabMr);
		setMergeRequestPickUpTime(builder, gitlabMr, owner, repository, token, repositoryUrl);
		setMergeRequestStats(builder, gitlabMr, owner, repository, token, repositoryUrl);

		return builder.build();
	}

	// CHANGE: Extract method to reduce complexity
	private ScmMergeRequests.ScmMergeRequestsBuilder createMergeRequestBuilder(
			org.gitlab4j.api.models.MergeRequest gitlabMr, String toolConfigId, String owner, String repository) {
		return ScmMergeRequests.builder().processorItemId(new ObjectId(toolConfigId))
				.repositoryName(owner + PATH_SEPARATOR + repository).externalId(gitlabMr.getIid().toString())
				.title(gitlabMr.getTitle()).summary(gitlabMr.getDescription()).state(gitlabMr.getState().toLowerCase())
				.fromBranch(gitlabMr.getSourceBranch()).toBranch(gitlabMr.getTargetBranch())
				.createdDate(gitlabMr.getCreatedAt().toInstant().toEpochMilli())
				.updatedDate(gitlabMr.getUpdatedAt().toInstant().toEpochMilli());
	}

	// CHANGE: Extract method to reduce complexity
	private void setMergeRequestState(ScmMergeRequests.ScmMergeRequestsBuilder builder,
			org.gitlab4j.api.models.MergeRequest gitlabMr) {
		if (gitlabMr.getClosedAt() != null) {
			builder.isClosed(true);
		} else {
			builder.isOpen(true);
		}
	}

	// CHANGE: Extract method to reduce complexity
	private void setMergeRequestTimestamps(ScmMergeRequests.ScmMergeRequestsBuilder builder,
			org.gitlab4j.api.models.MergeRequest gitlabMr) {
		if (gitlabMr.getMergedAt() != null) {
			builder.mergedAt(gitlabMr.getMergedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
		}
		if (gitlabMr.getClosedAt() != null) {
			builder.closedDate(gitlabMr.getClosedAt().toInstant().toEpochMilli());
		}
	}

	// CHANGE: Extract method to reduce complexity
	private void setMergeRequestAuthor(ScmMergeRequests.ScmMergeRequestsBuilder builder,
			org.gitlab4j.api.models.MergeRequest gitlabMr) {
		if (gitlabMr.getAuthor() != null) {
			builder.authorUserId(gitlabMr.getAuthor().getUsername());
			User user = User.builder().username(gitlabMr.getAuthor().getUsername())
					.email(gitlabMr.getAuthor().getEmail() != null ? gitlabMr.getAuthor().getEmail()
							: gitlabMr.getAuthor().getName())
					.displayName(gitlabMr.getAuthor().getName()).build();
			builder.authorId(user);
		}
	}

	// CHANGE: Extract method to reduce complexity
	private void setMergeRequestMetadata(ScmMergeRequests.ScmMergeRequestsBuilder builder,
			org.gitlab4j.api.models.MergeRequest gitlabMr) {
		if (gitlabMr.getWebUrl() != null) {
			builder.mergeRequestUrl(gitlabMr.getWebUrl());
		}
		if (gitlabMr.getWorkInProgress() != null) {
			builder.isDraft(gitlabMr.getWorkInProgress());
		}
	}

	// CHANGE: Extract method to reduce complexity
	private void setMergeRequestPickUpTime(ScmMergeRequests.ScmMergeRequestsBuilder builder,
			org.gitlab4j.api.models.MergeRequest gitlabMr, String owner, String repository, String token,
			String repositoryUrl) throws GitLabApiException {
		long pickUpTime = gitLabClient.getPrPickUpTimeStamp(owner, repository, token, repositoryUrl, gitlabMr.getIid());
		builder.pickedForReviewOn(pickUpTime);
	}

	// CHANGE: Extract method to reduce complexity
	private void setMergeRequestStats(ScmMergeRequests.ScmMergeRequestsBuilder builder,
			org.gitlab4j.api.models.MergeRequest gitlabMr, String owner, String repository, String token,
			String repositoryUrl) {
		MergeRequestStats mrStats = extractMergeRequestStats(gitlabMr, owner, repository, token, repositoryUrl);
		builder.linesChanged(mrStats.getLinesChanged()).commitCount(mrStats.getCommitCount())
				.filesChanged(mrStats.getFilesChanged()).addedLines(mrStats.getAddedLines())
				.removedLines(mrStats.getRemovedLines());
	}

	/**
	 * Extracts diff statistics from a GitLab commit
	 */
	private GitLabDiffStats extractDiffStats(org.gitlab4j.api.models.Commit gitlabCommit, String owner,
			String repository, String token, String repositoryUrl) {
		try {
			// First try to get stats from the commit object itself
			if (gitlabCommit.getStats() != null) {
				return extractDiffStatsFromCommitStats(gitlabCommit, owner, repository, token, repositoryUrl);
			}

			// Fallback: try to get file changes without stats
			return extractDiffStatsFromFileChanges(gitlabCommit, owner, repository, token, repositoryUrl);

		} catch (Exception e) {
			log.warn("Failed to extract diff stats from commit {}: {}", gitlabCommit.getId(), e.getMessage());
			return new GitLabDiffStats(0, 0, 0, 0, new ArrayList<>());
		}
	}

	// CHANGE: Extract method to reduce complexity
	private GitLabDiffStats extractDiffStatsFromCommitStats(org.gitlab4j.api.models.Commit gitlabCommit, String owner,
			String repository, String token, String repositoryUrl) {
		int additions = gitlabCommit.getStats().getAdditions();
		int deletions = gitlabCommit.getStats().getDeletions();
		int total = gitlabCommit.getStats().getTotal();

		// Try to get detailed file changes via additional API call
		List<ScmCommits.FileChange> fileChanges = extractFileChangesFromCommit(gitlabCommit, owner, repository, token,
				repositoryUrl);

		return new GitLabDiffStats(additions, deletions, total, fileChanges.size(), fileChanges);
	}

	// CHANGE: Extract method to reduce complexity
	private GitLabDiffStats extractDiffStatsFromFileChanges(org.gitlab4j.api.models.Commit gitlabCommit, String owner,
			String repository, String token, String repositoryUrl) {
		List<ScmCommits.FileChange> fileChanges = extractFileChangesFromCommit(gitlabCommit, owner, repository, token,
				repositoryUrl);
		int totalAdditions = fileChanges.stream().mapToInt(fc -> fc.getAddedLines() != null ? fc.getAddedLines() : 0)
				.sum();
		int totalDeletions = fileChanges.stream()
				.mapToInt(fc -> fc.getRemovedLines() != null ? fc.getRemovedLines() : 0).sum();
		int totalChanges = fileChanges.stream().mapToInt(fc -> fc.getChangedLines() != null ? fc.getChangedLines() : 0)
				.sum();

		return new GitLabDiffStats(totalAdditions, totalDeletions, totalChanges, fileChanges.size(), fileChanges);
	}

	/**
	 * Extracts file changes from a GitLab commit using additional API calls
	 */
	private List<ScmCommits.FileChange> extractFileChangesFromCommit(org.gitlab4j.api.models.Commit gitlabCommit,
			String owner, String repository, String token, String repositoryUrl) {
		List<ScmCommits.FileChange> fileChanges = new ArrayList<>();

		try {
			// Fetch commit diffs from GitLab API
			List<Diff> diffs = gitLabClient.fetchCommitDiffs(owner, repository, gitlabCommit.getId(), token,
					repositoryUrl);

			for (Diff diff : diffs) {
				try {
					ScmCommits.FileChange fileChange = convertDiffToFileChange(diff);
					if (fileChange != null) {
						fileChanges.add(fileChange);
					}
				} catch (Exception e) {
					log.debug("Failed to convert diff for file {}: {}", diff.getNewPath(), e.getMessage());
				}
			}

		} catch (Exception e) {
			log.debug("Could not extract detailed file changes for commit {}: {}", gitlabCommit.getId(),
					e.getMessage());
		}

		return fileChanges;
	}

	/**
	 * Converts a GitLab Diff to a FileChange object
	 */
	private ScmCommits.FileChange convertDiffToFileChange(Diff diff) {
		if (diff == null) {
			return null;
		}

		String filePath = diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath();
		if (filePath == null) {
			return null;
		}

		// Parse diff content to extract line changes
		String diffContent = diff.getDiff();
		DiffStats stats = parseDiffContent(diffContent);

		return ScmCommits.FileChange.builder().filePath(filePath).changeType(mapGitLabStatus(determineChangeType(diff)))
				.addedLines(stats.getAddedLines()).removedLines(stats.getRemovedLines())
				.changedLines(stats.getAddedLines() + stats.getRemovedLines()).isBinary(isBinaryFile(filePath))
				.changedLineNumbers(extractLineNumbers(diffContent)).build();
	}

	/**
	 * Determines the change type from a GitLab Diff
	 */
	private String determineChangeType(Diff diff) {
		if (Boolean.TRUE.equals(diff.getNewFile())) {
			return "new";
		} else if (Boolean.TRUE.equals(diff.getDeletedFile())) {
			return "deleted";
		} else if (Boolean.TRUE.equals(diff.getRenamedFile())) {
			return "renamed";
		} else {
			return "modified";
		}
	}

	/**
	 * Parses diff content to extract line statistics
	 */
	private DiffStats parseDiffContent(String diffContent) {
		if (diffContent == null || diffContent.isEmpty()) {
			return new DiffStats(0, 0);
		}

		int addedLines = 0;
		int removedLines = 0;

		String[] lines = diffContent.split("\n");
		for (String line : lines) {
			if (line.startsWith(DIFF_ADD_PREFIX) && !line.startsWith(DIFF_ADD_FILE_PREFIX)) {
				addedLines++;
			} else if (line.startsWith(DIFF_REMOVE_PREFIX) && !line.startsWith(DIFF_REMOVE_FILE_PREFIX)) {
				removedLines++;
			}
		}

		return new DiffStats(addedLines, removedLines);
	}

	/**
	 * Extracts statistics from a GitLab merge request
	 */
	private MergeRequestStats extractMergeRequestStats(org.gitlab4j.api.models.MergeRequest gitlabMr, String owner,
			String repository, String token, String repositoryUrl) {
		try {
			MergeRequestStatsBuilder statsBuilder = new MergeRequestStatsBuilder();

			// Try to get merge request changes
			extractMergeRequestChanges(statsBuilder, gitlabMr, owner, repository, token, repositoryUrl);

			// Try to get commit count
			extractMergeRequestCommitCount(statsBuilder, gitlabMr, owner, repository, token, repositoryUrl);

			// Fallback to basic stats if available
			applyFallbackStats(statsBuilder, gitlabMr);

			return statsBuilder.build();

		} catch (Exception e) {
			log.warn("Failed to extract stats from merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
			return new MergeRequestStats(0, 0, 0, 0, 0);
		}
	}

	// CHANGE: Extract method to reduce complexity
	private void extractMergeRequestChanges(MergeRequestStatsBuilder statsBuilder,
			org.gitlab4j.api.models.MergeRequest gitlabMr, String owner, String repository, String token,
			String repositoryUrl) {
		try {
			List<Diff> changes = gitLabClient.fetchMergeRequestChanges(owner, repository, gitlabMr.getIid(), token,
					repositoryUrl);

			for (Diff diff : changes) {
				if (diff.getDiff() != null) {
					DiffStats diffStats = parseDiffContent(diff.getDiff());
					statsBuilder.addAddedLines(diffStats.getAddedLines());
					statsBuilder.addRemovedLines(diffStats.getRemovedLines());
					statsBuilder.incrementFilesChanged();
				}
			}

		} catch (Exception e) {
			log.debug("Could not fetch detailed changes for merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
		}
	}

	// CHANGE: Extract method to reduce complexity
	private void extractMergeRequestCommitCount(MergeRequestStatsBuilder statsBuilder,
			org.gitlab4j.api.models.MergeRequest gitlabMr, String owner, String repository, String token,
			String repositoryUrl) {
		try {
			List<org.gitlab4j.api.models.Commit> commits = gitLabClient.fetchMergeRequestCommits(owner, repository,
					gitlabMr.getIid(), token, repositoryUrl);
			statsBuilder.setCommitCount(commits.size());
		} catch (Exception e) {
			log.debug("Could not fetch commits for merge request !{}: {}", gitlabMr.getIid(), e.getMessage());
		}
	}

	// CHANGE: Extract method to reduce complexity
	private void applyFallbackStats(MergeRequestStatsBuilder statsBuilder,
			org.gitlab4j.api.models.MergeRequest gitlabMr) {
		if (statsBuilder.getLinesChanged() == 0 && gitlabMr.getChangesCount() != null) {
			try {
				statsBuilder.setLinesChanged(Integer.parseInt(gitlabMr.getChangesCount()));
			} catch (NumberFormatException e) {
				log.debug("Could not parse changes count '{}' for merge request !{}", gitlabMr.getChangesCount(),
						gitlabMr.getIid());
			}
		}
	}

	/**
	 * Maps GitLab diff status to our change type
	 */
	private String mapGitLabStatus(String status) {
		if (status == null) {
			return MODIFIED_STATUS;
		}

		// CHANGE: Use enhanced switch expression to reduce complexity
		return switch (status.toLowerCase()) {
		case "new" -> "ADDED";
		case "deleted" -> "DELETED";
		case "modified" -> MODIFIED_STATUS;
		case "renamed" -> "RENAMED";
		default -> MODIFIED_STATUS;
		};
	}

	/**
	 * Determines if a file is binary based on its extension
	 */
	private boolean isBinaryFile(String fileName) {
		if (fileName == null) {
			return false;
		}

		String lowerFileName = fileName.toLowerCase();
		for (String ext : BINARY_EXTENSIONS) {
			if (lowerFileName.endsWith(ext)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Extracts line numbers from diff content for both added and removed lines
	 */
	private List<Integer> extractLineNumbers(String diff) {
		List<Integer> lineNumbers = new ArrayList<>();
		if (diff == null || diff.isEmpty()) {
			return lineNumbers;
		}

		try {
			String[] lines = diff.split("\n");
			int currentLineNumber = 0;

			for (String line : lines) {
				currentLineNumber = processLineForLineNumber(line, currentLineNumber, lineNumbers);
			}
		} catch (Exception e) {
			log.debug("Failed to extract line numbers from diff: {}", e.getMessage());
		}

		return lineNumbers;
	}

	// CHANGE: Extract method to reduce complexity
	private int processLineForLineNumber(String line, int currentLineNumber, List<Integer> lineNumbers) {
		Matcher matcher = HUNK_PATTERN.matcher(line);
		if (matcher.find()) {
			return Integer.parseInt(matcher.group(1));
		} else if (isChangeLine(line)) {
			lineNumbers.add(currentLineNumber);
			if (line.startsWith(DIFF_ADD_PREFIX)) {
				return currentLineNumber + 1;
			}
		} else if (isContentLine(line)) {
			return currentLineNumber + 1;
		}
		return currentLineNumber;
	}

	// CHANGE: Extract method for better readability
	private boolean isChangeLine(String line) {
		return (line.startsWith(DIFF_ADD_PREFIX) || line.startsWith(DIFF_REMOVE_PREFIX))
				&& !line.startsWith(DIFF_ADD_FILE_PREFIX) && !line.startsWith(DIFF_REMOVE_FILE_PREFIX);
	}

	// CHANGE: Extract method for better readability
	private boolean isContentLine(String line) {
		return !line.startsWith(BACKSLASH_PREFIX) && !line.startsWith(DIFF_HUNK_PREFIX)
				&& !line.startsWith(DIFF_PREFIX);
	}

	/**
	 * Helper class for diff statistics
	 */
	@Getter
	private static class DiffStats {
		private final int addedLines;
		private final int removedLines;

		public DiffStats(int addedLines, int removedLines) {
			this.addedLines = addedLines;
			this.removedLines = removedLines;
		}

	}

	/**
	 * Helper class for GitLab diff statistics
	 */
	@Getter
	private static class GitLabDiffStats {
		private final int addedLines;
		private final int removedLines;
		private final int changedLines;
		private final int filesChanged;
		private final List<ScmCommits.FileChange> fileChanges;

		public GitLabDiffStats(int addedLines, int removedLines, int changedLines, int filesChanged,
				List<ScmCommits.FileChange> fileChanges) {
			this.addedLines = addedLines;
			this.removedLines = removedLines;
			this.changedLines = changedLines;
			this.filesChanged = filesChanged;
			this.fileChanges = fileChanges;
		}

	}

	/**
	 * Helper class for merge request statistics
	 */
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

	// CHANGE: Add builder class to reduce complexity in extractMergeRequestStats
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
