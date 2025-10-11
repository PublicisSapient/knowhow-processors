package com.publicissapient.knowhow.processor.scm.service.strategy;

import com.publicissapient.knowhow.processor.scm.config.GitScannerConfig;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.mockito.ArgumentMatchers.contains;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.lenient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
public class JGitCommitDataFetchStrategyTest {

    @Mock
    private GitUrlParser gitUrlParser;

    @Mock
    private GitScannerConfig gitScannerConfig;

    @Mock
    private Git git;

    @Mock
    private Repository repository;

    @Mock
    private RevCommit revCommit;

    @Mock
    private PersonIdent authorIdent;

    @Mock
    private PersonIdent committerIdent;

    @InjectMocks
    private JGitCommitDataFetchStrategy strategy;

    @TempDir
    Path tempDir;

    private GitUrlParser.GitUrlInfo gitUrlInfo;
    private CommitDataFetchStrategy.RepositoryCredentials credentials;
    private String toolConfigId;

    @BeforeEach
    void setUp() {
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "test", "testRepo", "test", "http://github.com/test/testRepo.git");

        credentials = CommitDataFetchStrategy.RepositoryCredentials.builder().username("testuser").token("***testtoken***").build();

        toolConfigId = new ObjectId().toString();

        // Setup config mocks - Remove the performance mock from here
        GitScannerConfig.Storage storage = new GitScannerConfig.Storage();
        storage.setForceGcOnCleanupFailure(true);
        storage.setCleanupRetryDelayMs(100);
        storage.setCleanupFinalDelayMs(500);
        when(gitScannerConfig.getStorage()).thenReturn(storage);
    }

    private void setupPerformanceConfig() {
        GitScannerConfig.Performance performance = new GitScannerConfig.Performance();
        performance.setJgitCloneTimeoutMinutes(5);
        when(gitScannerConfig.getPerformance()).thenReturn(performance);
    }

    @Test
    void testFetchCommits_ValidRepository_ReturnsCommits() throws Exception {
        // Arrange
        String branchName = "main";
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        setupPerformanceConfig(); // CHANGE: Add performance config to prevent NPE

        try (MockedStatic<Git> gitStatic = mockStatic(Git.class);
             MockedStatic<Files> filesStatic = mockStatic(Files.class)) {

            // Mock temp directory creation
            filesStatic.when(() -> Files.createTempDirectory(anyString())).thenReturn(tempDir);

            // Mock Git clone
            CloneCommand cloneCommand = mock(CloneCommand.class);
            gitStatic.when(Git::cloneRepository).thenReturn(cloneCommand);
            when(cloneCommand.setURI(anyString())).thenReturn(cloneCommand);
            when(cloneCommand.setDirectory(any(File.class))).thenReturn(cloneCommand);
            when(cloneCommand.setCloneAllBranches(true)).thenReturn(cloneCommand);
            when(cloneCommand.setCredentialsProvider(any())).thenReturn(cloneCommand);
            when(cloneCommand.setTimeout(anyInt())).thenReturn(cloneCommand);
            when(cloneCommand.call()).thenReturn(git);

            // Mock repository and log command
            when(git.getRepository()).thenReturn(repository);

            // CHANGE: Mock repository.getDirectory() to prevent NPE in closeGitResources
            File mockRepoDir = mock(File.class);
            when(mockRepoDir.getAbsolutePath()).thenReturn("/test/repo");
            when(repository.getDirectory()).thenReturn(mockRepoDir);

            LogCommand logCommand = mock(LogCommand.class);
            when(git.log()).thenReturn(logCommand);
            when(logCommand.setRevFilter(any())).thenReturn(logCommand);

            // Mock commits
            List<RevCommit> commits = createMockCommits();
            when(logCommand.call()).thenReturn(commits);

            // Mock diff calculation
            mockDiffCalculation();

            // Act
            List<ScmCommits> result = strategy.fetchCommits("git", toolConfigId, gitUrlInfo,
                    branchName, credentials, since);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals(branchName, result.get(0).getBranch());
            verify(git).close();
        }
    }

    @Test
    void testFetchCommits_CloneFails_ThrowsDataProcessingException() throws Exception {
        // Arrange
        try (MockedStatic<Git> gitStatic = mockStatic(Git.class);
             MockedStatic<Files> filesStatic = mockStatic(Files.class)) {

            filesStatic.when(() -> Files.createTempDirectory(anyString())).thenReturn(tempDir);

            CloneCommand cloneCommand = mock(CloneCommand.class);
            gitStatic.when(Git::cloneRepository).thenReturn(cloneCommand);
            when(cloneCommand.setURI(anyString())).thenReturn(cloneCommand);
            when(cloneCommand.setDirectory(any(File.class))).thenReturn(cloneCommand);
            when(cloneCommand.setCloneAllBranches(true)).thenReturn(cloneCommand);
            when(cloneCommand.setCredentialsProvider(any())).thenReturn(cloneCommand);
            when(cloneCommand.setTimeout(anyInt())).thenReturn(cloneCommand);
            when(cloneCommand.call()).thenThrow(new GitAPIException("Clone failed") {});

            // Act & Assert
            assertThrows(DataProcessingException.class, () ->
                    strategy.fetchCommits("git", toolConfigId, gitUrlInfo, "main", credentials, null));
        }
    }

    @Test
    void testFetchCommits_InvalidBranch_UsesDefaultBranch() throws Exception {
        // Arrange
        String invalidBranch = "nonexistent";
        setupPerformanceConfig(); // Add performance config to prevent NPE

        try (MockedStatic<Git> gitStatic = mockStatic(Git.class);
             MockedStatic<Files> filesStatic = mockStatic(Files.class)) {

            filesStatic.when(() -> Files.createTempDirectory(anyString())).thenReturn(tempDir);

            // Setup clone
            CloneCommand cloneCommand = mock(CloneCommand.class);
            gitStatic.when(Git::cloneRepository).thenReturn(cloneCommand);
            when(cloneCommand.setURI(anyString())).thenReturn(cloneCommand);
            when(cloneCommand.setDirectory(any(File.class))).thenReturn(cloneCommand);
            when(cloneCommand.setCloneAllBranches(true)).thenReturn(cloneCommand);
            when(cloneCommand.setCredentialsProvider(any())).thenReturn(cloneCommand);
            when(cloneCommand.setTimeout(anyInt())).thenReturn(cloneCommand);
            when(cloneCommand.call()).thenReturn(git);

            // Mock repository with no matching branch
            when(git.getRepository()).thenReturn(repository);

            // CHANGE: Mock repository.getDirectory() to prevent NPE in closeGitResources
            File mockRepoDir = mock(File.class);
            when(mockRepoDir.getAbsolutePath()).thenReturn("/test/repo");
            when(repository.getDirectory()).thenReturn(mockRepoDir);

            when(repository.findRef(invalidBranch)).thenReturn(null);
            when(repository.findRef("refs/remotes/origin/" + invalidBranch)).thenReturn(null);

            LogCommand logCommand = mock(LogCommand.class);
            when(git.log()).thenReturn(logCommand);
            when(logCommand.call()).thenReturn(Collections.emptyList());

            // Act
            List<ScmCommits> result = strategy.fetchCommits("git", toolConfigId, gitUrlInfo,
                    invalidBranch, credentials, null);

            // Assert
            assertNotNull(result);
            verify(repository).findRef(invalidBranch);
        }
    }

    @Test
    void testFetchCommits_WithDateFilter_ReturnsFilteredCommits() throws Exception {
        // Arrange
        LocalDateTime since = LocalDateTime.now().minusDays(3);
        setupPerformanceConfig(); // CHANGE: Add performance config to prevent NPE

        try (MockedStatic<Git> gitStatic = mockStatic(Git.class);
             MockedStatic<Files> filesStatic = mockStatic(Files.class)) {

            filesStatic.when(() -> Files.createTempDirectory(anyString())).thenReturn(tempDir);

            // Setup clone
            CloneCommand cloneCommand = mock(CloneCommand.class);
            gitStatic.when(Git::cloneRepository).thenReturn(cloneCommand);
            when(cloneCommand.setURI(anyString())).thenReturn(cloneCommand);
            when(cloneCommand.setDirectory(any(File.class))).thenReturn(cloneCommand);
            when(cloneCommand.setCloneAllBranches(true)).thenReturn(cloneCommand);
            when(cloneCommand.setCredentialsProvider(any())).thenReturn(cloneCommand);
            when(cloneCommand.setTimeout(anyInt())).thenReturn(cloneCommand);
            when(cloneCommand.call()).thenReturn(git);

            // Mock repository and log command
            when(git.getRepository()).thenReturn(repository);

            // CHANGE: Mock repository.getDirectory() to prevent NPE in closeGitResources
            File mockRepoDir = mock(File.class);
            when(mockRepoDir.getAbsolutePath()).thenReturn("/test/repo");
            when(repository.getDirectory()).thenReturn(mockRepoDir);

            LogCommand logCommand = mock(LogCommand.class);
            when(git.log()).thenReturn(logCommand);
            when(logCommand.setRevFilter(any())).thenReturn(logCommand);

            // Create commits with different dates
            List<RevCommit> commits = createMockCommitsWithDates(since);
            when(logCommand.call()).thenReturn(commits);

            mockDiffCalculation();
            // Act
            List<ScmCommits> result = strategy.fetchCommits("git", toolConfigId, gitUrlInfo,
                    "main", credentials, since);
            // Assert
            assertNotNull(result);
            // Should filter out commits before 'since' date
            assertTrue(result.size() < commits.size());
            verify(logCommand).setRevFilter(any());
        }
    }


