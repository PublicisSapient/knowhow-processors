package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.knowhow.processor.scm.client.github.GitHubClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
public class GitHubServiceTest {

	@Mock
	private GitHubClient gitHubClient;

	@Mock
	private RateLimitService rateLimitService;

	@InjectMocks
	private GitHubService gitHubService;

	private String toolConfigId;
	private GitUrlParser.GitUrlInfo gitUrlInfo;
	private String token;
	private LocalDateTime since;
	private LocalDateTime until;

	@BeforeEach
	void setUp() {
		toolConfigId = new ObjectId().toString();
		gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "testRepo", "org",
				"https://github.com/owner/repo.git");

		token = "test-token";
		since = LocalDateTime.now().minusDays(7);
		until = LocalDateTime.now();
	}

	@Test
	void testFetchCommits_Success() throws IOException, PlatformApiException {
		// Arrange
		List<GHCommit> ghCommits = createMockCommits();
		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghCommits);

		// Act
		List<ScmCommits> result = gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals("sha1", result.get(0).getSha());
		assertEquals("Test commit 1", result.get(0).getCommitMessage());
		verify(gitHubClient).fetchCommits("org", "testRepo", "main", token, since, until);
	}

	@Test
	void testFetchCommits_IOException() throws IOException {
		// Arrange
		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenThrow(new IOException("Network error"));

		// Act & Assert
		PlatformApiException exception = assertThrows(PlatformApiException.class,
				() -> gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until));
		assertEquals("GitHub", exception.getPlatform());
		assertTrue(exception.getMessage().contains("Failed to fetch commits from GitHub"));
	}

	@Test
	void testFetchCommits_PartialConversionFailure() throws IOException, PlatformApiException {
		// Arrange
		List<GHCommit> ghCommits = new ArrayList<>();
		GHCommit validCommit = createMockCommit("sha1", "Valid commit");
		GHCommit invalidCommit = mock(GHCommit.class);
		when(invalidCommit.getSHA1()).thenReturn("sha2");
		when(invalidCommit.getCommitShortInfo()).thenThrow(new RuntimeException("Conversion error"));

		ghCommits.add(validCommit);
		ghCommits.add(invalidCommit);

		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghCommits);

		// Act
		List<ScmCommits> result = gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertEquals(1, result.size());
		assertEquals("sha1", result.get(0).getSha());
	}

	@Test
	void testFetchCommits_EmptyList() throws IOException, PlatformApiException {
		// Arrange
		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(new ArrayList<>());

		// Act
		List<ScmCommits> result = gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void testFetchCommits_NullAuthorWithCommitter() throws IOException, PlatformApiException {
		// Arrange
		GHCommit commit = createMockCommitWithNullAuthor();
		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(List.of(commit));

		// Act
		List<ScmCommits> result = gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertEquals(1, result.size());
		assertEquals("committerLogin", result.get(0).getAuthorName());
		assertEquals("Committer Name", result.get(0).getCommitAuthor().getDisplayName());
	}

	@Test
	void testFetchMergeRequests_Success() throws IOException, PlatformApiException {
		// Arrange
		List<GHPullRequest> pullRequests = createMockPullRequests();
		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(pullRequests);

		// Act
		List<ScmMergeRequests> result = gitHubService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since,
				until);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals("1", result.get(0).getExternalId());
		assertEquals("Test PR 1", result.get(0).getTitle());
	}

	@Test
	void testFetchMergeRequests_WithBranchFilter() throws IOException, PlatformApiException {
		// Arrange
		List<GHPullRequest> pullRequests = createMockPullRequestsWithDifferentBranches();
		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(pullRequests);

		// Act
		List<ScmMergeRequests> result = gitHubService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since,
				until);

		// Assert
		assertEquals(1, result.size());
		assertEquals("main", result.get(0).getToBranch());
	}

	@Test
	void testFetchMergeRequests_IOException() throws IOException {
		// Arrange
		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenThrow(new IOException("API error"));

		// Act & Assert
		PlatformApiException exception = assertThrows(PlatformApiException.class,
				() -> gitHubService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until));
		assertEquals("GitHub", exception.getPlatform());
		assertTrue(exception.getMessage().contains("Failed to fetch merge requests from GitHub"));
	}

	@Test
	void testFetchMergeRequests_PartialConversionFailure() throws IOException, PlatformApiException {
		// Arrange
		List<GHPullRequest> pullRequests = new ArrayList<>();
		GHPullRequest validPR = createMockPullRequest(1, "Valid PR", GHIssueState.OPEN);
		GHPullRequest invalidPR = mock(GHPullRequest.class);
		when(invalidPR.getNumber()).thenReturn(2);

		pullRequests.add(validPR);
		pullRequests.add(invalidPR);

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(pullRequests);

		// Act
		List<ScmMergeRequests> result = gitHubService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since,
				until);

		// Assert
		assertEquals(1, result.size());
		assertEquals("1", result.get(0).getExternalId());
	}

	@Test
	void testFetchMergeRequests_ClosedNotMerged() throws IOException, PlatformApiException {
		// Arrange
		GHPullRequest closedPR = createMockPullRequest(1, "Closed PR", GHIssueState.CLOSED);
		when(closedPR.getMergedAt()).thenReturn(null);
		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(List.of(closedPR));

		// Act
		List<ScmMergeRequests> result = gitHubService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since,
				until);

		// Assert
		assertEquals(1, result.size());
		assertEquals("CLOSED", result.get(0).getState());
		assertTrue(result.get(0).isClosed());
	}

	@Test
	void testFetchMergeRequests_Merged() throws IOException, PlatformApiException {
		// Arrange
		GHPullRequest mergedPR = createMockPullRequest(1, "Merged PR", GHIssueState.CLOSED);
		when(mergedPR.getMergedAt()).thenReturn(new Date());
		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(List.of(mergedPR));

		// Act
		List<ScmMergeRequests> result = gitHubService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since,
				until);

		// Assert
		assertEquals(1, result.size());
		assertEquals("MERGED", result.get(0).getState());
		assertNotNull(result.get(0).getMergedAt());
	}

	@Test
	void testGetPlatformName() {
		// Act
		String platformName = gitHubService.getPlatformName();

		// Assert
		assertEquals("GitHub", platformName);
	}

	@Test
	void testConvertToCommit_BinaryFileDetection() throws IOException, PlatformApiException {
		// Arrange
		GHCommit commit = createMockCommitWithFiles();
		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(List.of(commit));

		// Act
		List<ScmCommits> result = gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertEquals(1, result.size());
		List<ScmCommits.FileChange> fileChanges = result.get(0).getFileChanges();
		assertTrue(fileChanges.stream().anyMatch(fc -> fc.getFilePath().equals("code.java")));
	}

	@Test
	void testConvertToCommit_ExtractLineNumbers() throws IOException, PlatformApiException {
		// Arrange
		GHCommit commit = createMockCommitWithDiff();
		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(List.of(commit));

		// Act
		List<ScmCommits> result = gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertEquals(1, result.size());
		List<ScmCommits.FileChange> fileChanges = result.get(0).getFileChanges();
		assertFalse(fileChanges.isEmpty());
		assertFalse(fileChanges.get(0).getChangedLineNumbers().isEmpty());
	}

