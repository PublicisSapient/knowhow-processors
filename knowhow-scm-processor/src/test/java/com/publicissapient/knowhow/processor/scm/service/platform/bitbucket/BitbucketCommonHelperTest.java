package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BitbucketCommonHelperTest {

	private BitbucketCommonHelper helper;

	@BeforeEach
	void setUp() {
		helper = new BitbucketCommonHelper();
	}

	@Test
	void createUser_withAllFields() {
		User user = helper.createUser("testuser", "Test User", "test@example.com");

		assertNotNull(user);
		assertEquals("testuser", user.getUsername());
		assertEquals("Test User", user.getDisplayName());
		assertEquals("test@example.com", user.getEmail());
	}

	@Test
	void createUser_withNullDisplayName() {
		User user = helper.createUser("testuser", null, "test@example.com");

		assertNotNull(user);
		assertEquals("testuser", user.getUsername());
		assertEquals("testuser", user.getDisplayName());
	}

	@Test
	void mapBitbucketStatus_added() {
		assertEquals("ADDED", helper.mapBitbucketStatus("ADDED"));
	}

	@Test
	void mapBitbucketStatus_removed() {
		assertEquals("DELETED", helper.mapBitbucketStatus("REMOVED"));
	}

	@Test
	void mapBitbucketStatus_renamed() {
		assertEquals("RENAMED", helper.mapBitbucketStatus("RENAMED"));
	}

	@Test
	void mapBitbucketStatus_modified() {
		assertEquals("MODIFIED", helper.mapBitbucketStatus("MODIFIED"));
	}

	@Test
	void mapBitbucketStatus_null() {
		assertEquals("MODIFIED", helper.mapBitbucketStatus(null));
	}

	@Test
	void isBinaryFile_imageFile() {
		assertTrue(helper.isBinaryFile("test.jpg"));
		assertTrue(helper.isBinaryFile("test.png"));
	}

	@Test
	void isBinaryFile_archiveFile() {
		assertTrue(helper.isBinaryFile("test.zip"));
		assertTrue(helper.isBinaryFile("test.jar"));
	}

	@Test
	void isBinaryFile_textFile() {
		assertFalse(helper.isBinaryFile("test.java"));
		assertFalse(helper.isBinaryFile("test.txt"));
	}

	@Test
	void isBinaryFile_null() {
		assertFalse(helper.isBinaryFile(null));
	}

	@Test
	void parseDiffContent_withChanges() {
		String diff = "+added line\n-removed line\n unchanged line";
		BitbucketCommonHelper.DiffStats stats = helper.parseDiffContent(diff);

		assertEquals(1, stats.getAddedLines());
		assertEquals(1, stats.getRemovedLines());
	}

	@Test
	void parseDiffContent_emptyDiff() {
		BitbucketCommonHelper.DiffStats stats = helper.parseDiffContent("");

		assertEquals(0, stats.getAddedLines());
		assertEquals(0, stats.getRemovedLines());
	}

	@Test
	void parseDiffContent_null() {
		BitbucketCommonHelper.DiffStats stats = helper.parseDiffContent(null);

		assertEquals(0, stats.getAddedLines());
		assertEquals(0, stats.getRemovedLines());
	}

	@Test
	void extractLineNumbers_withHunk() {
		String diff = "@@ -1,3 +1,4 @@\n+added line\n-removed line\n unchanged";
		List<Integer> lineNumbers = helper.extractLineNumbers(diff);

		assertNotNull(lineNumbers);
		assertFalse(lineNumbers.isEmpty());
	}

	@Test
	void extractLineNumbers_emptyDiff() {
		List<Integer> lineNumbers = helper.extractLineNumbers("");

		assertNotNull(lineNumbers);
		assertTrue(lineNumbers.isEmpty());
	}

	@Test
	void setPullRequestState_merged() {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		helper.setPullRequestState(builder, "MERGED");
		ScmMergeRequests mr = builder.build();

		assertEquals("MERGED", mr.getState());
		assertTrue(mr.isClosed());
	}

	@Test
	void setPullRequestState_declined() {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		helper.setPullRequestState(builder, "DECLINED");
		ScmMergeRequests mr = builder.build();

		assertEquals("CLOSED", mr.getState());
		assertTrue(mr.isClosed());
	}

	@Test
	void setPullRequestState_open() {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		helper.setPullRequestState(builder, "OPEN");
		ScmMergeRequests mr = builder.build();

		assertEquals("OPEN", mr.getState());
		assertTrue(mr.isOpen());
	}

	@Test
	void setMergeRequestTimestamps_withClosedDate() {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		Instant closedOn = Instant.now();
		helper.setMergeRequestTimestamps(builder, null, closedOn);
		ScmMergeRequests mr = builder.build();

		assertNotNull(mr.getMergedAt());
		assertEquals(closedOn.toEpochMilli(), mr.getClosedDate());
	}

	@Test
	void setMergeRequestTimestamps_withUpdatedDate() {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		Instant updatedOn = Instant.now();
		helper.setMergeRequestTimestamps(builder, updatedOn, null);
		ScmMergeRequests mr = builder.build();

		assertEquals(updatedOn.toEpochMilli(), mr.getClosedDate());
	}

	@Test
	void credentials_parse_valid() {
		BitbucketCommonHelper.Credentials creds = BitbucketCommonHelper.Credentials.parse("user:pass");

		assertEquals("user", creds.username());
		assertEquals("pass", creds.password());
	}

	@Test
	void credentials_parse_withColon() {
		BitbucketCommonHelper.Credentials creds = BitbucketCommonHelper.Credentials.parse("user:pass:word");

		assertEquals("user", creds.username());
		assertEquals("pass:word", creds.password());
	}

	@Test
	void credentials_parse_null() {
		BitbucketCommonHelper.Credentials creds = BitbucketCommonHelper.Credentials.parse(null);

		assertNull(creds.username());
		assertNull(creds.password());
	}

	@Test
	void credentials_parse_noColon() {
		BitbucketCommonHelper.Credentials creds = BitbucketCommonHelper.Credentials.parse("invalid");

		assertNull(creds.username());
		assertNull(creds.password());
	}
}