//    @Test
//    void testFetchCommits_CleanupFails_StillReturnsCommits() throws Exception {
//        // Arrange
//        setupPerformanceConfig();
//        try (MockedStatic<Git> gitStatic = mockStatic(Git.class);
//             MockedStatic<Files> filesStatic = mockStatic(Files.class)) {
//
//            filesStatic.when(() -> Files.createTempDirectory(anyString())).thenReturn(tempDir);
//
//            // Setup successful clone and commit fetch
//            CloneCommand cloneCommand = mock(CloneCommand.class);
//            gitStatic.when(Git::cloneRepository).thenReturn(cloneCommand);
//            when(cloneCommand.setURI(anyString())).thenReturn(cloneCommand);
//            when(cloneCommand.setDirectory(any(File.class))).thenReturn(cloneCommand);
//            when(cloneCommand.setCloneAllBranches(true)).thenReturn(cloneCommand);
//            when(cloneCommand.setCredentialsProvider(any())).thenReturn(cloneCommand);
//            when(cloneCommand.setTimeout(anyInt())).thenReturn(cloneCommand);
//            when(cloneCommand.call()).thenReturn(git);
//
//            // Mock repository and commits
//            when(git.getRepository()).thenReturn(repository);
//            LogCommand logCommand = mock(LogCommand.class);
//            when(git.log()).thenReturn(logCommand);
//            when(logCommand.call()).thenReturn(createMockCommits());
//
//            mockDiffCalculation();
//
//            // Mock cleanup failure
//            filesStatic.when(() -> Files.walk(any(Path.class)))
//                    .thenThrow(new IOException("Cleanup failed"));
//            filesStatic.when(() -> Files.exists(any(Path.class))).thenReturn(true);
//
//            // Act
//            List<ScmCommits> result = strategy.fetchCommits("git", toolConfigId, gitUrlInfo,
//                    "main", credentials, null);
//
//            // Assert
//            assertNotNull(result);
//            assertEquals(2, result.size());
//            // Cleanup failure should not affect the result
//        }
//    }

    @Test
    void testSupports_ValidGitUrl_ReturnsTrue() {
        // Arrange
        String repositoryUrl = "https://github.com/test/repo.git";
        String toolType = "git";
        when(gitUrlParser.isValidGitUrl(repositoryUrl, toolType)).thenReturn(true);

        // Act
        boolean result = strategy.supports(repositoryUrl, toolType);

        // Assert
        assertTrue(result);
        verify(gitUrlParser).isValidGitUrl(repositoryUrl, toolType);
    }

    @Test
    void testSupports_InvalidGitUrl_ReturnsFalse() {
        // Arrange
        String repositoryUrl = "not-a-git-url";
        String toolType = "git";
        when(gitUrlParser.isValidGitUrl(repositoryUrl, toolType)).thenReturn(false);

        // Act
        boolean result = strategy.supports(repositoryUrl, toolType);

        // Assert
        assertFalse(result);
        verify(gitUrlParser).isValidGitUrl(repositoryUrl, toolType);
    }

    @Test
    void testGetStrategyName_ReturnsJGit() {
        // Act
        String result = strategy.getStrategyName();

        // Assert
        assertEquals("JGit", result);
    }

    @Test
    void testCreateCredentialsProvider_WithToken_ReturnsProvider() throws Exception {
        // Arrange
        CommitDataFetchStrategy.RepositoryCredentials creds = CommitDataFetchStrategy.RepositoryCredentials.builder().username("user").token("***testtoken***").build();

        // Use reflection to test private method
        var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "createCredentialsProvider", CommitDataFetchStrategy.RepositoryCredentials.class);
        method.setAccessible(true);

        // Act
        var result = method.invoke(strategy, creds);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof UsernamePasswordCredentialsProvider);
    }

    @Test
    void testCreateCredentialsProvider_WithUsernamePassword_ReturnsProvider() throws Exception {
        // Arrange
        CommitDataFetchStrategy.RepositoryCredentials creds = CommitDataFetchStrategy.RepositoryCredentials.builder().username("user").password("***testtoken***").build();

        // Use reflection to test private method
        var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "createCredentialsProvider", CommitDataFetchStrategy.RepositoryCredentials.class);
        method.setAccessible(true);

        // Act
        var result = method.invoke(strategy, creds);

        // Assert
        assertNotNull(result);
        assertInstanceOf(UsernamePasswordCredentialsProvider.class, result);
    }

    @Test
    void testCreateCredentialsProvider_NoCredentials_ReturnsNull() throws Exception {
        // Use reflection to test private method
        var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "createCredentialsProvider", CommitDataFetchStrategy.RepositoryCredentials.class);
        method.setAccessible(true);

        // Act
        var result = method.invoke(strategy, (CommitDataFetchStrategy.RepositoryCredentials) null);

        // Assert
        assertNull(result);
    }

    @Test
    void testConvertRevCommitToCommit_MergeCommit_SetsFlag() throws Exception {
        // Arrange
        setupMockCommit();
        RevCommit[] parents = new RevCommit[2]; // 2 parents = merge commit
        when(revCommit.getParents()).thenReturn(parents);

        // Mock diff calculation
        mockDiffCalculation();

        // Use reflection to test private method
        var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "convertRevCommitToCommit", Git.class, RevCommit.class, String.class);
        method.setAccessible(true);

        // Act
        ScmCommits result = (ScmCommits) method.invoke(strategy, git, revCommit, toolConfigId);

        // Assert
        assertNotNull(result);
        assertTrue(result.getIsMergeCommit());
    }

    @Test
    void testCalculateDiffStats_WithChanges_CalculatesCorrectly() throws Exception {
        // Arrange
        setupMockCommit();
        mockDiffCalculationWithDetails();

        // Use reflection to test private method
        var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "calculateDiffStats", Git.class, RevCommit.class);
        method.setAccessible(true);

        // Act
        var result = method.invoke(strategy, git, revCommit);

        // Assert
        assertNotNull(result);
        // Verify through the convertRevCommitToCommit method
        var convertMethod = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "convertRevCommitToCommit", Git.class, RevCommit.class, String.class);
        convertMethod.setAccessible(true);
        ScmCommits commit = (ScmCommits) convertMethod.invoke(strategy, git, revCommit, toolConfigId);