//	@Test
//	void testConvertToMergeRequest_WithReviewActivities() throws IOException, PlatformApiException {
//		// Arrange
//		GHPullRequest pr = createMockPullRequestWithReviews();
//		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
//				.thenReturn(List.of(pr));
//
//		// Act
//		List<ScmMergeRequests> result = gitHubService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since,
//				until);
//
//		// Assert
//		assertEquals(1, result.size());
//		assertNotNull(result.get(0).getPickedForReviewOn());
//	}

	@Test
	void testGetPrPickupTime_NoReviews() throws IOException {
		// Arrange
		GHPullRequest pr = mock(GHPullRequest.class);

		// Mock the PagedIterable directly
        @SuppressWarnings("unchecked")
        PagedIterable<GHPullRequestReview> mockReviews = mock(PagedIterable.class);

        // Mock the PagedIterator
        @SuppressWarnings("unchecked")
        PagedIterator<GHPullRequestReview> mockIterator = mock(PagedIterator.class);
        when(mockIterator.hasNext()).thenReturn(false);

        when(mockReviews.iterator()).thenReturn(mockIterator);
        when(pr.listReviews()).thenReturn(mockReviews);

		// Act
		Long pickupTime = gitHubService.getPrPickupTime(pr);

		// Assert
		assertNull(pickupTime);
	}

	@Test
	void testExtractDiffStats_Exception() throws IOException, PlatformApiException {
		// Arrange
		GHCommit commit = mock(GHCommit.class);
		when(commit.getSHA1()).thenReturn("sha1");
		when(commit.getCommitShortInfo()).thenReturn(mock(GHCommit.ShortInfo.class));
		when(commit.getCommitShortInfo().getMessage()).thenReturn("Test commit");
		when(commit.getCommitDate()).thenReturn(new Date());
		when(commit.getFiles()).thenThrow(new RuntimeException("Error getting files"));
		when(commit.getParentSHA1s()).thenReturn(List.of("parent1"));

		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(List.of(commit));

		// Act
		List<ScmCommits> result = gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertEquals(1, result.size());
		assertEquals(0, result.get(0).getAddedLines());
		assertEquals(0, result.get(0).getRemovedLines());
		assertEquals(0, result.get(0).getFilesChanged());
	}

	@Test
	void testMapGitHubStatus_AllTypes() throws IOException, PlatformApiException {
		// Arrange
		List<GHCommit> commits = createMockCommitsWithVariousStatuses();
		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(commits);

		// Act
		List<ScmCommits> result = gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertEquals(4, result.size());
		assertTrue(result.stream()
				.anyMatch(c -> c.getFileChanges().stream().anyMatch(fc -> fc.getChangeType().equals("ADDED"))));
		assertTrue(result.stream()
				.anyMatch(c -> c.getFileChanges().stream().anyMatch(fc -> fc.getChangeType().equals("DELETED"))));
		assertTrue(result.stream()
				.anyMatch(c -> c.getFileChanges().stream().anyMatch(fc -> fc.getChangeType().equals("RENAMED"))));
		assertTrue(result.stream()
				.anyMatch(c -> c.getFileChanges().stream().anyMatch(fc -> fc.getChangeType().equals("MODIFIED"))));
	}

	@Test
	void testIsBinaryFile_VariousExtensions() throws IOException, PlatformApiException {
		// Arrange
		GHCommit commit = createMockCommitWithVariousFiles();
		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(List.of(commit));

		// Act
		List<ScmCommits> result = gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		List<ScmCommits.FileChange> fileChanges = result.get(0).getFileChanges();

		// Binary files
		assertTrue(
				fileChanges.stream().filter(fc -> fc.getFilePath().endsWith(".jpg")).findFirst().get().getIsBinary());
		assertTrue(
				fileChanges.stream().filter(fc -> fc.getFilePath().endsWith(".pdf")).findFirst().get().getIsBinary());
		assertTrue(
				fileChanges.stream().filter(fc -> fc.getFilePath().endsWith(".zip")).findFirst().get().getIsBinary());
		assertTrue(
				fileChanges.stream().filter(fc -> fc.getFilePath().endsWith(".exe")).findFirst().get().getIsBinary());

		// Non-binary files
		assertFalse(
				fileChanges.stream().filter(fc -> fc.getFilePath().endsWith(".txt")).findFirst().get().getIsBinary());
		assertFalse(
				fileChanges.stream().filter(fc -> fc.getFilePath().endsWith(".java")).findFirst().get().getIsBinary());
		assertFalse(
				fileChanges.stream().filter(fc -> fc.getFilePath().endsWith(".xml")).findFirst().get().getIsBinary());
	}

	@Test
	void testExtractLineNumbers_ComplexDiff() throws IOException, PlatformApiException {
		// Arrange
		GHCommit commit = createMockCommitWithComplexDiff();
		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(List.of(commit));

		// Act
		List<ScmCommits> result = gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertEquals(1, result.size());
		List<ScmCommits.FileChange> fileChanges = result.get(0).getFileChanges();
		assertFalse(fileChanges.isEmpty());
		List<Integer> lineNumbers = fileChanges.get(0).getChangedLineNumbers();
		assertFalse(lineNumbers.isEmpty());
		assertTrue(lineNumbers.size() > 0);
	}

	@Test
	void testFetchCommits_NoOrganization() throws IOException, PlatformApiException {
		// Arrange
		gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "testRepo", null,
				"https://github.com/owner/repo.git");
		List<GHCommit> ghCommits = createMockCommits();
		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghCommits);

		// Act
		List<ScmCommits> result = gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		verify(gitHubClient).fetchCommits("owner", "testRepo", "main", token, since, until);
	}

	@Test
	void testConvertToCommit_MergeCommit() throws IOException, PlatformApiException {
		// Arrange
		GHCommit mergeCommit = createMockMergeCommit();
		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(List.of(mergeCommit));

		// Act
		List<ScmCommits> result = gitHubService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

		// Assert
		assertEquals(1, result.size());
		assertTrue(result.get(0).getIsMergeCommit());
		assertEquals(2, result.get(0).getParentShas().size());
	}

	@Test
	void testFetchMergeRequests_NullBranchFilter() throws IOException, PlatformApiException {
		// Arrange
		List<GHPullRequest> pullRequests = createMockPullRequests();
		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(pullRequests);

		// Act
		List<ScmMergeRequests> result = gitHubService.fetchMergeRequests(toolConfigId, gitUrlInfo, "", token, since,
				until);

		// Assert
		assertEquals(2, result.size());
	}

	@Test
	void testConvertToMergeRequest_NullUser() throws IOException, PlatformApiException {
		// Arrange
		GHPullRequest pr = createMockPullRequestWithNullUser();
		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(List.of(pr));

		// Act
		List<ScmMergeRequests> result = gitHubService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since,
				until);

		// Assert
		assertEquals(1, result.size());
		assertNull(result.get(0).getAuthorId());
		assertNull(result.get(0).getAuthorUserId());
	}

	@Test
	void testExtractPullRequestStats_Exception() throws IOException, PlatformApiException, NoSuchMethodException {
        // Arrange
        GHPullRequest pr = mock(GHPullRequest.class);

        when(pr.getNumber()).thenReturn(1);
        when(pr.getTitle()).thenReturn("Test PR");
        when(pr.getBody()).thenReturn("Test body");
        when(pr.getState()).thenReturn(GHIssueState.OPEN);
        doReturn(new Date()).when(pr).getUpdatedAt();
        when(pr.getHtmlUrl()).thenReturn(new URL("https://github.com/test/repo/pull/1"));

        GHRepository repo = mock(GHRepository.class);
        when(repo.getFullName()).thenReturn("test/repo");
        when(pr.getRepository()).thenReturn(repo);

        GHCommitPointer base = mock(GHCommitPointer.class);
        when(base.getRef()).thenReturn("main");
        when(pr.getBase()).thenReturn(base);

        GHCommitPointer head = mock(GHCommitPointer.class);
        when(head.getRef()).thenReturn("feature");
        when(pr.getHead()).thenReturn(head);

        // Make stats methods throw exceptions
        when(pr.getAdditions()).thenThrow(new RuntimeException("Stats error"));
        @SuppressWarnings("unchecked")
        PagedIterable<GHPullRequestReview> mockReviews = mock(PagedIterable.class);

        // Mock the PagedIterator
        @SuppressWarnings("unchecked")
        PagedIterator<GHPullRequestReview> mockIterator = mock(PagedIterator.class);
        when(mockIterator.hasNext()).thenReturn(false);

        when(mockReviews.iterator()).thenReturn(mockIterator);
        when(pr.listReviews()).thenReturn(mockReviews);

        when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(List.of(pr));

        // Act
        List<ScmMergeRequests> result = gitHubService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since,
                until);

        // Assert
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getLinesChanged());
        assertEquals(0, result.get(0).getCommitCount());
    }


    // Helper methods to create mock objects

	private List<GHCommit> createMockCommits() throws IOException {
		List<GHCommit> commits = new ArrayList<>();
		commits.add(createMockCommit("sha1", "Test commit 1"));
		commits.add(createMockCommit("sha2", "Test commit 2"));
		return commits;
	}

	private GHCommit createMockCommit(String sha, String message) throws IOException {
		GHCommit commit = mock(GHCommit.class);
		when(commit.getSHA1()).thenReturn(sha);

		GHCommit.ShortInfo shortInfo = mock(GHCommit.ShortInfo.class);
		when(shortInfo.getMessage()).thenReturn(message);
		when(commit.getCommitShortInfo()).thenReturn(shortInfo);

		when(commit.getCommitDate()).thenReturn(new Date());

		GHUser author = mock(GHUser.class);
		when(author.getLogin()).thenReturn("authorLogin");
		when(author.getName()).thenReturn("Author Name");
		when(author.getEmail()).thenReturn("author@example.com");
		when(commit.getAuthor()).thenReturn(author);

		when(commit.getParentSHA1s()).thenReturn(List.of("parent1"));
		when(commit.getFiles()).thenReturn(new ArrayList<>());

		return commit;
	}

	private GHCommit createMockCommitWithNullAuthor() throws IOException {
		GHCommit commit = mock(GHCommit.class);
		when(commit.getSHA1()).thenReturn("sha1");

		GHCommit.ShortInfo shortInfo = mock(GHCommit.ShortInfo.class);
		when(shortInfo.getMessage()).thenReturn("Test commit");
		when(commit.getCommitShortInfo()).thenReturn(shortInfo);

		when(commit.getCommitDate()).thenReturn(new Date());
		when(commit.getAuthor()).thenReturn(null);

		GHUser committer = mock(GHUser.class);
		when(committer.getLogin()).thenReturn("committerLogin");
		when(committer.getName()).thenReturn("Committer Name");
		when(committer.getEmail()).thenReturn("committer@example.com");
		when(commit.getCommitter()).thenReturn(committer);

		when(commit.getParentSHA1s()).thenReturn(List.of("parent1"));
		when(commit.getFiles()).thenReturn(new ArrayList<>());

		return commit;
	}

	private GHCommit createMockCommitWithFiles() throws IOException {
		GHCommit commit = createMockCommit("sha1", "Test commit with files");

		List<GHCommit.File> files = new ArrayList<>();

		GHCommit.File binaryFile = mock(GHCommit.File.class);
		when(binaryFile.getFileName()).thenReturn("image.png");
		when(binaryFile.getLinesAdded()).thenReturn(0);
		when(binaryFile.getLinesDeleted()).thenReturn(0);
		when(binaryFile.getStatus()).thenReturn("added");
		when(binaryFile.getPatch()).thenReturn(null);
		files.add(binaryFile);

		GHCommit.File codeFile = mock(GHCommit.File.class);
		when(codeFile.getFileName()).thenReturn("code.java");
		when(codeFile.getLinesAdded()).thenReturn(10);
		when(codeFile.getLinesDeleted()).thenReturn(5);
		when(codeFile.getStatus()).thenReturn("modified");
		when(codeFile.getPatch()).thenReturn("@@ -1,5 +1,10 @@\n+added line\n-removed line");
		files.add(codeFile);

		when(commit.getFiles()).thenReturn(files);

		return commit;
	}

	private GHCommit createMockCommitWithDiff() throws IOException {
		GHCommit commit = createMockCommit("sha1", "Test commit with diff");

		List<GHCommit.File> files = new ArrayList<>();
		GHCommit.File file = mock(GHCommit.File.class);
		when(file.getFileName()).thenReturn("test.java");
		when(file.getLinesAdded()).thenReturn(5);
		when(file.getLinesDeleted()).thenReturn(3);
		when(file.getStatus()).thenReturn("modified");
		when(file.getPatch()).thenReturn(
				"@@ -10,3 +10,5 @@\n context line\n-removed line\n+added line 1\n+added line 2\n context line");
		files.add(file);

		when(commit.getFiles()).thenReturn(files);

		return commit;
	}

	private List<GHCommit> createMockCommitsWithVariousStatuses() throws IOException {
		List<GHCommit> commits = new ArrayList<>();

		// Added file
		GHCommit addedCommit = createMockCommit("sha1", "Added file");
		List<GHCommit.File> addedFiles = new ArrayList<>();
		GHCommit.File addedFile = mock(GHCommit.File.class);
		when(addedFile.getFileName()).thenReturn("new.java");
		when(addedFile.getStatus()).thenReturn("added");
		when(addedFile.getLinesAdded()).thenReturn(100);
		when(addedFile.getLinesDeleted()).thenReturn(0);
		addedFiles.add(addedFile);
		when(addedCommit.getFiles()).thenReturn(addedFiles);
		commits.add(addedCommit);

		// Removed file
		GHCommit removedCommit = createMockCommit("sha2", "Removed file");
		List<GHCommit.File> removedFiles = new ArrayList<>();
		GHCommit.File removedFile = mock(GHCommit.File.class);
		when(removedFile.getFileName()).thenReturn("old.java");
		when(removedFile.getStatus()).thenReturn("removed");
		when(removedFile.getLinesAdded()).thenReturn(0);
		when(removedFile.getLinesDeleted()).thenReturn(50);
		removedFiles.add(removedFile);
		when(removedCommit.getFiles()).thenReturn(removedFiles);
		commits.add(removedCommit);

		// Renamed file
		GHCommit renamedCommit = createMockCommit("sha3", "Renamed file");
		List<GHCommit.File> renamedFiles = new ArrayList<>();
		GHCommit.File renamedFile = mock(GHCommit.File.class);
		when(renamedFile.getFileName()).thenReturn("renamed.java");
		when(renamedFile.getPreviousFilename()).thenReturn("original.java");
		when(renamedFile.getStatus()).thenReturn("renamed");
		when(renamedFile.getLinesAdded()).thenReturn(5);
		when(renamedFile.getLinesDeleted()).thenReturn(5);
		renamedFiles.add(renamedFile);
		when(renamedCommit.getFiles()).thenReturn(renamedFiles);
		commits.add(renamedCommit);

		// Modified file
		GHCommit modifiedCommit = createMockCommit("sha4", "Modified file");
		List<GHCommit.File> modifiedFiles = new ArrayList<>();
		GHCommit.File modifiedFile = mock(GHCommit.File.class);
		when(modifiedFile.getFileName()).thenReturn("modified.java");
		when(modifiedFile.getStatus()).thenReturn("modified");
		when(modifiedFile.getLinesAdded()).thenReturn(20);
		when(modifiedFile.getLinesDeleted()).thenReturn(10);
		modifiedFiles.add(modifiedFile);
		when(modifiedCommit.getFiles()).thenReturn(modifiedFiles);
		commits.add(modifiedCommit);

		return commits;
	}

	private GHCommit createMockCommitWithVariousFiles() throws IOException {
		GHCommit commit = createMockCommit("sha1", "Test various file types");

		List<GHCommit.File> files = new ArrayList<>();

		// Binary files
		String[] binaryFiles = { "image.jpg", "document.pdf", "archive.zip", "program.exe" };
		for (String fileName : binaryFiles) {
			GHCommit.File file = mock(GHCommit.File.class);
			when(file.getFileName()).thenReturn(fileName);
			when(file.getLinesAdded()).thenReturn(0);
			when(file.getLinesDeleted()).thenReturn(0);
			when(file.getStatus()).thenReturn("added");
			files.add(file);
		}

		// Non-binary files
		String[] textFiles = { "readme.txt", "Main.java", "config.xml" };
		for (String fileName : textFiles) {
			GHCommit.File file = mock(GHCommit.File.class);
			when(file.getFileName()).thenReturn(fileName);
			when(file.getLinesAdded()).thenReturn(10);
			when(file.getLinesDeleted()).thenReturn(5);
			when(file.getStatus()).thenReturn("modified");
			files.add(file);
		}

		when(commit.getFiles()).thenReturn(files);

		return commit;
	}

	private GHCommit createMockCommitWithComplexDiff() throws IOException {
		GHCommit commit = createMockCommit("sha1", "Complex diff commit");

		List<GHCommit.File> files = new ArrayList<>();
		GHCommit.File file = mock(GHCommit.File.class);
		when(file.getFileName()).thenReturn("complex.java");
		when(file.getLinesAdded()).thenReturn(15);
		when(file.getLinesDeleted()).thenReturn(10);
		when(file.getStatus()).thenReturn("modified");

		String complexDiff = "@@ -1,10 +1,15 @@\n" + " public class Complex {\n" + "-    private String oldField;\n"
				+ "+    private String newField;\n" + "+    private int counter;\n" + " \n"
				+ "     public void method() {\n" + "-        System.out.println(old);\n"
				+ "+        System.out.println(new);\n" + "+        counter++;\n" + "     }\n" + "@@ -20,5 +25,8 @@\n"
				+ "     public void anotherMethod() {\n" + "-        // old implementation\n"
				+ "+        // new implementation\n" + "+        if (counter > 0) {\n" + "+            process();\n"
				+ "+        }\n" + "     }\n" + " }";

		when(file.getPatch()).thenReturn(complexDiff);
		files.add(file);

		when(commit.getFiles()).thenReturn(files);

		return commit;
	}

	private GHCommit createMockMergeCommit() throws IOException {
		GHCommit commit = createMockCommit("sha1", "Merge commit");
		when(commit.getParentSHA1s()).thenReturn(Arrays.asList("parent1", "parent2"));
		return commit;
	}

	private List<GHPullRequest> createMockPullRequests() throws IOException {
		List<GHPullRequest> pullRequests = new ArrayList<>();
		pullRequests.add(createMockPullRequest(1, "Test PR 1", GHIssueState.OPEN));
		pullRequests.add(createMockPullRequest(2, "Test PR 2", GHIssueState.OPEN));
		return pullRequests;
	}

	private List<GHPullRequest> createMockPullRequestsWithDifferentBranches() throws IOException {
		List<GHPullRequest> pullRequests = new ArrayList<>();

		GHPullRequest mainPR = createMockPullRequest(1, "PR to main", GHIssueState.OPEN);
		GHCommitPointer mainBase = mock(GHCommitPointer.class);
		when(mainBase.getRef()).thenReturn("main");
		when(mainPR.getBase()).thenReturn(mainBase);
		pullRequests.add(mainPR);

		GHPullRequest devPR = createMockPullRequest(2, "PR to dev", GHIssueState.OPEN);
		GHCommitPointer devBase = mock(GHCommitPointer.class);
		when(devBase.getRef()).thenReturn("dev");
		when(devPR.getBase()).thenReturn(devBase);
		pullRequests.add(devPR);

		return pullRequests;
	}

	private GHPullRequest createMockPullRequest(int number, String title, GHIssueState state) throws IOException {
        GHPullRequest pr = mock(GHPullRequest.class);
        when(pr.getNumber()).thenReturn(number);
        when(pr.getTitle()).thenReturn(title);
        when(pr.getBody()).thenReturn("PR body");
        when(pr.getState()).thenReturn(state);
        when(pr.getUpdatedAt()).thenReturn(new Date());
        when(pr.getHtmlUrl()).thenReturn(new URL("https://github.com/test/repo/pull/" + number));

        GHRepository repo = mock(GHRepository.class);
        when(repo.getFullName()).thenReturn("test/repo");
        when(pr.getRepository()).thenReturn(repo);

        GHCommitPointer base = mock(GHCommitPointer.class);
        when(base.getRef()).thenReturn("main");
        when(pr.getBase()).thenReturn(base);

        GHCommitPointer head = mock(GHCommitPointer.class);
        when(head.getRef()).thenReturn("feature-branch");
        when(pr.getHead()).thenReturn(head);

        GHUser user = mock(GHUser.class);
//        when(user.getLogin()).thenReturn("testuser");
//        when(user.getName()).thenReturn("Test User");
//        when(user.getEmail()).thenReturn("test@example.com");
        when(pr.getUser()).thenReturn(user);

        when(pr.getAdditions()).thenReturn(100);
        when(pr.getDeletions()).thenReturn(50);
        when(pr.getCommits()).thenReturn(5);
        when(pr.getChangedFiles()).thenReturn(3);

        // Mock the PagedIterable for empty reviews
        @SuppressWarnings("unchecked")
        PagedIterable<GHPullRequestReview> mockReviews = mock(PagedIterable.class);

        // Mock the PagedIterator
        @SuppressWarnings("unchecked")
        PagedIterator<GHPullRequestReview> mockIterator = mock(PagedIterator.class);
        when(mockIterator.hasNext()).thenReturn(false);

        when(mockReviews.iterator()).thenReturn(mockIterator);
        when(pr.listReviews()).thenReturn(mockReviews);

        return pr;
    }

	private GHPullRequest createMockPullRequestWithReviews() throws IOException {
		GHPullRequest pr = createMockPullRequest(1, "PR with reviews", GHIssueState.OPEN);

		List<GHPullRequestReview> reviews = new ArrayList<>();

		GHPullRequestReview review1 = mock(GHPullRequestReview.class);
		when(review1.getState()).thenReturn(GHPullRequestReviewState.COMMENTED);
		when(review1.getSubmittedAt()).thenReturn(new Date(System.currentTimeMillis() - 3600000)); // 1 hour ago

		GHPullRequestReview review2 = mock(GHPullRequestReview.class);
		when(review2.getState()).thenReturn(GHPullRequestReviewState.APPROVED);
		when(review2.getSubmittedAt()).thenReturn(new Date(System.currentTimeMillis() - 7200000)); // 2 hours ago

		reviews.add(review1);
		reviews.add(review2);

		// Mock the PagedIterable with reviews
		@SuppressWarnings("unchecked")
        PagedIterable<GHPullRequestReview> mockReviews = new PagedIterable<GHPullRequestReview>() {
            @Override
            public @NotNull PagedIterator<GHPullRequestReview> _iterator(int pageSize) {
                return (PagedIterator<GHPullRequestReview>) reviews.iterator();
            }
        };
		when(pr.listReviews()).thenReturn(mockReviews);

		return pr;
	}

	private GHPullRequest createMockPullRequestWithNullUser() throws IOException {
		GHPullRequest pr = createMockPullRequest(1, "PR with null user", GHIssueState.OPEN);
		when(pr.getUser()).thenReturn(null);
		return pr;
	}
}
