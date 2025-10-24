package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitHubCommonHelperTest {

    private GitHubCommonHelper helper;

    @Mock
    private GHUser ghUser;

    @Mock
    private GHCommit.File ghFile;

    @Mock
    private GHPullRequest ghPullRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        helper = new GitHubCommonHelper();
    }

    @Test
    void testCreateUser() throws IOException {
        when(ghUser.getLogin()).thenReturn("testuser");
        when(ghUser.getName()).thenReturn("Test User");
        when(ghUser.getEmail()).thenReturn("test@example.com");

        User user = helper.createUser(ghUser);

        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertEquals("Test User", user.getDisplayName());
        assertEquals("test@example.com", user.getEmail());
    }

    @Test
    void testCreateUser_NullName() throws IOException {
        when(ghUser.getLogin()).thenReturn("testuser");
        when(ghUser.getName()).thenReturn(null);
        when(ghUser.getEmail()).thenReturn("test@example.com");

        User user = helper.createUser(ghUser);

        assertEquals("testuser", user.getDisplayName());
    }

    @Test
    void testMapGitHubStatus() {
        assertEquals("ADDED", helper.mapGitHubStatus("added"));
        assertEquals("DELETED", helper.mapGitHubStatus("removed"));
        assertEquals("RENAMED", helper.mapGitHubStatus("renamed"));
        assertEquals("MODIFIED", helper.mapGitHubStatus("modified"));
        assertEquals("MODIFIED", helper.mapGitHubStatus(null));
        assertEquals("MODIFIED", helper.mapGitHubStatus("unknown"));
    }

    @Test
    void testIsBinaryFile() {
        assertTrue(helper.isBinaryFile("image.jpg"));
        assertTrue(helper.isBinaryFile("document.pdf"));
        assertTrue(helper.isBinaryFile("archive.zip"));
        assertTrue(helper.isBinaryFile("program.exe"));
        assertFalse(helper.isBinaryFile("code.java"));
        assertFalse(helper.isBinaryFile("readme.txt"));
        assertFalse(helper.isBinaryFile(null));
    }

    @Test
    void testExtractLineNumbers() {
        String diff = "@@ -10,3 +10,5 @@\n context\n-removed\n+added1\n+added2\n context";
        List<Integer> lineNumbers = helper.extractLineNumbers(diff);

        assertFalse(lineNumbers.isEmpty());
        assertTrue(lineNumbers.contains(10));
    }

    @Test
    void testExtractLineNumbers_EmptyDiff() {
        List<Integer> lineNumbers = helper.extractLineNumbers("");
        assertTrue(lineNumbers.isEmpty());
    }

    @Test
    void testProcessFileChange() {
        when(ghFile.getFileName()).thenReturn("test.java");
        when(ghFile.getLinesAdded()).thenReturn(10);
        when(ghFile.getLinesDeleted()).thenReturn(5);
        when(ghFile.getStatus()).thenReturn("modified");
        when(ghFile.getPreviousFilename()).thenReturn(null);
        when(ghFile.getPatch()).thenReturn("@@ -1,5 +1,10 @@\n+added");

        GitHubCommonHelper.FileChangeStats stats = helper.processFileChange(ghFile);

        assertEquals(10, stats.getAdditions());
        assertEquals(5, stats.getDeletions());
        assertEquals(15, stats.getChanges());
        assertNotNull(stats.getFileChange());
        assertEquals("test.java", stats.getFileChange().getFilePath());
    }

    @Test
    void testSetPullRequestState_Open() throws IOException {
        when(ghPullRequest.getState()).thenReturn(GHIssueState.OPEN);

        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
        helper.setPullRequestState(builder, ghPullRequest);

        ScmMergeRequests mr = builder.build();
        assertEquals("OPEN", mr.getState());
        assertTrue(mr.isOpen());
    }

    @Test
    void testSetPullRequestState_Merged() throws IOException {
        when(ghPullRequest.getState()).thenReturn(GHIssueState.CLOSED);
        when(ghPullRequest.getMergedAt()).thenReturn(new Date());

        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
        helper.setPullRequestState(builder, ghPullRequest);

        ScmMergeRequests mr = builder.build();
        assertEquals("MERGED", mr.getState());
        assertTrue(mr.isClosed());
    }

    @Test
    void testSetPullRequestState_Closed() throws IOException {
        when(ghPullRequest.getState()).thenReturn(GHIssueState.CLOSED);
        when(ghPullRequest.getMergedAt()).thenReturn(null);

        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
        helper.setPullRequestState(builder, ghPullRequest);

        ScmMergeRequests mr = builder.build();
        assertEquals("CLOSED", mr.getState());
        assertTrue(mr.isClosed());
    }

    @Test
    void testExtractPullRequestStats() throws IOException {
        when(ghPullRequest.getAdditions()).thenReturn(100);
        when(ghPullRequest.getDeletions()).thenReturn(50);
        when(ghPullRequest.getCommits()).thenReturn(5);
        when(ghPullRequest.getChangedFiles()).thenReturn(3);

        GitHubCommonHelper.PullRequestStats stats = helper.extractPullRequestStats(ghPullRequest);

        assertEquals(150, stats.getLinesChanged());
        assertEquals(5, stats.getCommitCount());
        assertEquals(3, stats.getFilesChanged());
        assertEquals(100, stats.getAddedLines());
        assertEquals(50, stats.getRemovedLines());
    }

    @Test
    void testExtractPullRequestStats_Exception() throws IOException {
        when(ghPullRequest.getAdditions()).thenThrow(new RuntimeException("Error"));
        when(ghPullRequest.getNumber()).thenReturn(1);

        GitHubCommonHelper.PullRequestStats stats = helper.extractPullRequestStats(ghPullRequest);

        assertEquals(0, stats.getLinesChanged());
        assertEquals(0, stats.getCommitCount());
    }
}