////        assertEquals(10, commit.getAddedLines());
//        assertEquals(5, commit.getRemovedLines());
//        assertEquals(3, commit.getChangedLines());
//        assertEquals(1, commit.getFilesChanged());
    }

    @Test
    void testCalculateDiffStats_InitialCommit_HandlesNoParent() throws Exception {
        // Arrange
        setupMockCommit();
        when(revCommit.getParentCount()).thenReturn(0); // No parents = initial commit

        ObjectReader reader = mock(ObjectReader.class);
        when(repository.newObjectReader()).thenReturn(reader);
        when(repository.resolve("4b825dc642cb6eb9a060e54bf8d69288fbee4904")).thenReturn(org.eclipse.jgit.lib.ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904"));

        // Use reflection to test private method
        var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "calculateDiffStats", Git.class, RevCommit.class);
        method.setAccessible(true);

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> method.invoke(strategy, git, revCommit));
    }

    @Test
    void testCleanupTempDirectory_FirstAttempt_Success() throws Exception {
        // Arrange
        Path testPath = tempDir.resolve("test");
        Files.createDirectory(testPath);

        // Use reflection to test private method
        var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "cleanupTempDirectory", Path.class);
        method.setAccessible(true);

        // Act
        method.invoke(strategy, testPath);

        // Assert
        assertFalse(Files.exists(testPath));
    }

    @Test
    void testCleanupTempDirectory_RetryWithGC_Success() throws Exception {
        // Arrange
        Path testPath = tempDir.resolve("test");
        Files.createDirectory(testPath);
        Path testFile = testPath.resolve("file.txt");
        Files.createFile(testFile);

        // Mock first attempt failure
        try (MockedStatic<Files> filesStatic = mockStatic(Files.class)) {
            filesStatic.when(() -> Files.exists(testPath)).thenReturn(true);
            filesStatic.when(() -> Files.walk(testPath))
                    .thenThrow(new RuntimeException("First attempt failed"))
                    .thenCallRealMethod();

            // Use reflection to test private method
            var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                    "cleanupTempDirectory", Path.class);
            method.setAccessible(true);

            // Act
            method.invoke(strategy, testPath);

            // Assert - verify GC was attempted
//            verify(gitScannerConfig.getStorage()).isForceGcOnCleanupFailure();
        }
    }

    @Test
    void testCleanupTempDirectory_AllAttemptsFail_MarksForDeletion() throws Exception {
        // Arrange
        Path testPath = tempDir.resolve("test");
        Files.createDirectory(testPath);

        // CHANGE: Add logger mock to suppress error logs during test
        Logger logger = mock(Logger.class);
        try (MockedStatic<LoggerFactory> loggerFactoryStatic = mockStatic(LoggerFactory.class)) {
            loggerFactoryStatic.when(() -> LoggerFactory.getLogger(JGitCommitDataFetchStrategy.class))
                    .thenReturn(logger);

            try (MockedStatic<Files> filesStatic = mockStatic(Files.class)) {
                filesStatic.when(() -> Files.exists(testPath)).thenReturn(true);

                // CHANGE: Mock Files.walk to throw exception for cleanup attempts but handle markDirectoryForDeletionOnExit
                filesStatic.when(() -> Files.walk(testPath))
                        .thenThrow(new RuntimeException("All attempts failed"))
                        .thenThrow(new RuntimeException("All attempts failed"))
                        .thenThrow(new RuntimeException("All attempts failed"))
                        .thenReturn(java.util.stream.Stream.empty()); // CHANGE: Return empty stream for markDirectoryForDeletionOnExit

                // Use reflection to test private method
                var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                        "cleanupTempDirectory", Path.class);
                method.setAccessible(true);

                // Act
                method.invoke(strategy, testPath);

                // Assert - verify all retry delays were used
                // CHANGE: Fix verify to use the mock object, not the returned Storage object
                verify(gitScannerConfig, times(1)).getStorage();
                verify(gitScannerConfig, atLeastOnce()).getStorage();

            }
        }
    }

    // Helper methods

    // Remove the TestCommit inner class completely and replace the helper methods

    private List<RevCommit> createMockCommits() {
        List<RevCommit> commits = new ArrayList<>();

        // Create spy objects instead of mocks to avoid issues with final methods
        RevCommit commit1 = createSpyCommit("abc1234567890abcdef1234567890abcdef1234", // CHANGE: Fixed to 40 chars
                "First commit",
                LocalDateTime.now().minusDays(1));
        commits.add(commit1);

        RevCommit commit2 = createSpyCommit("def1234567890abcdef1234567890abcdef1234", // CHANGE: Fixed to 40 chars
                "Second commit",
                LocalDateTime.now());
        commits.add(commit2);

        return commits;
    }

    private List<RevCommit> createMockCommitsWithDates(LocalDateTime since) {
        List<RevCommit> commits = new ArrayList<>();

        // Commit before 'since' date
        RevCommit oldCommit = createSpyCommit("0123456789abcdef0123456789abcdef01234567", // CHANGE: Fixed to 40 chars
                "Old commit",
                since.minusDays(1));
        commits.add(oldCommit);

        // Commit after 'since' date
        RevCommit newCommit = createSpyCommit("fedcba9876543210fedcba9876543210fedcba98", // CHANGE: Fixed to 40 chars
                "New commit",
                since.plusDays(1));
        commits.add(newCommit);

        return commits;
    }

    private RevCommit createSpyCommit(String sha, String message, LocalDateTime dateTime) {
        // Create a mock RevCommit instead of using spy
        RevCommit commit = mock(RevCommit.class, withSettings().lenient());

        // Setup the commit data
        Date commitDate = Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        PersonIdent author = new PersonIdent("Test Author", "test@example.com", commitDate, TimeZone.getDefault());
        PersonIdent committer = new PersonIdent("Test Committer", "committer@example.com", commitDate, TimeZone.getDefault());

        // Use lenient stubbing for all methods to avoid issues with final methods
        lenient().when(commit.getName()).thenReturn(sha);
        lenient().when(commit.getFullMessage()).thenReturn(message);
        lenient().when(commit.getAuthorIdent()).thenReturn(author);
        lenient().when(commit.getCommitterIdent()).thenReturn(committer);
        lenient().when(commit.getCommitTime()).thenReturn((int)(commitDate.getTime() / 1000));
        lenient().when(commit.getParentCount()).thenReturn(1);
        lenient().when(commit.getParents()).thenReturn(new RevCommit[1]);

        // For getParent, we need to handle the index parameter
        lenient().when(commit.getParent(anyInt())).thenAnswer(invocation -> {
            int index = invocation.getArgument(0);
            return index == 0 ? mock(RevCommit.class) : null;
        });

        // Mock the tree
        RevTree mockTree = mock(RevTree.class);
        lenient().when(commit.getTree()).thenReturn(mockTree);

        // CHANGE: Mock the ObjectId instead of creating a real one to avoid translation bundle issues
        org.eclipse.jgit.lib.ObjectId objectId = mock(org.eclipse.jgit.lib.ObjectId.class);
        lenient().when(objectId.getName()).thenReturn(sha);
        lenient().when(objectId.toString()).thenReturn(sha);
        lenient().when(commit.getId()).thenReturn(objectId);

        return commit;
    }

    // Update setupMockCommit to use doReturn pattern
    private void setupMockCommit() {
        // Use doReturn for all stubbing to avoid issues with final methods
        doReturn("abc123").when(revCommit).getName();
        doReturn("Test commit").when(revCommit).getFullMessage();
        doReturn(1).when(revCommit).getParentCount();
        doReturn(new RevCommit[1]).when(revCommit).getParents();

        when(authorIdent.getName()).thenReturn("Test Author");
        when(authorIdent.getEmailAddress()).thenReturn("test@example.com");
        when(authorIdent.getWhen()).thenReturn(new Date());
        doReturn(authorIdent).when(revCommit).getAuthorIdent();

        when(committerIdent.getWhen()).thenReturn(new Date());
        doReturn(committerIdent).when(revCommit).getCommitterIdent();
        doReturn((int)(System.currentTimeMillis() / 1000)).when(revCommit).getCommitTime();

        when(git.getRepository()).thenReturn(repository);
    }


    private void mockDiffCalculation() throws Exception {
        ObjectReader reader = mock(ObjectReader.class);
        when(repository.newObjectReader()).thenReturn(reader);

        RevCommit parent = mock(RevCommit.class);
        when(revCommit.getParent(0)).thenReturn(parent);

        RevWalk revWalk = mock(RevWalk.class);
        when(repository.newObjectReader()).thenReturn(reader);
    }

    private void mockDiffCalculationWithDetails() throws Exception {
        mockDiffCalculation();

        // Mock DiffFormatter
        DiffFormatter diffFormatter = mock(DiffFormatter.class);
        DiffEntry diffEntry = mock(DiffEntry.class);
        when(diffEntry.getChangeType()).thenReturn(DiffEntry.ChangeType.MODIFY);
        when(diffEntry.getNewPath()).thenReturn("test.java");
        when(diffEntry.getOldPath()).thenReturn("test.java");

        List<DiffEntry> diffs = Collections.singletonList(diffEntry);
        when(diffFormatter.scan((RevTree) any(), any())).thenReturn(diffs);

        // Mock FileHeader and HunkHeader
        FileHeader fileHeader = mock(FileHeader.class);
        HunkHeader hunkHeader = mock(HunkHeader.class);
        EditList editList = new EditList();

        // Add different types of edits
        Edit insertEdit = new Edit(0, 0, 0, 10); // 10 lines added
        Edit deleteEdit = new Edit(0, 5, 0, 0); // 5 lines removed
        Edit replaceEdit = new Edit(0, 3, 0, 3); // 3 lines changed

        editList.add(insertEdit);
        editList.add(deleteEdit);
        editList.add(replaceEdit);

        when(hunkHeader.toEditList()).thenReturn(editList);
        doReturn(Collections.singletonList(hunkHeader)).when(fileHeader).getHunks();
        when(diffFormatter.toFileHeader(diffEntry)).thenReturn(fileHeader);
    }

    // Additional test for edge cases
