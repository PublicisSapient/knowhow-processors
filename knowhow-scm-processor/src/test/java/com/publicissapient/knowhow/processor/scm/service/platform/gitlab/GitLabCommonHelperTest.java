package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.MergeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitLabCommonHelperTest {

    private GitLabCommonHelper helper;

    @Mock
    private Diff diff;

    @Mock
    private MergeRequest mergeRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        helper = new GitLabCommonHelper();
    }

    @Test
    void testCreateUser() {
        User user = helper.createUser("testuser", "test@example.com", "Test User");

        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("Test User", user.getDisplayName());
    }

    @Test
    void testCreateUser_NullEmail() {
        User user = helper.createUser("testuser", null, "Test User");

        assertEquals("Test User", user.getEmail());
    }

    @Test
    void testMapGitLabStatus() {
        assertEquals("ADDED", helper.mapGitLabStatus("new"));
        assertEquals("DELETED", helper.mapGitLabStatus("deleted"));
        assertEquals("RENAMED", helper.mapGitLabStatus("renamed"));
        assertEquals("MODIFIED", helper.mapGitLabStatus("modified"));
        assertEquals("MODIFIED", helper.mapGitLabStatus(null));
        assertEquals("MODIFIED", helper.mapGitLabStatus("unknown"));
    }

    @Test
    void testIsBinaryFile() {
        assertTrue(helper.isBinaryFile("image.jpg"));
        assertTrue(helper.isBinaryFile("document.pdf"));
        assertTrue(helper.isBinaryFile("archive.zip"));
        assertFalse(helper.isBinaryFile("code.java"));
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
    void testParseDiffContent() {
        String diffContent = "+added line\n-removed line\n context";
        GitLabCommonHelper.DiffStats stats = helper.parseDiffContent(diffContent);

        assertEquals(1, stats.getAddedLines());
        assertEquals(1, stats.getRemovedLines());
    }

    @Test
    void testParseDiffContent_Empty() {
        GitLabCommonHelper.DiffStats stats = helper.parseDiffContent("");
        assertEquals(0, stats.getAddedLines());
        assertEquals(0, stats.getRemovedLines());
    }

    @Test
    void testSetMergeRequestState_Open() {
        when(mergeRequest.getClosedAt()).thenReturn(null);

        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
        helper.setMergeRequestState(builder, mergeRequest);

        ScmMergeRequests mr = builder.build();
        assertTrue(mr.isOpen());
    }

    @Test
    void testSetMergeRequestState_Closed() {
        when(mergeRequest.getClosedAt()).thenReturn(new Date());

        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
        helper.setMergeRequestState(builder, mergeRequest);

        ScmMergeRequests mr = builder.build();
        assertTrue(mr.isClosed());
    }

    @Test
    void testConvertDiffToFileChange() {
        when(diff.getNewPath()).thenReturn("test.java");
        when(diff.getOldPath()).thenReturn(null);
        when(diff.getDiff()).thenReturn("+added\n-removed");
        when(diff.getNewFile()).thenReturn(false);
        when(diff.getDeletedFile()).thenReturn(false);
        when(diff.getRenamedFile()).thenReturn(false);

        ScmCommits.FileChange fileChange = helper.convertDiffToFileChange(diff);

        assertNotNull(fileChange);
        assertEquals("test.java", fileChange.getFilePath());
        assertEquals("MODIFIED", fileChange.getChangeType());
    }

    @Test
    void testConvertDiffToFileChange_Null() {
        ScmCommits.FileChange fileChange = helper.convertDiffToFileChange(null);
        assertNull(fileChange);
    }
}
