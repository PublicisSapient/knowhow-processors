package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
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

	private BitbucketCommitsServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new BitbucketCommitsServiceImpl(bitbucketClient, commonHelper);
	}

	@Test
	void fetchCommits_success() throws PlatformApiException {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "user:pass";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketCommit bbCommit = createBitbucketCommit();
		List<BitbucketClient.BitbucketCommit> bbCommits = Arrays.asList(bbCommit);

		when(bitbucketClient.fetchCommits(eq("owner"), eq("repo"), eq(branchName), eq("user"), eq("pass"), eq(since),
				anyString())).thenReturn(bbCommits);
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(bitbucketClient).fetchCommits(eq("owner"), eq("repo"), eq(branchName), eq("user"), eq("pass"), eq(since),
				anyString());
	}

	@Test
	void fetchCommits_emptyList() throws PlatformApiException {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "user:pass";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(new ArrayList<>());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void fetchCommits_withConversionError() throws PlatformApiException {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "user:pass";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketCommit bbCommit = new BitbucketClient.BitbucketCommit();
		bbCommit.setHash(null);
		List<BitbucketClient.BitbucketCommit> bbCommits = Arrays.asList(bbCommit);

		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(bbCommits);

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test
	void fetchCommits_withAuthorInfo() throws PlatformApiException {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "user:pass";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketCommit bbCommit = createBitbucketCommit();
		List<BitbucketClient.BitbucketCommit> bbCommits = Arrays.asList(bbCommit);

		User mockUser = createUser();
		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(bbCommits);
		when(commonHelper.createUser("testuser", "Test User", null)).thenReturn(mockUser);

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(commonHelper).createUser("testuser", "Test User", null);
	}

	@Test
	void fetchCommits_withParentInfo() throws PlatformApiException {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "user:pass";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketCommit bbCommit = createBitbucketCommit();
		bbCommit.setParents(Arrays.asList("parent1", "parent2"));
		List<BitbucketClient.BitbucketCommit> bbCommits = Arrays.asList(bbCommit);

		when(bitbucketClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(bbCommits);
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		ScmCommits commit = result.get(0);
		assertTrue(commit.getIsMergeCommit());
		assertEquals(2, commit.getParentShas().size());
	}

	private BitbucketClient.BitbucketCommit createBitbucketCommit() {
		BitbucketClient.BitbucketCommit commit = new BitbucketClient.BitbucketCommit();
		commit.setHash("abc123");
		commit.setMessage("Test commit");
		commit.setDate("2024-01-01T10:00:00Z");

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		BitbucketClient.BbUser user = new BitbucketClient.BbUser();
		user.setUsername("testuser");
		user.setDisplayName("Test User");
		author.setUser(user);
		commit.setAuthor(author);

		BitbucketClient.BitbucketCommitStats stats = new BitbucketClient.BitbucketCommitStats();
		stats.setAdditions(10);
		stats.setDeletions(5);
		commit.setStats(stats);

		commit.setParents(Arrays.asList("parent1"));

		return commit;
	}

	private User createUser() {
		return User.builder().username("testuser").displayName("Test User").build();
	}
}