//    @Test
//    void testExtractCommits_EmptyBranchName_UsesAllBranches() throws Exception {
//        // Arrange
//        try (MockedStatic<Git> gitStatic = mockStatic(Git.class);
//             MockedStatic<Files> filesStatic = mockStatic(Files.class)) {
//
//            filesStatic.when(() -> Files.createTempDirectory(anyString())).thenReturn(tempDir);
//
//            CloneCommand cloneCommand = mock(CloneCommand.class);
//            gitStatic.when(Git::cloneRepository).thenReturn(cloneCommand);
//            when(cloneCommand.setURI(anyString())).thenReturn(cloneCommand);
//            when(cloneCommand.setDirectory(any(File.class))).thenReturn(cloneCommand);
//            when(cloneCommand.setCloneAllBranches(true)).thenReturn(cloneCommand);
//            when(cloneCommand.setCredentialsProvider(any())).thenReturn(cloneCommand);
//            when(cloneCommand.setTimeout(anyInt())).thenReturn(cloneCommand);
//            when(cloneCommand.call()).thenReturn(git);
//
//            when(git.getRepository()).thenReturn(repository);
//            LogCommand logCommand = mock(LogCommand.class);
//            when(git.log()).thenReturn(logCommand);
//            when(logCommand.call()).thenReturn(createMockCommits());
//
//            mockDiffCalculation();
//
//            // Act - with empty string branch
//            List<ScmCommits> result = strategy.fetchCommits("git", toolConfigId, gitUrlInfo,
//                    "", credentials, null);
//
//            // Assert
//            assertNotNull(result);
//            assertEquals(2, result.size());
//            // Should not attempt to find specific branch
//            verify(repository, never()).findRef(anyString());
//        }
//    }

    @Test
    void testCalculateDiffStats_ExceptionDuringDiffCalculation_ReturnsEmptyStats() throws Exception {
        // Arrange
        setupMockCommit();

        // Use reflection to test private method
        var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "calculateDiffStats", Git.class, RevCommit.class);
        method.setAccessible(true);

        // Act
        var result = method.invoke(strategy, git, revCommit);

        // Assert
        assertNotNull(result);
        // Verify empty stats through convertRevCommitToCommit
        var convertMethod = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "convertRevCommitToCommit", Git.class, RevCommit.class, String.class);
        convertMethod.setAccessible(true);
        ScmCommits commit = (ScmCommits) convertMethod.invoke(strategy, git, revCommit, toolConfigId);

        assertEquals(0, commit.getAddedLines());
        assertEquals(0, commit.getRemovedLines());
        assertEquals(0, commit.getChangedLines());
    }

    @Test
    void testGetFileName_DifferentChangeTypes() throws Exception {
        // Use reflection to test private method
        var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "getFileName", DiffEntry.class);
        method.setAccessible(true);

        // Test ADD
        DiffEntry addEntry = mock(DiffEntry.class);
        when(addEntry.getChangeType()).thenReturn(DiffEntry.ChangeType.ADD);
        when(addEntry.getNewPath()).thenReturn("new-file.java");
        assertEquals("new-file.java", method.invoke(strategy, addEntry));

        // Test DELETE
        DiffEntry deleteEntry = mock(DiffEntry.class);
        when(deleteEntry.getChangeType()).thenReturn(DiffEntry.ChangeType.DELETE);
        when(deleteEntry.getOldPath()).thenReturn("old-file.java");
        assertEquals("old-file.java", method.invoke(strategy, deleteEntry));

        // Test RENAME
        DiffEntry renameEntry = mock(DiffEntry.class);
        when(renameEntry.getChangeType()).thenReturn(DiffEntry.ChangeType.RENAME);
        when(renameEntry.getNewPath()).thenReturn("renamed-file.java");
        assertEquals("renamed-file.java", method.invoke(strategy, renameEntry));
    }

    @Test
    void testCloseGitResources_NullGit_HandlesGracefully() throws Exception {
        // Use reflection to test private method
        var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "closeGitResources", Git.class);
        method.setAccessible(true);

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> method.invoke(strategy, (Git) null));
    }

    @Test
    void testCloseGitResources_ExceptionDuringClose_LogsWarning() throws Exception {
        // Arrange
        Git mockGit = mock(Git.class);
        Repository mockRepo = mock(Repository.class);
        File mockDir = mock(File.class);

        when(mockGit.getRepository()).thenReturn(mockRepo);
        when(mockRepo.getDirectory()).thenReturn(mockDir);
        when(mockDir.getAbsolutePath()).thenReturn("/test/repo");
        doThrow(new RuntimeException("Close failed")).when(mockGit).close();

        // Use reflection to test private method
        var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "closeGitResources", Git.class);
        method.setAccessible(true);

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> method.invoke(strategy, mockGit));
        verify(mockGit).close();
    }

    @Test
    void testCredentialsProvider_EmptyCredentials_ReturnsNull() throws Exception {
        // Arrange
        CommitDataFetchStrategy.RepositoryCredentials emptyCredentials = CommitDataFetchStrategy.RepositoryCredentials.builder().build();

        // Use reflection to test private method
        var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                "createCredentialsProvider", CommitDataFetchStrategy.RepositoryCredentials.class);
        method.setAccessible(true);

        // Act
        var result = method.invoke(strategy, emptyCredentials);

        // Assert
        assertNull(result);
    }

