package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.knowhow.processor.scm.util.wrapper.BitbucketParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BitbucketCommitsServiceImplTest {

	@Mock
	private BitbucketClient bitbucketClient;

	@Mock
	private BitbucketCommonHelper commonHelper;

	@Mock
	private BitbucketParser bitbucketParser;

	private BitbucketCommitsServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new BitbucketCommitsServiceImpl(bitbucketClient, commonHelper);
	}

	@Test
	void fetchCommits_success() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "username:password";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketCommit bbCommit = createBitbucketCommit();
		List<BitbucketClient.BitbucketCommit> bbCommits = Arrays.asList(bbCommit);

		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(bbCommits);
		when(bitbucketClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn("diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parseDiffToFileChanges(anyString())).thenReturn(new ArrayList<>());
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(bitbucketClient).fetchCommits(eq("owner"), eq("repo"), eq(branchName), eq("username"), eq("password"), eq(since), anyString());
	}

	@Test
	void fetchCommits_emptyList() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "username:password";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(new ArrayList<>());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void fetchCommits_withMergeCommit() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "username:password";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketCommit bbCommit = createBitbucketCommit();
		bbCommit.setParents(Arrays.asList("parent1", "parent2"));

		List<BitbucketClient.BitbucketCommit> bbCommits = Arrays.asList(bbCommit);

		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(bbCommits);
		when(bitbucketClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn("diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parseDiffToFileChanges(anyString())).thenReturn(new ArrayList<>());
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertTrue(result.get(0).getIsMergeCommit());
	}

	@Test
	void fetchCommits_withStats() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "username:password";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketCommit bbCommit = createBitbucketCommit();
		BitbucketClient.BitbucketCommitStats stats = new BitbucketClient.BitbucketCommitStats();
		stats.setAdditions(10);
		stats.setDeletions(5);
		stats.setTotal(15);
		bbCommit.setStats(stats);

		List<BitbucketClient.BitbucketCommit> bbCommits = Arrays.asList(bbCommit);

		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(bbCommits);
		when(bitbucketClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn("diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parseDiffToFileChanges(anyString())).thenReturn(new ArrayList<>());
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(10, result.get(0).getAddedLines());
		assertEquals(5, result.get(0).getRemovedLines());
		assertEquals(15, result.get(0).getChangedLines());
	}

	@Test
	void fetchCommits_withFileChanges() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "username:password";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketCommit bbCommit = createBitbucketCommit();
		List<BitbucketClient.BitbucketCommit> bbCommits = Arrays.asList(bbCommit);

		ScmCommits.FileChange fileChange = ScmCommits.FileChange.builder()
				.filePath("test.java")
				.addedLines(5)
				.removedLines(3)
				.build();
		List<ScmCommits.FileChange> fileChanges = Arrays.asList(fileChange);

		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(bbCommits);
		when(bitbucketClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn("diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parseDiffToFileChanges(anyString())).thenReturn(fileChanges);
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(1, result.get(0).getFileChanges().size());
		assertEquals(5, result.get(0).getAddedLines());
		assertEquals(3, result.get(0).getRemovedLines());
	}

	@Test
	void fetchCommits_diffFetchFailure() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "username:password";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketCommit bbCommit = createBitbucketCommit();
		List<BitbucketClient.BitbucketCommit> bbCommits = Arrays.asList(bbCommit);

		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(bbCommits);
		when(bitbucketClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenThrow(new RuntimeException("Diff fetch failed"));
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertTrue(result.get(0).getFileChanges().isEmpty());
	}

	private BitbucketClient.BitbucketCommit createBitbucketCommit() {
		BitbucketClient.BitbucketCommit commit = new BitbucketClient.BitbucketCommit();
		commit.setHash("abc123");
		commit.setMessage("Test commit");
		commit.setDate("2024-01-01T10:00:00Z");

		BitbucketClient.BbUser user = new BitbucketClient.BbUser();
		user.setUsername("testuser");
		user.setDisplayName("Test User");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		author.setUser(user);

		commit.setAuthor(author);
		commit.setParents(new ArrayList<>());

		return commit;
	}

	private User createUser() {
		return User.builder().username("testuser").displayName("Test User").build();
	}
}
