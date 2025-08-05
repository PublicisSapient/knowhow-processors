package com.publicissapient.knowhow.processor.scm.service.strategy;

import com.publicissapient.knowhow.processor.scm.config.GitScannerConfig;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.User;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
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
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JGit-based implementation of CommitDataFetchStrategy.
 * 
 * This strategy fetches commit data by cloning the repository locally
 * using JGit and then extracting commit information from the local clone.
 * 
 * Advantages:
 * - Complete commit history access
 * - No API rate limiting
 * - Works with any Git repository
 * 
 * Disadvantages:
 * - Requires local disk space
 * - Slower for large repositories
 * - Network bandwidth for cloning
 */
@Component("jGitCommitDataFetchStrategy")
public class JGitCommitDataFetchStrategy implements CommitDataFetchStrategy {

    private static final Logger logger = LoggerFactory.getLogger(JGitCommitDataFetchStrategy.class);
    
    private static final String TEMP_DIR_PREFIX = "git-scanner-";
    private static final int DEFAULT_COMMIT_LIMIT = 1000;

    private final GitUrlParser gitUrlParser;
    private final GitScannerConfig gitScannerConfig;

    public JGitCommitDataFetchStrategy(GitUrlParser gitUrlParser, GitScannerConfig gitScannerConfig) {
        this.gitUrlParser = gitUrlParser;
        this.gitScannerConfig = gitScannerConfig;
    }

    @Override
    public List<ScmCommits> fetchCommits(String toolType, String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo, String branchName,
                                         RepositoryCredentials credentials, java.time.LocalDateTime since) throws DataProcessingException {

        String repositoryUrl = gitUrlInfo.getOriginalUrl();

        logger.info("Fetching commits using JGit strategy for repository: {} of tool: {}", repositoryUrl, toolType);

        Path tempDir = null;
        Git git = null;
        try {
            // Create temporary directory for cloning
            tempDir = createTempDirectory();

            // Clone repository
            git = cloneRepository(repositoryUrl, tempDir, credentials);

            // Fetch commits
            List<ScmCommits> commitDetails = extractCommits(git, toolConfigId, branchName, since, null, DEFAULT_COMMIT_LIMIT);

            logger.info("Successfully fetched {} commits from repository: {}", commitDetails.size(), repositoryUrl);
            return commitDetails;

        } catch (Exception e) {
            logger.error("Error fetching commits from repository {}: {}", repositoryUrl, e.getMessage(), e);
            throw new DataProcessingException("Failed to fetch commits using JGit strategy", e);
        } finally {
            // Close Git resources first to release file handles
            closeGitResources(git);

            // Clean up temporary directory
            if (tempDir != null) {
                cleanupTempDirectory(tempDir);
            }
        }
    }

    @Override
    public boolean supports(String repositoryUrl, String toolType) {
        // JGit supports all Git repositories
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

        logger.debug("Cloning repository {} to {}", repositoryUrl, tempDir);

        var cloneCommand = Git.cloneRepository()
                .setURI(repositoryUrl)
                .setDirectory(tempDir.toFile())
                .setCloneAllBranches(true);

        // Set credentials if provided
        CredentialsProvider credentialsProvider = createCredentialsProvider(credentials);
        if (credentialsProvider != null) {
            cloneCommand.setCredentialsProvider(credentialsProvider);
        }

        // Set timeout to prevent indefinite blocking
        int timeoutMinutes = gitScannerConfig.getPerformance().getJgitCloneTimeoutMinutes();
        cloneCommand.setTimeout(timeoutMinutes*60); // Convert minutes to seconds

        logger.debug("Cloning repository {} with timeout of {} minutes", repositoryUrl, timeoutMinutes);

        try {
            return cloneCommand.call();
        } catch (GitAPIException e) {
            logger.error("Failed to clone repository {} within {} minutes: {}",
                        repositoryUrl, timeoutMinutes, e.getMessage());
            throw e;
        }
    }

