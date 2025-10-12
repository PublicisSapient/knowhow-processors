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

package com.publicissapient.knowhow.processor.scm.service.strategy;

import com.publicissapient.knowhow.processor.scm.config.GitScannerConfig;
import com.publicissapient.knowhow.processor.scm.exception.GitScannerException;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.User;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JGit-based implementation of CommitDataFetchStrategy. This strategy fetches
 * commit data by cloning the repository locally using JGit and then extracting
 * commit information from the local clone. Advantages: - Complete commit
 * history access - No API rate limiting - Works with any Git repository
 * Disadvantages: - Requires local disk space - Slower for large repositories -
 * Network bandwidth for cloning
 */
@Component("jGitCommitDataFetchStrategy")
@Slf4j
public class JGitCommitDataFetchStrategy implements CommitDataFetchStrategy {

	private static final String TEMP_DIR_PREFIX = "git-scanner-";
	private static final String ORIGIN_REF_PREFIX = "refs/remotes/origin/";
	private static final int SECONDS_TO_MINUTES = 60;
	private static final int MERGE_COMMIT_PARENT_COUNT = 2;
	private static final int LINE_NUMBER_OFFSET = 1;
	private static final String EMPTY_TREE_HASH = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";

	private final GitUrlParser gitUrlParser;
	private final GitScannerConfig gitScannerConfig;

	public JGitCommitDataFetchStrategy(GitUrlParser gitUrlParser, GitScannerConfig gitScannerConfig) {
		this.gitUrlParser = gitUrlParser;
		this.gitScannerConfig = gitScannerConfig;
	}

	@Override
	public List<ScmCommits> fetchCommits(String toolType, String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo,
			String branchName, RepositoryCredentials credentials, java.time.LocalDateTime since)
			throws DataProcessingException {

		String repositoryUrl = gitUrlInfo.getOriginalUrl();
		log.info("Fetching commits using JGit strategy for repository: {} of tool: {}", repositoryUrl, toolType);

		Path tempDir = null;
		Git git = null;
		try {
			tempDir = createTempDirectory();
			git = cloneRepository(repositoryUrl, tempDir, credentials);
			List<ScmCommits> commitDetails = extractCommits(git, toolConfigId, branchName, since);

			log.info("Successfully fetched {} commits from repository: {}", commitDetails.size(), repositoryUrl);
			return commitDetails;

		} catch (GitAPIException e) {
			// CHANGE: Specific exception handling for Git operations
			log.error("Git operation failed for repository {}: {}", repositoryUrl, e.getMessage(), e);
			throw new DataProcessingException("Failed to perform Git operation", e);
		} catch (IOException e) {
			// CHANGE: Specific exception handling for IO operations
			log.error("IO error while processing repository {}: {}", repositoryUrl, e.getMessage(), e);
			throw new DataProcessingException("Failed to access repository files", e);
		} catch (Exception e) {
			// CHANGE: Catch-all for unexpected exceptions
			log.error("Unexpected error fetching commits from repository {}: {}", repositoryUrl, e.getMessage(), e);
			throw new DataProcessingException("Failed to fetch commits using JGit strategy", e);
		} finally {
			closeGitResources(git);
			if (tempDir != null) {
				cleanupTempDirectory(tempDir);
			}
		}
	}

	@Override
	public boolean supports(String repositoryUrl, String toolType) {
		return gitUrlParser.isValidGitUrl(repositoryUrl, toolType);
	}

	@Override
	public String getStrategyName() {
		return "JGit";
	}

	private Path createTempDirectory() throws IOException {
		return Files.createTempDirectory(TEMP_DIR_PREFIX + UUID.randomUUID().toString());
	}

