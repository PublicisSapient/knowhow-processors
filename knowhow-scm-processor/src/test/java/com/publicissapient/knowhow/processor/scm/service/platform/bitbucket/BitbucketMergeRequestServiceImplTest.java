package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.knowhow.processor.scm.util.wrapper.BitbucketParser;
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

	@Mock
	private BitbucketParser bitbucketParser;

	private BitbucketMergeRequestServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new BitbucketMergeRequestServiceImpl(bitbucketClient, commonHelper);
	}

	@Test
	void fetchMergeRequests_success() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "username:password";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketPullRequest pr = createBitbucketPullRequest();
		List<BitbucketClient.BitbucketPullRequest> prs = Arrays.asList(pr);

		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(prs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
				.thenReturn("diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createPullRequestStats());
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(bitbucketClient).fetchPullRequests(eq("owner"), eq("repo"), eq(branchName), eq("username"), eq("password"), eq(since), anyString());
	}

	@Test
	void fetchMergeRequests_emptyList() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "username:password";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(new ArrayList<>());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void fetchMergeRequests_withMergedState() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "username:password";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketPullRequest pr = createBitbucketPullRequest();
		pr.setState("MERGED");
		pr.setClosedOn("2024-01-01T10:00:00Z");
		List<BitbucketClient.BitbucketPullRequest> prs = Arrays.asList(pr);

		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(prs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
				.thenReturn("diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createPullRequestStats());
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("MERGED", result.get(0).getState());
		assertTrue(result.get(0).isClosed());
		assertNotNull(result.get(0).getMergedAt());
	}

	@Test
	void fetchMergeRequests_withClosedState() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "username:password";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketPullRequest pr = createBitbucketPullRequest();
		pr.setState("DECLINED");
		pr.setClosedOn("2024-01-01T10:00:00Z");
		List<BitbucketClient.BitbucketPullRequest> prs = Arrays.asList(pr);

		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(prs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
				.thenReturn("diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createPullRequestStats());
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("CLOSED", result.get(0).getState());
		assertTrue(result.get(0).isClosed());
	}

	@Test
	void fetchMergeRequests_withReviewers() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "username:password";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketPullRequest pr = createBitbucketPullRequest();
		BitbucketClient.BitbucketUser reviewer = createBitbucketUser("reviewer1", "Reviewer One");
		pr.setReviewers(Arrays.asList(reviewer));
		List<BitbucketClient.BitbucketPullRequest> prs = Arrays.asList(pr);

		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(prs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
				.thenReturn("diff content");
		when(bitbucketClient.getBitbucketParser(anyBoolean())).thenReturn(bitbucketParser);
		when(bitbucketParser.parsePRDiffToFileChanges(anyString())).thenReturn(createPullRequestStats());
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertNotNull(result.get(0).getReviewerUserIds());
		assertFalse(result.get(0).getReviewerUserIds().isEmpty());
	}

	@Test
	void fetchMergeRequests_diffFetchFailure() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
		String token = "username:password";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		BitbucketClient.BitbucketPullRequest pr = createBitbucketPullRequest();
		List<BitbucketClient.BitbucketPullRequest> prs = Arrays.asList(pr);

		when(bitbucketClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(prs);
		when(bitbucketClient.fetchPullRequestDiffs(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
				.thenThrow(new RuntimeException("Diff fetch failed"));
		when(commonHelper.createUser(anyString(), anyString(), isNull())).thenReturn(createUser());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(0, result.get(0).getAddedLines());
		assertEquals(0, result.get(0).getRemovedLines());
		assertEquals(0, result.get(0).getFilesChanged());
	}

	private BitbucketClient.BitbucketPullRequest createBitbucketPullRequest() {
		BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
		pr.setId(1L);
		pr.setTitle("Test PR");
		pr.setDescription("Test description");
		pr.setState("OPEN");
		pr.setCreatedOn("2024-01-01T10:00:00Z");
		pr.setUpdatedOn("2024-01-02T10:00:00Z");
		pr.setSelfLink("https://bitbucket.org/owner/repo/pull-requests/1");

		BitbucketClient.BitbucketUser author = createBitbucketUser("testuser", "Test User");
		pr.setAuthor(author);

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

		return pr;
	}

	private BitbucketClient.BitbucketUser createBitbucketUser(String username, String displayName) {
		BitbucketClient.BbUser user = new BitbucketClient.BbUser();
		user.setUsername(username);
		user.setDisplayName(displayName);

		BitbucketClient.BitbucketUser bitbucketUser = new BitbucketClient.BitbucketUser();
		bitbucketUser.setUser(user);

		return bitbucketUser;
	}

	private User createUser() {
		return User.builder().username("testuser").displayName("Test User").build();
	}

	private ScmMergeRequests.PullRequestStats createPullRequestStats() {
		return new ScmMergeRequests.PullRequestStats(10, 5, 3);
	}
}