    private CredentialsProvider createCredentialsProvider(RepositoryCredentials credentials) {
        if (credentials == null) {
            return null;
        }

        if (credentials.hasToken()) {
            // Use token as password with empty username for most Git platforms
            return new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getToken());
        } else if (credentials.hasUsernamePassword()) {
            return new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getPassword());
        }

        // SSH key authentication would require additional setup
        return null;
    }

    private List<ScmCommits> extractCommits(Git git, String toolConfigId, String branchName,
                                               LocalDateTime since, LocalDateTime until, int limit) throws GitAPIException {
        
        List<ScmCommits> commitDetails = new ArrayList<>();
        
        var logCommand = git.log();
        
        // Set branch if specified
        if (branchName != null && !branchName.trim().isEmpty()) {
            try {
                var ref = git.getRepository().findRef(branchName);
                if (ref == null) {
                    ref = git.getRepository().findRef("refs/remotes/origin/" + branchName);
                }
                if (ref != null) {
                    logCommand.add(ref.getObjectId());
                }
            } catch (IOException e) {
                logger.warn("Could not find branch {}, using default branch", branchName);
            }
        }

        // Set date range if specified
        // Note: JGit LogCommand doesn't have direct since/until methods
        // We'll filter the results after fetching
        // if (since != null) {
        //     logCommand.since(since.atZone(ZoneId.systemDefault()).toInstant());
        // }
        // if (until != null) {
        //     logCommand.until(until.atZone(ZoneId.systemDefault()).toInstant());
        // }

        // Set limit
        logCommand.setMaxCount(limit);

        Iterable<RevCommit> revCommits = logCommand.call();

        for (RevCommit revCommit : revCommits) {
            // Apply date filtering
            LocalDateTime commitDate = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(revCommit.getCommitTime()),
                ZoneId.systemDefault()
            );

            if (since != null && commitDate.isBefore(since)) {
                continue;
            }
            if (until != null && commitDate.isAfter(until)) {
                continue;
            }

            ScmCommits commitDetail = convertRevCommitToCommit(git, revCommit, toolConfigId);
            commitDetails.add(commitDetail);
        }

        return commitDetails;
    }

    private ScmCommits convertRevCommitToCommit(Git git, RevCommit revCommit, String toolConfigId) {
        // Convert author information
        User author = User.builder()
                .displayName(revCommit.getAuthorIdent().getName())
                .email(revCommit.getAuthorIdent().getEmailAddress())
                .build();

        User committer = User.builder()
                .displayName(revCommit.getCommitterIdent().getName())
                .email(revCommit.getCommitterIdent().getEmailAddress())
                .build();

        // Convert timestamps
        LocalDateTime authorDate = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(revCommit.getAuthorIdent().getWhen().getTime() / 1000),
                ZoneId.systemDefault()
        );

        Long commitDate = revCommit.getCommitterIdent().getWhen().toInstant().toEpochMilli();

        // Calculate diff statistics and file changes
        DiffStats diffStats = calculateDiffStats(git, revCommit);

        return ScmCommits.builder()
                .sha(revCommit.getName())
                .commitMessage(revCommit.getFullMessage())
                .commitAuthorId(null) // Will be set by the persistence service
                .authorName(revCommit.getAuthorIdent().getName())
                .authorEmail(revCommit.getAuthorIdent().getEmailAddress())
                .committerName(revCommit.getCommitterIdent().getName())
                .committerEmail(revCommit.getCommitterIdent().getEmailAddress())
                .commitTimestamp(authorDate.toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
                .commitTimestamp(commitDate)
                .addedLines(diffStats.addedLines)
                .removedLines(diffStats.removedLines)
                .changedLines(diffStats.changedLines)
                .fileChanges(diffStats.fileChanges)
                .filesChanged(diffStats.filesChanged)
                .processorItemId(new ObjectId(toolConfigId))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
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

            // Get parent commit for diff comparison
            RevCommit parentCommit = null;
            if (commit.getParentCount() > 0) {
                parentCommit = commit.getParent(0);
                try (RevWalk revWalk = new RevWalk(repository)) {
                    revWalk.parseCommit(parentCommit);
                }
            }

            // Create tree parsers for diff
            try (ObjectReader reader = repository.newObjectReader()) {
                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                CanonicalTreeParser newTreeIter = new CanonicalTreeParser();

                if (parentCommit != null) {
                    oldTreeIter.reset(reader, parentCommit.getTree());
                } else {
                    // For initial commit, use empty tree
                    oldTreeIter.reset(reader, repository.resolve("4b825dc642cb6eb9a060e54bf8d69288fbee4904"));
                }
                newTreeIter.reset(reader, commit.getTree());

                // Create diff formatter
                try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    diffFormatter.setRepository(repository);
                    diffFormatter.setDetectRenames(true);

                    List<DiffEntry> diffs = diffFormatter.scan(oldTreeIter, newTreeIter);
                    stats.filesChanged = diffs.size();

                    for (DiffEntry diff : diffs) {
                        String fileName = getFileName(diff);
                        List<Integer> changedLineNumbers = new ArrayList<>();
                        int fileAddedLines = 0;
                        int fileRemovedLines = 0;
                        int fileChangedLines = 0;

                        try {
                            FileHeader fileHeader = diffFormatter.toFileHeader(diff);

                            for (HunkHeader hunk : fileHeader.getHunks()) {
                                EditList editList = hunk.toEditList();

                                for (Edit edit : editList) {
                                    switch (edit.getType()) {
                                        case INSERT:
                                            int insertLines = edit.getEndB() - edit.getBeginB();
                                            fileAddedLines += insertLines;
                                            stats.addedLines += insertLines;
                                            // Add line numbers for inserted lines
                                            for (int i = edit.getBeginB(); i < edit.getEndB(); i++) {
                                                changedLineNumbers.add(i + 1); // 1-based line numbers
                                            }
                                            break;

                                        case DELETE:
                                            int deleteLines = edit.getEndA() - edit.getBeginA();
                                            fileRemovedLines += deleteLines;
                                            stats.removedLines += deleteLines;
                                            // Add line numbers for deleted lines (from old file)
                                            for (int i = edit.getBeginA(); i < edit.getEndA(); i++) {
                                                changedLineNumbers.add(i + 1); // 1-based line numbers
                                            }
                                            break;

                                        case REPLACE:
                                            int oldLines = edit.getEndA() - edit.getBeginA();
                                            int newLines = edit.getEndB() - edit.getBeginB();
                                            fileRemovedLines += oldLines;
                                            fileAddedLines += newLines;
                                            int minLines = Math.min(oldLines, newLines);
                                            fileChangedLines += minLines;
                                            stats.removedLines += oldLines;
                                            stats.addedLines += newLines;
                                            stats.changedLines += minLines;

                                            // Add line numbers for replaced lines
                                            for (int i = edit.getBeginB(); i < edit.getEndB(); i++) {
                                                changedLineNumbers.add(i + 1); // 1-based line numbers
                                            }
                                            break;
                                    }
                                }
                            }

                            if (!changedLineNumbers.isEmpty() || fileAddedLines > 0 || fileRemovedLines > 0) {
                                ScmCommits.FileChange fileChange = ScmCommits.FileChange.builder()
                                        .filePath(fileName)
                                        .addedLines(fileAddedLines)
                                        .removedLines(fileRemovedLines)
                                        .changedLines(fileChangedLines)
                                        .changeType(diff.getChangeType().name())
                                        .previousPath(diff.getOldPath())
                                        .isBinary(false) // JGit FileHeader doesn't have isBinary method
                                        .changedLineNumbers(changedLineNumbers)
                                        .build();

                                stats.fileChanges.add(fileChange);
                            }

                        } catch (IOException e) {
                            logger.warn("Could not analyze diff for file {}: {}", fileName, e.getMessage());
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("Could not calculate diff stats for commit {}: {}", commit.getName(), e.getMessage());
        }

        return stats;
    }

    /**
     * Get the appropriate file name from a DiffEntry
     */
    private String getFileName(DiffEntry diff) {
        switch (diff.getChangeType()) {
            case ADD:
                return diff.getNewPath();
            case DELETE:
                return diff.getOldPath();
            case MODIFY:
            case RENAME:
            case COPY:
            default:
                return diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath();
        }
    }

    /**
     * Properly close Git resources to release file handles
     */
    private void closeGitResources(Git git) {
        if (git != null) {
            try {
                logger.debug("Attempting to close Git resources for repository: {}",
                    git.getRepository().getDirectory().getAbsolutePath());
                // Close the Git object which will close the underlying Repository
                git.close();
                logger.debug("Successfully closed Git resources for repository: {}",
                    git.getRepository().getDirectory().getAbsolutePath());
            } catch (Exception e) {
                logger.warn("Error closing Git resources. Repository path: {}, Error: {}",
                    git.getRepository().getDirectory().getAbsolutePath(), e.getMessage(), e);
            }
        } else {
            logger.debug("No Git resources to close - git object was null");
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

        // First attempt - immediate cleanup
        if (attemptDirectoryCleanup(tempDir)) {
            logger.debug("Successfully cleaned up temporary directory: {}", tempDir);
            return;
        }

        // Second attempt - with garbage collection and retry
        logger.debug("First cleanup attempt failed, retrying with GC...");
        if (storageConfig.isForceGcOnCleanupFailure()) {
            System.gc(); // Force garbage collection to release any remaining references
        }

        try {
            Thread.sleep(storageConfig.getCleanupRetryDelayMs()); // Configurable pause to allow GC to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (attemptDirectoryCleanup(tempDir)) {
            logger.debug("Successfully cleaned up temporary directory on retry: {}", tempDir);
            return;
        }

        // Third attempt - with longer delay
        logger.warn("Second cleanup attempt failed, trying with longer delay...");
        try {
            Thread.sleep(storageConfig.getCleanupFinalDelayMs()); // Configurable longer pause for Windows file handle release
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (attemptDirectoryCleanup(tempDir)) {
            logger.debug("Successfully cleaned up temporary directory on final retry: {}", tempDir);
            return;
        }

        // Final fallback - mark for deletion on JVM exit
        logger.error("Failed to delete temporary directory: {}. Marking for deletion on JVM exit.", tempDir);
        markDirectoryForDeletionOnExit(tempDir);
    }

    /**
     * Attempt to clean up directory with proper error handling
     */
    private boolean attemptDirectoryCleanup(Path tempDir) {
        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // On Windows, if file is locked, this will throw an exception
                            // We'll catch it and let the retry logic handle it
                            throw new RuntimeException("Could not delete: " + path, e);
                        }
                    });
            return true;
        } catch (Exception e) {
            logger.debug("Directory cleanup attempt failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Mark directory and all its contents for deletion when JVM exits
     */
    private void markDirectoryForDeletionOnExit(Path tempDir) {
        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            path.toFile().deleteOnExit();
                        } catch (Exception e) {
                            logger.debug("Could not mark file for deletion on exit: {}", path);
                        }
                    });
        } catch (IOException e) {
            logger.warn("Could not mark directory for deletion on exit: {}", tempDir, e);
        }
    }
}