	private Git cloneRepository(String repositoryUrl, Path tempDir, RepositoryCredentials credentials)
			throws GitAPIException {

		log.debug("Cloning repository {} to {}", repositoryUrl, tempDir);

		var cloneCommand = Git.cloneRepository().setURI(repositoryUrl).setDirectory(tempDir.toFile())
				.setCloneAllBranches(true);

		CredentialsProvider credentialsProvider = createCredentialsProvider(credentials);
		if (credentialsProvider != null) {
			cloneCommand.setCredentialsProvider(credentialsProvider);
		}

		int timeoutMinutes = gitScannerConfig.getPerformance().getJgitCloneTimeoutMinutes();
		cloneCommand.setTimeout(timeoutMinutes * SECONDS_TO_MINUTES);

		log.debug("Cloning repository {} with timeout of {} minutes", repositoryUrl, timeoutMinutes);

		try {
			return cloneCommand.call();
		} catch (GitAPIException e) {
			log.error("Failed to clone repository {} within {} minutes: {}", repositoryUrl, timeoutMinutes,
					e.getMessage());
			throw e;
		}
	}

	private CredentialsProvider createCredentialsProvider(RepositoryCredentials credentials) {
		if (credentials == null) {
			return null;
		}

		if (credentials.hasToken()) {
			return new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getToken());
		} else if (credentials.hasUsernamePassword()) {
			return new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getPassword());
		}

		return null;
	}

	private List<ScmCommits> extractCommits(Git git, String toolConfigId, String branchName, LocalDateTime since)
			throws GitAPIException, IOException {

		List<ScmCommits> commitDetails = new ArrayList<>();
		var logCommand = git.log();

		// CHANGE: Extracted branch setup to separate method
		setupBranch(git, logCommand, branchName);

		// CHANGE: Extracted date filter setup to separate method
		setupDateFilter(logCommand, since);

		Iterable<RevCommit> revCommits = logCommand.call();

		for (RevCommit revCommit : revCommits) {
			if (shouldProcessCommit(revCommit, since)) {
				ScmCommits commitDetail = convertRevCommitToCommit(git, revCommit, toolConfigId);
				commitDetail.setBranch(branchName);
				commitDetails.add(commitDetail);
			}
		}

		return commitDetails;
	}

	// CHANGE: Extracted method to reduce cognitive complexity
	private void setupBranch(Git git, org.eclipse.jgit.api.LogCommand logCommand, String branchName)
			throws IOException {
		if (branchName != null && !branchName.trim().isEmpty()) {
			var ref = git.getRepository().findRef(branchName);
			if (ref == null) {
				ref = git.getRepository().findRef(ORIGIN_REF_PREFIX + branchName);
			}
			if (ref != null) {
				logCommand.add(ref.getObjectId());
			} else {
				log.warn("Could not find branch {}, using default branch", branchName);
			}
		}
	}

	// CHANGE: Extracted method to reduce cognitive complexity
	private void setupDateFilter(org.eclipse.jgit.api.LogCommand logCommand, LocalDateTime since) {
		if (since != null) {
			Date sinceDate = Date.from(since.atZone(ZoneId.systemDefault()).toInstant());
			RevFilter sinceFilter = CommitTimeRevFilter.after(sinceDate);
			logCommand.setRevFilter(sinceFilter);
			log.debug("Applied date filter - since: {}", since);
		}
	}

	// CHANGE: Extracted method to reduce cognitive complexity
	private boolean shouldProcessCommit(RevCommit revCommit, LocalDateTime since) {
		if (since == null) {
			return true;
		}

		LocalDateTime commitDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(revCommit.getCommitTime()),
				ZoneId.systemDefault());

		return !commitDate.isBefore(since);
	}

	private ScmCommits convertRevCommitToCommit(Git git, RevCommit revCommit, String toolConfigId) {

		Long commitDate = revCommit.getCommitterIdent().getWhen().toInstant().toEpochMilli();

		DiffStats diffStats = calculateDiffStats(git, revCommit);

		User user = User.builder().displayName(revCommit.getAuthorIdent().getName())
				.username(revCommit.getAuthorIdent().getName()).email(revCommit.getAuthorIdent().getEmailAddress())
				.build();

		return ScmCommits.builder().sha(revCommit.getName()).commitMessage(revCommit.getFullMessage())
				.commitAuthor(user).authorName(revCommit.getAuthorIdent().getName()).commitTimestamp(commitDate)
				.addedLines(diffStats.addedLines).removedLines(diffStats.removedLines)
				.changedLines(diffStats.changedLines).fileChanges(diffStats.fileChanges)
				.filesChanged(diffStats.filesChanged).processorItemId(new ObjectId(toolConfigId))
				.createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
				.isMergeCommit(revCommit.getParents().length == MERGE_COMMIT_PARENT_COUNT).build();
	}

	/**
	 * Helper class to hold diff statistics
	 */
	private static class DiffStats {
		int addedLines = 0;
		int removedLines = 0;
		int changedLines = 0;
		int filesChanged = 0;
		List<ScmCommits.FileChange> fileChanges = new ArrayList<>();
	}

	/**
	 * Calculate diff statistics for a commit
	 */
	private DiffStats calculateDiffStats(Git git, RevCommit commit) {
		DiffStats stats = new DiffStats();

		try {
			Repository repository = git.getRepository();
			RevCommit parentCommit = getParentCommit(repository, commit);

			// CHANGE: Use try-with-resources for proper resource management
			try (ObjectReader reader = repository.newObjectReader();
					DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

				diffFormatter.setRepository(repository);
				diffFormatter.setDetectRenames(true);

				List<DiffEntry> diffs = calculateDiffs(reader, diffFormatter, commit, parentCommit, repository);
				stats.filesChanged = diffs.size();

				for (DiffEntry diff : diffs) {
					processFileDiff(diffFormatter, diff, stats);
				}
			}

		} catch (IOException e) {
			// CHANGE: More specific exception handling
			log.warn("IO error calculating diff stats for commit {}: {}", commit.getName(), e.getMessage());
		} catch (Exception e) {
			// CHANGE: Catch unexpected exceptions separately
			log.warn("Unexpected error calculating diff stats for commit {}: {}", commit.getName(), e.getMessage());
		}

		return stats;
	}

	// CHANGE: Extracted method to reduce cognitive complexity
	private RevCommit getParentCommit(Repository repository, RevCommit commit) throws IOException {
		if (commit.getParentCount() > 0) {
			RevCommit parentCommit = commit.getParent(0);
			try (RevWalk revWalk = new RevWalk(repository)) {
				revWalk.parseCommit(parentCommit);
				return parentCommit;
			}
		}
		return null;
	}

	// CHANGE: Extracted method to reduce cognitive complexity
	private List<DiffEntry> calculateDiffs(ObjectReader reader, DiffFormatter diffFormatter, RevCommit commit,
			RevCommit parentCommit, Repository repository) throws IOException {
		CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
		CanonicalTreeParser newTreeIter = new CanonicalTreeParser();

		if (parentCommit != null) {
			oldTreeIter.reset(reader, parentCommit.getTree());
		} else {
			// For initial commit, use empty tree
			oldTreeIter.reset(reader, repository.resolve(EMPTY_TREE_HASH));
		}
		newTreeIter.reset(reader, commit.getTree());

		return diffFormatter.scan(oldTreeIter, newTreeIter);
	}

	// CHANGE: Extracted method to reduce cognitive complexity
	private void processFileDiff(DiffFormatter diffFormatter, DiffEntry diff, DiffStats stats) {
		String fileName = getFileName(diff);
		FileChangeStats fileStats = new FileChangeStats();

		try {
			FileHeader fileHeader = diffFormatter.toFileHeader(diff);
			processFileHeader(fileHeader, fileStats);

			if (fileStats.hasChanges()) {
				ScmCommits.FileChange fileChange = createFileChange(fileName, diff, fileStats);
				stats.fileChanges.add(fileChange);
			}

			// Update total stats
			stats.addedLines += fileStats.addedLines;
			stats.removedLines += fileStats.removedLines;
			stats.changedLines += fileStats.changedLines;

		} catch (IOException e) {
			log.warn("Could not analyze diff for file {}: {}", fileName, e.getMessage());
		}
	}

	// CHANGE: New helper class to reduce parameter passing
	private static class FileChangeStats {
		int addedLines = 0;
		int removedLines = 0;
		int changedLines = 0;
		List<Integer> changedLineNumbers = new ArrayList<>();

		boolean hasChanges() {
			return !changedLineNumbers.isEmpty() || addedLines > 0 || removedLines > 0;
		}
	}

	// CHANGE: Extracted method to reduce cognitive complexity
	private void processFileHeader(FileHeader fileHeader, FileChangeStats fileStats) {
		for (HunkHeader hunk : fileHeader.getHunks()) {
			EditList editList = hunk.toEditList();
			for (Edit edit : editList) {
				processEdit(edit, fileStats);
			}
		}
	}

	// CHANGE: Extracted method to reduce cognitive complexity
	private void processEdit(Edit edit, FileChangeStats fileStats) {
		switch (edit.getType()) {
		case INSERT:
			processInsertEdit(edit, fileStats);
			break;
		case DELETE:
			processDeleteEdit(edit, fileStats);
			break;
		case REPLACE:
			processReplaceEdit(edit, fileStats);
			break;
		default:
			// No action needed for other types
			break;
		}
	}

	// CHANGE: Extracted methods for each edit type
	private void processInsertEdit(Edit edit, FileChangeStats fileStats) {
		int insertLines = edit.getEndB() - edit.getBeginB();
		fileStats.addedLines += insertLines;

		for (int i = edit.getBeginB(); i < edit.getEndB(); i++) {
			fileStats.changedLineNumbers.add(i + LINE_NUMBER_OFFSET);
		}
	}

	private void processDeleteEdit(Edit edit, FileChangeStats fileStats) {
		int deleteLines = edit.getEndA() - edit.getBeginA();
		fileStats.removedLines += deleteLines;

		for (int i = edit.getBeginA(); i < edit.getEndA(); i++) {
			fileStats.changedLineNumbers.add(i + LINE_NUMBER_OFFSET);
		}
	}

	private void processReplaceEdit(Edit edit, FileChangeStats fileStats) {
		int oldLines = edit.getEndA() - edit.getBeginA();
		int newLines = edit.getEndB() - edit.getBeginB();
		int minLines = Math.min(oldLines, newLines);

		fileStats.removedLines += oldLines;
		fileStats.addedLines += newLines;
		fileStats.changedLines += minLines;

		for (int i = edit.getBeginB(); i < edit.getEndB(); i++) {
			fileStats.changedLineNumbers.add(i + LINE_NUMBER_OFFSET);
		}
	}

	// CHANGE: Extracted method to create FileChange object
	private ScmCommits.FileChange createFileChange(String fileName, DiffEntry diff, FileChangeStats fileStats) {
		return ScmCommits.FileChange.builder().filePath(fileName).addedLines(fileStats.addedLines)
				.removedLines(fileStats.removedLines).changedLines(fileStats.changedLines)
				.changeType(diff.getChangeType().name()).previousPath(diff.getOldPath()).isBinary(false) // JGit
																										 // FileHeader
																										 // doesn't have
																										 // isBinary
																										 // method
				.changedLineNumbers(fileStats.changedLineNumbers).build();
	}

	/**
	 * Get the appropriate file name from a DiffEntry
	 */
	private String getFileName(DiffEntry diff) {
        return switch (diff.getChangeType()) {
            case ADD -> diff.getNewPath();
            case DELETE -> diff.getOldPath();
            default -> diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath();
        };
	}

	/**
	 * Properly close Git resources to release file handles
	 */
	private void closeGitResources(Git git) {
		if (git != null) {
			try {
				String repoPath = git.getRepository().getDirectory().getAbsolutePath();
				log.debug("Attempting to close Git resources for repository: {}", repoPath);
				git.close();
				log.debug("Successfully closed Git resources for repository: {}", repoPath);
			} catch (Exception e) {
				log.warn("Error closing Git resources: {}", e.getMessage(), e);
			}
		} else {
			log.debug("No Git resources to close - git object was null");
		}
	}

	/**
	 * Enhanced cleanup method with retry logic for Windows file locking issues
	 */
	private void cleanupTempDirectory(Path tempDir) {
		if (tempDir == null || !Files.exists(tempDir)) {
			return;
		}

		GitScannerConfig.Storage storageConfig = gitScannerConfig.getStorage();

		// CHANGE: Extracted cleanup attempts to separate method
		if (performCleanupAttempts(tempDir, storageConfig)) {
			return;
		}

		// Final fallback - mark for deletion on JVM exit
		log.error("Failed to delete temporary directory: {}. Marking for deletion on JVM exit.", tempDir);
		markDirectoryForDeletionOnExit(tempDir);
	}

	private boolean performCleanupAttempts(Path tempDir, GitScannerConfig.Storage storageConfig) {
		// First attempt - immediate cleanup
		if (attemptDirectoryCleanup(tempDir)) {
			log.debug("Successfully cleaned up temporary directory: {}", tempDir);
			return true;
		}

		// Second attempt - with garbage collection and retry
		if (performCleanupWithGc(tempDir, storageConfig)) {
			return true;
		}

		// Third attempt - with longer delay
		return performFinalCleanupAttempt(tempDir, storageConfig);
	}

	private boolean performCleanupWithGc(Path tempDir, GitScannerConfig.Storage storageConfig) {
		log.debug("First cleanup attempt failed, retrying with GC...");

		sleepSafely(storageConfig.getCleanupRetryDelayMs());

		if (attemptDirectoryCleanup(tempDir)) {
			log.debug("Successfully cleaned up temporary directory on retry: {}", tempDir);
			return true;
		}
		return false;
	}

	private boolean performFinalCleanupAttempt(Path tempDir, GitScannerConfig.Storage storageConfig) {
		log.warn("Second cleanup attempt failed, trying with longer delay...");

		sleepSafely(storageConfig.getCleanupFinalDelayMs());

		if (attemptDirectoryCleanup(tempDir)) {
			log.debug("Successfully cleaned up temporary directory on final retry: {}", tempDir);
			return true;
		}
		return false;
	}

	private void sleepSafely(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.debug("Thread interrupted during cleanup delay");
		}
	}

	/**
	 * Attempt to clean up directory with proper error handling
	 */
	private boolean attemptDirectoryCleanup(Path tempDir) {
		try {
			Files.walk(tempDir).sorted((a, b) -> b.compareTo(a)) // Delete files before directories
					.forEach(this::deletePathSafely);
			return true;
		} catch (IOException e) {
			log.debug("Directory walk failed: {}", e.getMessage());
			return false;
		} catch (RuntimeException e) {
			log.debug("Directory cleanup attempt failed: {}", e.getMessage());
			return false;
		}
	}

	// CHANGE: Extracted method to handle individual path deletion
	private void deletePathSafely(Path path) {
		try {
			Files.delete(path);
		} catch (IOException e) {
			// On Windows, if file is locked, this will throw an exception
			// We'll catch it and let the retry logic handle it
			throw new GitScannerException("Could not delete: " + path, e);
		}
	}

	/**
	 * Mark directory and all its contents for deletion when JVM exits
	 */
	private void markDirectoryForDeletionOnExit(Path tempDir) {
		try {
			Files.walk(tempDir).sorted((a, b) -> b.compareTo(a)) // Delete files before directories
					.forEach(this::markPathForDeletionOnExit);
		} catch (IOException e) {
			log.warn("Could not mark directory for deletion on exit: {}", tempDir, e);
		}
	}

	// CHANGE: Extracted method for marking individual paths
	private void markPathForDeletionOnExit(Path path) {
		try {
			path.toFile().deleteOnExit();
		} catch (Exception e) {
			log.debug("Could not mark file for deletion on exit: {}", path);
		}
	}
}
