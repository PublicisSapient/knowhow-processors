package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
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
class BitbucketMergeRequestServiceImplTest {

	@Mock
	private BitbucketClient bitbucketClient;

	@Mock
	private BitbucketCommonHelper commonHelper;

	private BitbucketMergeRequestServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new BitbucketMergeRequestServiceImpl(bitbucketClient, commonHelper);
	}

	@Test
	void fetchMergeRequests_success() throws PlatformApiException {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "user:pass";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketPullRequest bbPr = createBitbucketPullRequest();
		List<BitbucketClient.BitbucketPullRequest> bbPrs = Arrays.asList(bbPr);

		when(bitbucketClient.fetchPullRequests(eq("owner"), eq("repo"), eq(branchName), eq("user"), eq("pass"),
				eq(since), anyString())).thenReturn(bbPrs);
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(bitbucketClient).fetchPullRequests(eq("owner"), eq("repo"), eq(branchName), eq("user"), eq("pass"),
				eq(since), anyString());
	}

	@Test
	void fetchMergeRequests_emptyList() throws PlatformApiException {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "user:pass";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(new ArrayList<>());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void fetchMergeRequests_withConversionError() throws PlatformApiException {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "user:pass";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketPullRequest bbPr = new BitbucketClient.BitbucketPullRequest();
		bbPr.setId(null);
		List<BitbucketClient.BitbucketPullRequest> bbPrs = Arrays.asList(bbPr);

		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(bbPrs);

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test
	void fetchMergeRequests_withAuthorInfo() throws PlatformApiException {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "user:pass";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketPullRequest bbPr = createBitbucketPullRequest();
		List<BitbucketClient.BitbucketPullRequest> bbPrs = Arrays.asList(bbPr);

		User mockUser = createUser();
		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(bbPrs);
		when(commonHelper.createUser("testuser", "Test User", null)).thenReturn(mockUser);

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(commonHelper).createUser("testuser", "Test User", null);
	}

	@Test
	void fetchMergeRequests_withTimestamps() throws PlatformApiException {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "user:pass";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketPullRequest bbPr = createBitbucketPullRequest();
		bbPr.setCreatedOn("2024-01-01T10:00:00Z");
		bbPr.setUpdatedOn("2024-01-02T10:00:00Z");
		bbPr.setClosedOn("2024-01-03T10:00:00Z");
		List<BitbucketClient.BitbucketPullRequest> bbPrs = Arrays.asList(bbPr);

		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
				anyString())).thenReturn(bbPrs);
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(commonHelper).setMergeRequestTimestamps(any(), any(), any());
	}

	private BitbucketClient.BitbucketPullRequest createBitbucketPullRequest() {
		BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
		pr.setId(123L);
		pr.setTitle("Test PR");
		pr.setDescription("Test description");
		pr.setState("OPEN");
		pr.setCreatedOn("2024-01-01T10:00:00Z");
		pr.setUpdatedOn("2024-01-02T10:00:00Z");
		pr.setSelfLink("https://bitbucket.org/owner/repo/pull-requests/123");

		BitbucketClient.BitbucketBranch source = new BitbucketClient.BitbucketBranch();
		BitbucketClient.BbBranch sourceBranch = new BitbucketClient.BbBranch();
		sourceBranch.setName("feature");
		source.setBranch(sourceBranch);
		pr.setSource(source);

		BitbucketClient.BitbucketBranch destination = new BitbucketClient.BitbucketBranch();
		BitbucketClient.BbBranch destBranch = new BitbucketClient.BbBranch();
		destBranch.setName("main");
		destination.setBranch(destBranch);
		pr.setDestination(destination);

		BitbucketClient.BitbucketUser author = new BitbucketClient.BitbucketUser();
		BitbucketClient.BbUser user = new BitbucketClient.BbUser();
		user.setUsername("testuser");
		user.setDisplayName("Test User");
		author.setUser(user);
		pr.setAuthor(author);

		return pr;
	}

	private User createUser() {
		return User.builder().username("testuser").displayName("Test User").build();
	}
}
