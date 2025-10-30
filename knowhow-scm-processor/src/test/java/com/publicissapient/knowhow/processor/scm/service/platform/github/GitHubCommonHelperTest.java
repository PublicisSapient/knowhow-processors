package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubCommonHelperTest {

	@Mock
	private GHUser ghUser;

	@Mock
	private GHCommit.File ghFile;

	@Mock
	private GHPullRequest ghPullRequest;

	@Mock
	private GHPullRequestReview ghReview;

	private GitHubCommonHelper helper;

	@BeforeEach
	void setUp() {
		helper = new GitHubCommonHelper();
	}

	@Test
	void createUser_success() throws IOException {
		when(ghUser.getLogin()).thenReturn("testuser");
		when(ghUser.getName()).thenReturn("Test User");
		when(ghUser.getEmail()).thenReturn("test@example.com");

		User result = helper.createUser(ghUser);

		assertNotNull(result);
		assertEquals("testuser", result.getUsername());
		assertEquals("Test User", result.getDisplayName());
		assertEquals("test@example.com", result.getEmail());
	}

	@Test
	void createUser_withoutName() throws IOException {
		when(ghUser.getLogin()).thenReturn("testuser");
		when(ghUser.getName()).thenReturn(null);
		when(ghUser.getEmail()).thenReturn("test@example.com");

		User result = helper.createUser(ghUser);

		assertNotNull(result);
		assertEquals("testuser", result.getDisplayName());
	}

	@Test
	void mapGitHubStatus_added() {
		assertEquals("ADDED", helper.mapGitHubStatus("added"));
	}

	@Test
	void mapGitHubStatus_removed() {
		assertEquals("DELETED", helper.mapGitHubStatus("removed"));
	}

	@Test
	void mapGitHubStatus_renamed() {
		assertEquals("RENAMED", helper.mapGitHubStatus("renamed"));
	}

	@Test
	void mapGitHubStatus_modified() {
		assertEquals("MODIFIED", helper.mapGitHubStatus("modified"));
	}

	@Test
	void mapGitHubStatus_null() {
		assertEquals("MODIFIED", helper.mapGitHubStatus(null));
	}

	@Test
	void isBinaryFile_jpg() {
		assertTrue(helper.isBinaryFile("image.jpg"));
	}

	@Test
	void isBinaryFile_textFile() {
		assertFalse(helper.isBinaryFile("file.txt"));
	}

	@Test
	void isBinaryFile_null() {
		assertFalse(helper.isBinaryFile(null));
	}

	@Test
	void extractLineNumbers_validDiff() {
		String diff = "@@ -10,5 +10,6 @@\n-old line\n+new line\n context";
		List<Integer> result = helper.extractLineNumbers(diff);
		assertNotNull(result);
		assertFalse(result.isEmpty());
	}

	@Test
	void extractLineNumbers_emptyDiff() {
		List<Integer> result = helper.extractLineNumbers("");
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void extractLineNumbers_nullDiff() {
		List<Integer> result = helper.extractLineNumbers(null);
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void processFileChange_success() {
		when(ghFile.getFileName()).thenReturn("test.java");
		when(ghFile.getLinesAdded()).thenReturn(10);
		when(ghFile.getLinesDeleted()).thenReturn(5);
		when(ghFile.getStatus()).thenReturn("modified");
		when(ghFile.getPreviousFilename()).thenReturn(null);
		when(ghFile.getPatch()).thenReturn(null);

		GitHubCommonHelper.FileChangeStats result = helper.processFileChange(ghFile);

		assertNotNull(result);
		assertEquals(10, result.getAdditions());
		assertEquals(5, result.getDeletions());
		assertEquals(15, result.getChanges());
		assertNotNull(result.getFileChange());
	}

	@Test
	void setPullRequestState_merged() throws IOException {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		when(ghPullRequest.getState()).thenReturn(GHIssueState.CLOSED);
		when(ghPullRequest.getMergedAt()).thenReturn(new Date());

		helper.setPullRequestState(builder, ghPullRequest);

		ScmMergeRequests result = builder.build();
		assertEquals(ScmMergeRequests.MergeRequestState.MERGED.name(), result.getState());
		assertTrue(result.isClosed());
	}

	@Test
	void setPullRequestState_closed() throws IOException {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		when(ghPullRequest.getState()).thenReturn(GHIssueState.CLOSED);
		when(ghPullRequest.getMergedAt()).thenReturn(null);

		helper.setPullRequestState(builder, ghPullRequest);

		ScmMergeRequests result = builder.build();
		assertEquals(ScmMergeRequests.MergeRequestState.CLOSED.name(), result.getState());
		assertTrue(result.isClosed());
	}

	@Test
	void setPullRequestState_open() throws IOException {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		when(ghPullRequest.getState()).thenReturn(GHIssueState.OPEN);

		helper.setPullRequestState(builder, ghPullRequest);

		ScmMergeRequests result = builder.build();
		assertEquals(ScmMergeRequests.MergeRequestState.OPEN.name(), result.getState());
		assertTrue(result.isOpen());
	}

	@Test
	void setMergeAndCloseTimestamps_withDates() {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		Date mergedAt = new Date();
		Date closedAt = new Date();
		when(ghPullRequest.getMergedAt()).thenReturn(mergedAt);
		when(ghPullRequest.getClosedAt()).thenReturn(closedAt);

		helper.setMergeAndCloseTimestamps(builder, ghPullRequest);

		ScmMergeRequests result = builder.build();
		assertNotNull(result.getMergedAt());
		assertNotNull(result.getClosedDate());
	}

	@Test
	void setPullRequestAuthor_success() throws IOException {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		when(ghPullRequest.getUser()).thenReturn(ghUser);
		when(ghUser.getLogin()).thenReturn("testuser");
		when(ghUser.getName()).thenReturn("Test User");
		when(ghUser.getEmail()).thenReturn("test@example.com");

		helper.setPullRequestAuthor(builder, ghPullRequest);

		ScmMergeRequests result = builder.build();
		assertNotNull(result.getAuthorId());
		assertEquals("testuser", result.getAuthorUserId());
	}

	@Test
	void extractPullRequestStats_success() throws IOException {
		when(ghPullRequest.getAdditions()).thenReturn(10);
		when(ghPullRequest.getDeletions()).thenReturn(5);
		when(ghPullRequest.getCommits()).thenReturn(3);
		when(ghPullRequest.getChangedFiles()).thenReturn(2);

		GitHubCommonHelper.PullRequestStats result = helper.extractPullRequestStats(ghPullRequest);

		assertNotNull(result);
		assertEquals(15, result.getLinesChanged());
		assertEquals(3, result.getCommitCount());
		assertEquals(2, result.getFilesChanged());
		assertEquals(10, result.getAddedLines());
		assertEquals(5, result.getRemovedLines());
	}

	@Test
	void extractPullRequestStats_withException() throws IOException {
		when(ghPullRequest.getAdditions()).thenThrow(new IOException("API error"));

		GitHubCommonHelper.PullRequestStats result = helper.extractPullRequestStats(ghPullRequest);

		assertNotNull(result);
		assertEquals(0, result.getLinesChanged());
	}
}