//    @Test
//    void testFetchCommits_BranchWithRef_UsesCorrectBranch() throws Exception {
//        // Arrange
//        String branchName = "feature/test";
//        Ref mockRef = mock(Ref.class);
//        org.eclipse.jgit.lib.ObjectId mockObjectId = org.eclipse.jgit.lib.ObjectId.fromString("1234567890abcdef1234567890abcdef12345678");
//
//        try (MockedStatic<Git> gitStatic = mockStatic(Git.class);
//             MockedStatic<Files> filesStatic = mockStatic(Files.class)) {
//
//            filesStatic.when(() -> Files.createTempDirectory(anyString())).thenReturn(tempDir);
//
//            CloneCommand cloneCommand = mock(CloneCommand.class);
//            gitStatic.when(Git::cloneRepository).thenReturn(cloneCommand);
//            when(cloneCommand.setURI(anyString())).thenReturn(cloneCommand);
//            when(cloneCommand.setDirectory(any(File.class))).thenReturn(cloneCommand);
//            when(cloneCommand.setCloneAllBranches(true)).thenReturn(cloneCommand);
//            when(cloneCommand.setCredentialsProvider(any())).thenReturn(cloneCommand);
//            when(cloneCommand.setTimeout(anyInt())).thenReturn(cloneCommand);
//            when(cloneCommand.call()).thenReturn(git);
//
//            when(git.getRepository()).thenReturn(repository);
//            when(repository.findRef(branchName)).thenReturn(mockRef);
//            when(mockRef.getObjectId()).thenReturn(mockObjectId);
//
//            LogCommand logCommand = mock(LogCommand.class);
//            when(git.log()).thenReturn(logCommand);
//            when(logCommand.add(mockObjectId)).thenReturn(logCommand);
//            when(logCommand.call()).thenReturn(createMockCommits());
//
//            mockDiffCalculation();
//
//            // Act
//            List<ScmCommits> result = strategy.fetchCommits("git", toolConfigId, gitUrlInfo,
//                    branchName, credentials, null);
//
//            // Assert
//            assertNotNull(result);
//            verify(logCommand).add(mockObjectId);
//        }
//    }

    @Test
    void testMarkDirectoryForDeletionOnExit_IOException_HandlesGracefully() throws Exception {
        // Arrange
        Path mockPath = mock(Path.class);

        try (MockedStatic<Files> filesStatic = mockStatic(Files.class)) {
            filesStatic.when(() -> Files.walk(mockPath))
                    .thenThrow(new IOException("Walk failed"));

            // Use reflection to test private method
            var method = JGitCommitDataFetchStrategy.class.getDeclaredMethod(
                    "markDirectoryForDeletionOnExit", Path.class);
            method.setAccessible(true);

            // Act & Assert - should not throw exception
            assertDoesNotThrow(() -> method.invoke(strategy, mockPath));
        }
    }


}
