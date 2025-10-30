package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.gitlab4j.api.models.Author;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.MergeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitLabCommonHelperTest {

	private GitLabCommonHelper helper;

	@BeforeEach
	void setUp() {
		helper = new GitLabCommonHelper();
	}

	@Test
	void createUser_withEmail() {
		User result = helper.createUser("testuser", "test@example.com", "Test User");

		assertNotNull(result);
		assertEquals("testuser", result.getUsername());
		assertEquals("test@example.com", result.getEmail());
		assertEquals("Test User", result.getDisplayName());
	}

	@Test
	void createUser_withoutEmail() {
		User result = helper.createUser("testuser", null, "Test User");

		assertNotNull(result);
		assertEquals("testuser", result.getUsername());
		assertEquals("Test User", result.getEmail());
		assertEquals("Test User", result.getDisplayName());
	}

	@Test
	void mapGitLabStatus_new() {
		assertEquals("ADDED", helper.mapGitLabStatus("new"));
	}

	@Test
	void mapGitLabStatus_deleted() {
		assertEquals("DELETED", helper.mapGitLabStatus("deleted"));
	}

	@Test
	void mapGitLabStatus_renamed() {
		assertEquals("RENAMED", helper.mapGitLabStatus("renamed"));
	}

	@Test
	void mapGitLabStatus_modified() {
		assertEquals("MODIFIED", helper.mapGitLabStatus("modified"));
	}

	@Test
	void mapGitLabStatus_null() {
		assertEquals("MODIFIED", helper.mapGitLabStatus(null));
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
	void parseDiffContent_validDiff() {
		String diff = "+added line\n-removed line\n context";
		GitLabCommonHelper.DiffStats result = helper.parseDiffContent(diff);

		assertNotNull(result);
		assertEquals(1, result.getAddedLines());
		assertEquals(1, result.getRemovedLines());
	}

	@Test
	void parseDiffContent_emptyDiff() {
		GitLabCommonHelper.DiffStats result = helper.parseDiffContent("");

		assertNotNull(result);
		assertEquals(0, result.getAddedLines());
		assertEquals(0, result.getRemovedLines());
	}

	@Test
	void parseDiffContent_nullDiff() {
		GitLabCommonHelper.DiffStats result = helper.parseDiffContent(null);

		assertNotNull(result);
		assertEquals(0, result.getAddedLines());
		assertEquals(0, result.getRemovedLines());
	}

	@Test
	void setMergeRequestState_closed() {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		MergeRequest mr = new MergeRequest();
		mr.setClosedAt(new Date());

		helper.setMergeRequestState(builder, mr);

		ScmMergeRequests result = builder.build();
		assertTrue(result.isClosed());
	}

	@Test
	void setMergeRequestState_open() {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		MergeRequest mr = new MergeRequest();
		mr.setClosedAt(null);

		helper.setMergeRequestState(builder, mr);

		ScmMergeRequests result = builder.build();
		assertTrue(result.isOpen());
	}

	@Test
	void setMergeRequestTimestamps_withDates() {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		MergeRequest mr = new MergeRequest();
		mr.setMergedAt(new Date());
		mr.setClosedAt(new Date());

		helper.setMergeRequestTimestamps(builder, mr);

		ScmMergeRequests result = builder.build();
		assertNotNull(result.getMergedAt());
		assertNotNull(result.getClosedDate());
	}

	@Test
	void setMergeRequestAuthor_success() {
		ScmMergeRequests.ScmMergeRequestsBuilder builder = ScmMergeRequests.builder();
		MergeRequest mr = new MergeRequest();
		Author author = new Author();
		author.setUsername("testuser");
		author.setEmail("test@example.com");
		author.setName("Test User");
		mr.setAuthor(author);

		helper.setMergeRequestAuthor(builder, mr);

		ScmMergeRequests result = builder.build();
		assertEquals("testuser", result.getAuthorUserId());
		assertNotNull(result.getAuthorId());
	}

	@Test
	void convertDiffToFileChange_success() {
		Diff diff = new Diff();
		diff.setNewPath("test.java");
		diff.setOldPath("test.java");
		diff.setNewFile(false);
		diff.setDeletedFile(false);
		diff.setRenamedFile(false);
		diff.setDiff("+added line\n-removed line");

		ScmCommits.FileChange result = helper.convertDiffToFileChange(diff);

		assertNotNull(result);
		assertEquals("test.java", result.getFilePath());
		assertEquals("MODIFIED", result.getChangeType());
		assertEquals(1, result.getAddedLines());
		assertEquals(1, result.getRemovedLines());
	}

	@Test
	void convertDiffToFileChange_null() {
		ScmCommits.FileChange result = helper.convertDiffToFileChange(null);
		assertNull(result);
	}

	@Test
	void convertDiffToFileChange_newFile() {
		Diff diff = new Diff();
		diff.setNewPath("test.java");
		diff.setNewFile(true);
		diff.setDiff("+added line");

		ScmCommits.FileChange result = helper.convertDiffToFileChange(diff);

		assertNotNull(result);
		assertEquals("ADDED", result.getChangeType());
	}

	@Test
	void convertDiffToFileChange_deletedFile() {
		Diff diff = new Diff();
		diff.setOldPath("test.java");
		diff.setDeletedFile(true);
		diff.setDiff("-removed line");

		ScmCommits.FileChange result = helper.convertDiffToFileChange(diff);

		assertNotNull(result);
		assertEquals("DELETED", result.getChangeType());
	}
}
