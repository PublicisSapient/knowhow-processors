package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BitbucketCommonHelperTest {

    private BitbucketCommonHelper helper;

    @BeforeEach
    void setUp() {
        helper = new BitbucketCommonHelper();
    }

    @Test
    void testCreateUser() {
        User user = helper.createUser("testuser", "Test User", "test@example.com");

        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertEquals("Test User", user.getDisplayName());
        assertEquals("test@example.com", user.getEmail());
    }

    @Test
    void testCreateUser_NullDisplayName() {
        User user = helper.createUser("testuser", null, "test@example.com");
        assertEquals("testuser", user.getDisplayName());
    }

    @Test
    void testMapBitbucketStatus() {
        assertEquals("ADDED", helper.mapBitbucketStatus("ADDED"));
        assertEquals("DELETED", helper.mapBitbucketStatus("REMOVED"));
        assertEquals("RENAMED", helper.mapBitbucketStatus("RENAMED"));
        assertEquals("MODIFIED", helper.mapBitbucketStatus("MODIFIED"));
        assertEquals("MODIFIED", helper.mapBitbucketStatus(null));
    }

    @Test
    void testIsBinaryFile() {
        assertTrue(helper.isBinaryFile("image.jpg"));
        assertTrue(helper.isBinaryFile("document.pdf"));
        assertFalse(helper.isBinaryFile("code.java"));
        assertFalse(helper.isBinaryFile(null));
    }

    @Test
    void testExtractLineNumbers() {
        String diff = "@@ -10,3 +10,5 @@\n context\n-removed\n+added";
        List<Integer> lineNumbers = helper.extractLineNumbers(diff);
        assertFalse(lineNumbers.isEmpty());
    }

    @Test
    void testParseDiffContent() {
        String diffContent = "+added\n-removed";
        BitbucketCommonHelper.DiffStats stats = helper.parseDiffContent(diffContent);
        assertEquals(1, stats.getAddedLines());
        assertEquals(1, stats.getRemovedLines());
    }

    @Test
    void testSetPullRequestState_Merged() {
        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
        helper.setPullRequestState(builder, "MERGED");
        ScmMergeRequests mr = builder.build();
        assertEquals("MERGED", mr.getState());
        assertTrue(mr.isClosed());
    }

    @Test
    void testSetPullRequestState_Open() {
        ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
        helper.setPullRequestState(builder, "OPEN");
        ScmMergeRequests mr = builder.build();
        assertEquals("OPEN", mr.getState());
        assertTrue(mr.isOpen());
    }

    @Test
    void testCredentialsParse() {
        BitbucketCommonHelper.Credentials creds = BitbucketCommonHelper.Credentials.parse("user:pass");
        assertEquals("user", creds.username());
        assertEquals("pass", creds.password());
    }

    @Test
    void testCredentialsParse_Invalid() {
        BitbucketCommonHelper.Credentials creds = BitbucketCommonHelper.Credentials.parse("invalid");
        assertNull(creds.username());
        assertNull(creds.password());
    }
}
