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

package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.knowhow.processor.scm.client.github.GitHubClient;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.kohsuke.github.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub implementation of GitPlatformService. This service focuses on business
 * logic and data transformation, delegating all GitHub API interactions to
 * GitHubClient.
 */
@Service
@Slf4j
public class GitHubService implements GitPlatformService {

	private static final String DEFAULT_BRANCH_NAME = "main";
	private static final String MODIFIED_STATUS = "MODIFIED";
	private static final String PLATFORM_NAME = "GitHub";
	private static final Set<String> BINARY_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".ico", ".svg",
			".pdf", ".zip", ".tar", ".gz", ".rar", ".7z", ".exe", ".dll", ".so", ".dylib", ".class", ".jar");
	private static final Pattern HUNK_PATTERN = Pattern.compile("@@\\s+-\\d+(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@");

	private final GitHubClient gitHubClient;

	public GitHubService(GitHubClient gitHubClient) {
		this.gitHubClient = gitHubClient;
	}

	@Override
	public List<ScmCommits> fetchCommits(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
			String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
		try {
			log.info("Fetching commits for GitHub repository: {}/{}", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName());
			String owner = gitUrlInfo.getOrganization() != null ? gitUrlInfo.getOrganization() : gitUrlInfo.getOwner();
			List<GHCommit> ghCommits = gitHubClient.fetchCommits(owner, gitUrlInfo.getRepositoryName(), branchName,
					token, since, until);
			List<ScmCommits> commitDetails = new ArrayList<>();

			for (GHCommit ghCommit : ghCommits) {
				processCommit(ghCommit, toolConfigId, owner, gitUrlInfo.getRepositoryName(), commitDetails);
			}

			log.info("Successfully converted {} GitHub commits to domain objects", commitDetails.size());
			return commitDetails;

		} catch (IOException e) {
			log.error("Failed to fetch commits from GitHub repository {}/{}: {}", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), e.getMessage());
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch commits from GitHub", e);
		}
	}

	/**
	 * Processes a single GitHub commit and adds it to the commit details list.
	 * Handles conversion errors gracefully by logging warnings.
	 *
	 * @param ghCommit
	 *            The GitHub commit to process
	 * @param toolConfigId
	 *            The tool configuration ID
	 * @param owner
	 *            The repository owner
	 * @param repositoryName
	 *            The repository name
	 * @param commitDetails
	 *            The list to add the converted commit to
	 */
	private void processCommit(GHCommit ghCommit, String toolConfigId, String owner, String repositoryName,
			List<ScmCommits> commitDetails) {
		try {
			ScmCommits commitDetail = convertToCommit(ghCommit, toolConfigId, owner, repositoryName);
			commitDetails.add(commitDetail);
		} catch (Exception e) {
			log.warn("Failed to convert GitHub commit {}: {}", ghCommit.getSHA1(), e.getMessage());
		}
	}

	@Override
	public List<ScmMergeRequests> fetchMergeRequests(String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo,
			String branchName, String token, LocalDateTime since, LocalDateTime until) throws PlatformApiException {
		try {
			log.info("Fetching merge requests for GitHub repository: {}/{} (branch: {})", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), branchName != null ? branchName : "all");

			List<GHPullRequest> ghPullRequests = gitHubClient.fetchPullRequests(gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), token, since, until);
			List<ScmMergeRequests> mergeRequests = new ArrayList<>();

			// Filter by target branch if specified
			if (branchName != null && !branchName.trim().isEmpty()) {
				ghPullRequests = filterPullRequestsByBranch(ghPullRequests, branchName);
			}

			for (GHPullRequest ghPr : ghPullRequests) {
				processPullRequest(ghPr, toolConfigId, mergeRequests);
			}

			log.info("Successfully converted {} GitHub pull requests to domain objects", mergeRequests.size());
			return mergeRequests;

		} catch (IOException e) {
			log.error("Failed to fetch merge requests from GitHub repository {}/{}: {}", gitUrlInfo.getOwner(),
					gitUrlInfo.getRepositoryName(), e.getMessage());
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch merge requests from GitHub", e);
		}
	}

	/**
	 * Processes a single GitHub pull request and adds it to the merge requests
	 * list. Handles conversion errors gracefully by logging warnings.
	 *
	 * @param ghPr
	 *            The GitHub pull request to process
	 * @param toolConfigId
	 *            The tool configuration ID
	 * @param mergeRequests
	 *            The list to add the converted merge request to
	 */
	private void processPullRequest(GHPullRequest ghPr, String toolConfigId, List<ScmMergeRequests> mergeRequests) {
		try {
			ScmMergeRequests mergeRequest = convertToMergeRequest(ghPr, toolConfigId);
			mergeRequests.add(mergeRequest);
		} catch (Exception e) {
			log.warn("Failed to convert GitHub pull request #{}: {}", ghPr.getNumber(), e.getMessage());
		}
	}

	@Override
	public String getPlatformName() {
		return PLATFORM_NAME;
	}

	/**
	 * Converts a GitHub commit to domain Commit object
	 */
	private ScmCommits convertToCommit(GHCommit ghCommit, String toolConfigId, String owner, String repository)
			throws IOException {
		ScmCommits.ScmCommitsBuilder builder = ScmCommits.builder().processorItemId(new ObjectId(toolConfigId))
				.repositoryName(owner + "/" + repository).sha(ghCommit.getSHA1())
				.commitMessage(ghCommit.getCommitShortInfo().getMessage())
				.commitTimestamp(ghCommit.getCommitDate().toInstant().toEpochMilli());

		setCommitAuthor(builder, ghCommit);

		builder.branchName(DEFAULT_BRANCH_NAME);

		// Extract diff statistics and file changes
		GitHubDiffStats diffStats = extractDiffStats(ghCommit);
		builder.addedLines(diffStats.getAddedLines()).removedLines(diffStats.getRemovedLines())
				.changedLines(diffStats.getChangedLines()).filesChanged(diffStats.getFilesChanged())
				.fileChanges(diffStats.getFileChanges());

		setParentInformation(builder, ghCommit);

		return builder.build();
	}

	/**
	 * Converts a GitHub pull request to domain MergeRequest object
	 */
	private ScmMergeRequests convertToMergeRequest(GHPullRequest ghPr, String toolConfigId) throws IOException {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder()
				.processorItemId(new ObjectId(toolConfigId)).repositoryName(ghPr.getRepository().getFullName())
				.externalId(String.valueOf(ghPr.getNumber())).title(ghPr.getTitle()).summary(ghPr.getBody())
				.fromBranch(ghPr.getHead().getRef()).toBranch(ghPr.getBase().getRef())
				.createdDate(ghPr.getCreatedAt() != null ? ghPr.getCreatedAt().toInstant().toEpochMilli() : null)
				.updatedDate(ghPr.getUpdatedAt().toInstant().toEpochMilli());

		setPullRequestState(builder, ghPr);

		// Set merge/close timestamps
		setMergeAndCloseTimestamps(builder, ghPr);

		setPullRequestAuthor(builder, ghPr);

		// Set merge request URL
		builder.mergeRequestUrl(ghPr.getHtmlUrl().toString());

		// Extract pull request statistics
		PullRequestStats prStats = extractPullRequestStats(ghPr);
		builder.linesChanged(prStats.getLinesChanged()).commitCount(prStats.getCommitCount())
				.filesChanged(prStats.getFilesChanged()).addedLines(prStats.getAddedLines())
				.removedLines(prStats.getRemovedLines());

		builder.pickedForReviewOn(getPrPickupTime(ghPr));

		return builder.build();
	}

	private List<GHPullRequest> filterPullRequestsByBranch(List<GHPullRequest> pullRequests, String branchName) {
		return pullRequests.stream().filter(pr -> {
			try {
				String baseBranch = pr.getBase().getRef();
				return baseBranch != null && baseBranch.equals(branchName);
			} catch (Exception e) {
				log.warn("Failed to get base branch for PR #{}: {}", pr.getNumber(), e.getMessage());
				return false;
			}
		}).toList();
	}

	private void setCommitAuthor(ScmCommits.ScmCommitsBuilder builder, GHCommit ghCommit) throws IOException {
		GHUser user = ghCommit.getAuthor() != null ? ghCommit.getAuthor() : ghCommit.getCommitter();

		if (user != null) {
			User commitUser = createUser(user);
			builder.commitAuthor(commitUser).authorName(user.getLogin());
		}
	}

	private User createUser(GHUser ghUser) throws IOException {
		return User.builder().username(ghUser.getLogin())
				.displayName(ghUser.getName() != null ? ghUser.getName() : ghUser.getLogin()).email(ghUser.getEmail())
				.build();
	}

	private void setParentInformation(ScmCommits.ScmCommitsBuilder builder, GHCommit ghCommit) {
		try {
			List<String> parentShas = new ArrayList<>(ghCommit.getParentSHA1s());
			builder.parentShas(parentShas).isMergeCommit(parentShas.size() > 1);
		} catch (Exception e) {
			log.debug("Could not get parent SHAs for commit {}", ghCommit.getSHA1());
		}
	}

	private void setPullRequestState(ScmMergeRequests.ScmMergeRequestsBuilder builder, GHPullRequest ghPr) {
		if (ghPr.getState().name().equalsIgnoreCase(ScmMergeRequests.MergeRequestState.CLOSED.name())) {
			builder.state((ghPr.getMergedAt() != null) ? ScmMergeRequests.MergeRequestState.MERGED.name()
					: ScmMergeRequests.MergeRequestState.CLOSED.name());
			builder.isClosed(true);
		} else {
			builder.state(ScmMergeRequests.MergeRequestState.OPEN.name());
			builder.isOpen(true);
		}
	}

	private void setMergeAndCloseTimestamps(ScmMergeRequests.ScmMergeRequestsBuilder builder, GHPullRequest ghPr) {
		if (ghPr.getMergedAt() != null) {
			builder.mergedAt(ghPr.getMergedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
		}
		if (ghPr.getClosedAt() != null) {
			builder.closedDate(ghPr.getClosedAt().toInstant().toEpochMilli());
		}
	}

	private void setPullRequestAuthor(ScmMergeRequests.ScmMergeRequestsBuilder builder, GHPullRequest ghPr)
			throws IOException {
		if (ghPr.getUser() != null) {
			User author = createUser(ghPr.getUser());
			builder.authorId(author);
			builder.authorUserId(ghPr.getUser().getLogin());
		}
	}

	/**
	 * Extracts diff statistics from a GitHub commit
	 */
	private GitHubDiffStats extractDiffStats(GHCommit ghCommit) {
		try {
			// Get commit files to extract detailed diff information
			List<GHCommit.File> files = ghCommit.getFiles();

			int totalAdditions = 0;
			int totalDeletions = 0;
			int totalChanges = 0;
			List<ScmCommits.FileChange> fileChanges = new ArrayList<>();

			for (GHCommit.File file : files) {
				FileChangeStats stats = processFileChange(file);

				totalAdditions += stats.additions;
				totalDeletions += stats.deletions;
				totalChanges += stats.changes;
				fileChanges.add(stats.fileChange);
			}

			return new GitHubDiffStats(totalAdditions, totalDeletions, totalChanges, files.size(), fileChanges);

		} catch (Exception e) {
			log.warn("Failed to extract diff stats from commit {}: {}", ghCommit.getSHA1(), e.getMessage());
			return new GitHubDiffStats(0, 0, 0, 0, new ArrayList<>());
		}
	}

	private FileChangeStats processFileChange(GHCommit.File file) {
		int additions = file.getLinesAdded();
		int deletions = file.getLinesDeleted();
		int changes = additions + deletions;

		ScmCommits.FileChange fileChange = ScmCommits.FileChange.builder().filePath(file.getFileName())
				.addedLines(additions).removedLines(deletions).changedLines(changes)
				.changeType(mapGitHubStatus(file.getStatus())).previousPath(file.getPreviousFilename())
				.isBinary(isBinaryFile(file.getFileName())).changedLineNumbers(extractLineNumbers(file.getPatch()))
				.build();

		return new FileChangeStats(additions, deletions, changes, fileChange);
	}

	private static class FileChangeStats {
		final int additions;
		final int deletions;
		final int changes;
		final ScmCommits.FileChange fileChange;

		FileChangeStats(int additions, int deletions, int changes, ScmCommits.FileChange fileChange) {
			this.additions = additions;
			this.deletions = deletions;
			this.changes = changes;
			this.fileChange = fileChange;
		}
	}

	public Long getPrPickupTime(GHPullRequest ghPr) throws IOException {
		Set<String> reviewActivities = Set.of(GHPullRequestReviewState.APPROVED.name(),
				GHPullRequestReviewState.COMMENTED.name(), GHPullRequestReviewState.CHANGES_REQUESTED.name(),
				GHPullRequestReviewState.DISMISSED.name());

		Date pickedForReviewOn = null;
		for (GHPullRequestReview requestReview : ghPr.listReviews()) {
			GHPullRequestReviewState state = requestReview.getState();
			if (state != null && reviewActivities.contains(state.name())) {
				Date reviewTime = requestReview.getSubmittedAt();
				if (reviewTime != null && (pickedForReviewOn == null || reviewTime.before(pickedForReviewOn))) {
					pickedForReviewOn = reviewTime;
				}
			}
		}
		return pickedForReviewOn == null ? null : pickedForReviewOn.toInstant().toEpochMilli();
	}

	/**
	 * Maps GitHub file status to our change type
	 */
	private String mapGitHubStatus(String status) {
		if (status == null)
			return MODIFIED_STATUS;

		return switch (status.toLowerCase()) {
		case "added" -> "ADDED";
		case "removed" -> "DELETED";
		case "renamed" -> "RENAMED";
		default -> MODIFIED_STATUS;
		};
	}

	/**
	 * Determines if a file is binary based on its extension
	 */
	private boolean isBinaryFile(String fileName) {
		if (fileName == null)
			return false;

		String lowerFileName = fileName.toLowerCase();
		return BINARY_EXTENSIONS.stream().anyMatch(lowerFileName::endsWith);
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
				Matcher matcher = HUNK_PATTERN.matcher(line);
				if (matcher.find()) {
					currentLineNumber = Integer.parseInt(matcher.group(1));
				} else if (isChangeLine(line)) {
					lineNumbers.add(currentLineNumber);
					if (line.startsWith("+")) {
						currentLineNumber++;
					}
				} else if (isContentLine(line)) {
					currentLineNumber++;
				}
			}
		} catch (Exception e) {
			log.debug("Failed to extract line numbers from diff: {}", e.getMessage());
		}

		return lineNumbers;
	}

	private boolean isChangeLine(String line) {
		return (line.startsWith("+") || line.startsWith("-")) && !line.startsWith("+++") && !line.startsWith("---");
	}

	private boolean isContentLine(String line) {
		return !line.startsWith("\\") && !line.startsWith("@@") && !line.startsWith("diff");
	}

	/**
	 * Extracts statistics from a GitHub pull request
	 */
	private PullRequestStats extractPullRequestStats(GHPullRequest ghPr) {
		try {
			return new PullRequestStats(ghPr.getAdditions() + ghPr.getDeletions(), ghPr.getCommits(),
					ghPr.getChangedFiles(), ghPr.getAdditions(), ghPr.getDeletions());
		} catch (Exception e) {
			log.warn("Failed to extract stats from pull request #{}: {}", ghPr.getNumber(), e.getMessage());
			return new PullRequestStats(0, 0, 0, 0, 0);
		}
	}

	/**
	 * Helper class for GitHub diff statistics
	 */
	@Getter
	private static class GitHubDiffStats {
		private final int addedLines;
		private final int removedLines;
		private final int changedLines;
		private final int filesChanged;
		private final List<ScmCommits.FileChange> fileChanges;

		public GitHubDiffStats(int addedLines, int removedLines, int changedLines, int filesChanged,
				List<ScmCommits.FileChange> fileChanges) {
			this.addedLines = addedLines;
			this.removedLines = removedLines;
			this.changedLines = changedLines;
			this.filesChanged = filesChanged;
			this.fileChanges = fileChanges;
		}

	}

	/**
	 * Helper class for pull request statistics
	 */
	@Getter
	private static class PullRequestStats {
		private final int linesChanged;
		private final int commitCount;
		private final int filesChanged;
		private final int addedLines;
		private final int removedLines;

		public PullRequestStats(int linesChanged, int commitCount, int filesChanged, int addedLines, int removedLines) {
			this.linesChanged = linesChanged;
			this.commitCount = commitCount;
			this.filesChanged = filesChanged;
			this.addedLines = addedLines;
			this.removedLines = removedLines;
		}
	}
}
