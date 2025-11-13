package com.publicissapient.knowhow.processor.scm.service.platform.azuredevops;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;

class AzureDevOpsCommonHelperTest {

    private AzureDevOpsCommonHelper helper;

    @BeforeEach
    void setUp() {
        helper = new AzureDevOpsCommonHelper();
    }

    @Test
    void createUser_WithDisplayName() {
        User user = helper.createUser("john@example.com", "John Doe");

        assertEquals("john@example.com", user.getUsername());
        assertEquals("John Doe", user.getDisplayName());
        assertEquals("john@example.com", user.getEmail());
    }

    @Test
    void createUser_WithNullDisplayName() {
        User user = helper.createUser("john@example.com", null);

        assertEquals("john@example.com", user.getUsername());
        assertEquals("john@example.com", user.getDisplayName());
        assertEquals("john@example.com", user.getEmail());
    }

    @Test
    void mapAzureDevOpsStatus_Add() {
        assertEquals("ADDED", helper.mapAzureDevOpsStatus("add"));
        assertEquals("ADDED", helper.mapAzureDevOpsStatus("ADD"));
    }

    @Test
    void mapAzureDevOpsStatus_Delete() {
        assertEquals("DELETED", helper.mapAzureDevOpsStatus("delete"));
        assertEquals("DELETED", helper.mapAzureDevOpsStatus("DELETE"));
    }

    @Test
    void mapAzureDevOpsStatus_Rename() {
        assertEquals("RENAMED", helper.mapAzureDevOpsStatus("rename"));
    }

    @Test
    void mapAzureDevOpsStatus_Edit() {
        assertEquals("MODIFIED", helper.mapAzureDevOpsStatus("edit"));
    }

    @Test
    void mapAzureDevOpsStatus_Null() {
        assertEquals("MODIFIED", helper.mapAzureDevOpsStatus(null));
    }

    @Test
    void mapAzureDevOpsStatus_Unknown() {
        assertEquals("MODIFIED", helper.mapAzureDevOpsStatus("unknown"));
    }

    @Test
    void isBinaryFile_ImageFiles() {
        assertTrue(helper.isBinaryFile("image.jpg"));
        assertTrue(helper.isBinaryFile("photo.PNG"));
        assertTrue(helper.isBinaryFile("icon.svg"));
    }

    @Test
    void isBinaryFile_ArchiveFiles() {
        assertTrue(helper.isBinaryFile("archive.zip"));
        assertTrue(helper.isBinaryFile("file.tar.gz"));
    }

    @Test
    void isBinaryFile_ExecutableFiles() {
        assertTrue(helper.isBinaryFile("app.exe"));
        assertTrue(helper.isBinaryFile("library.dll"));
        assertTrue(helper.isBinaryFile("MyClass.class"));
    }

    @Test
    void isBinaryFile_TextFiles() {
        assertFalse(helper.isBinaryFile("file.txt"));
        assertFalse(helper.isBinaryFile("code.java"));
    }

    @Test
    void isBinaryFile_Null() {
        assertFalse(helper.isBinaryFile(null));
    }

    @Test
    void extractLineNumbers_WithValidDiff() {
        String diff = "@@ -10,5 +15,6 @@\n context\n+added line\n-removed line\n context";

        List<Integer> lineNumbers = helper.extractLineNumbers(diff);

        assertFalse(lineNumbers.isEmpty());
        assertTrue(lineNumbers.contains(17));
        assertTrue(lineNumbers.contains(16));
    }

    @Test
    void extractLineNumbers_EmptyDiff() {
        List<Integer> lineNumbers = helper.extractLineNumbers("");

        assertTrue(lineNumbers.isEmpty());
    }

    @Test
    void extractLineNumbers_NullDiff() {
        List<Integer> lineNumbers = helper.extractLineNumbers(null);

        assertTrue(lineNumbers.isEmpty());
    }

    @Test
    void parseDiffContent_WithAdditionsAndDeletions() {
        String diff = "--- a/file.txt\n+++ b/file.txt\n@@ -1,3 +1,3 @@\n context\n-removed\n+added\n+another added";

        AzureDevOpsCommonHelper.DiffStats stats = helper.parseDiffContent(diff);

        assertEquals(2, stats.getAddedLines());
        assertEquals(1, stats.getRemovedLines());
    }

    @Test
    void parseDiffContent_EmptyDiff() {
        AzureDevOpsCommonHelper.DiffStats stats = helper.parseDiffContent("");

        assertEquals(0, stats.getAddedLines());
        assertEquals(0, stats.getRemovedLines());
    }

    @Test
    void parseDiffContent_NullDiff() {
        AzureDevOpsCommonHelper.DiffStats stats = helper.parseDiffContent(null);

        assertEquals(0, stats.getAddedLines());
        assertEquals(0, stats.getRemovedLines());
    }

    @Test
    void setPullRequestState_Completed() {
        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();

        helper.setPullRequestState(builder, "completed");
        ScmMergeRequests mr = builder.build();

        assertEquals(ScmMergeRequests.MergeRequestState.MERGED.name(), mr.getState());
        assertTrue(mr.isClosed());
    }

    @Test
    void setPullRequestState_Abandoned() {
        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();

        helper.setPullRequestState(builder, "abandoned");
        ScmMergeRequests mr = builder.build();

        assertEquals(ScmMergeRequests.MergeRequestState.CLOSED.name(), mr.getState());
        assertTrue(mr.isClosed());
    }

    @Test
    void setPullRequestState_Active() {
        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();

        helper.setPullRequestState(builder, "active");
        ScmMergeRequests mr = builder.build();

        assertEquals(ScmMergeRequests.MergeRequestState.OPEN.name(), mr.getState());
        assertTrue(mr.isOpen());
    }

    @Test
    void setMergeRequestTimestamps_WithClosedDate() {
        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
        Instant closedDate = Instant.parse("2024-01-15T10:30:00Z");

        helper.setMergeRequestTimestamps(builder, closedDate);
        ScmMergeRequests mr = builder.build();

        assertNotNull(mr.getMergedAt());
        assertNotNull(mr.getClosedDate());
    }

    @Test
    void setMergeRequestTimestamps_WithNullClosedDate() {
        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();

        helper.setMergeRequestTimestamps(builder, null);
        ScmMergeRequests mr = builder.build();

        assertNull(mr.getMergedAt());
        assertNull(mr.getClosedDate());
    }
}
