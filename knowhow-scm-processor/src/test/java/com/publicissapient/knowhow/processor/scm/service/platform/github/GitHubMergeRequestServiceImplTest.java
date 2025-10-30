package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.knowhow.processor.scm.client.github.GitHubClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubMergeRequestServiceImplTest {

	@Mock
	private GitHubClient gitHubClient;

	@Mock
	private GitHubCommonHelper commonHelper;

	@Mock
	private GHPullRequest ghPullRequest;

	@Mock
	private GHRepository ghRepository;

	@Mock
	private GHCommitPointer head;

	@Mock
	private GHCommitPointer base;

	@Mock
	private GHUser ghUser;

	private GitHubMergeRequestServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new GitHubMergeRequestServiceImpl(gitHubClient, commonHelper);
	}

	@Test
	void fetchMergeRequests_success() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = null;
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHPullRequest> ghPullRequests = Arrays.asList(ghPullRequest);

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghPullRequests);
		when(ghPullRequest.getNumber()).thenReturn(1);
		when(ghPullRequest.getRepository()).thenReturn(ghRepository);
		when(ghRepository.getFullName()).thenReturn("owner/repo");
		when(ghPullRequest.getTitle()).thenReturn("Test PR");
		when(ghPullRequest.getBody()).thenReturn("Test body");
		when(ghPullRequest.getHead()).thenReturn(head);
		when(head.getRef()).thenReturn("feature");
		when(ghPullRequest.getBase()).thenReturn(base);
		when(base.getRef()).thenReturn("main");
		when(ghPullRequest.getCreatedAt()).thenReturn(new Date());
		when(ghPullRequest.getUpdatedAt()).thenReturn(new Date());
		when(ghPullRequest.getHtmlUrl()).thenReturn(new URL("https://github.com/owner/repo/pull/1"));
		when(commonHelper.extractPullRequestStats(ghPullRequest))
				.thenReturn(new GitHubCommonHelper.PullRequestStats(10, 2, 3, 5, 5));
		when(commonHelper.getPrPickupTime(ghPullRequest)).thenReturn(null);

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(gitHubClient).fetchPullRequests(anyString(), anyString(), anyString(), any(), any());
	}

	@Test
	void fetchMergeRequests_withBranchFilter() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHPullRequest> ghPullRequests = Arrays.asList(ghPullRequest);

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghPullRequests);
		when(ghPullRequest.getBase()).thenReturn(base);
		when(base.getRef()).thenReturn("main");
		when(ghPullRequest.getNumber()).thenReturn(1);
		when(ghPullRequest.getRepository()).thenReturn(ghRepository);
		when(ghRepository.getFullName()).thenReturn("owner/repo");
		when(ghPullRequest.getTitle()).thenReturn("Test PR");
		when(ghPullRequest.getBody()).thenReturn("Test body");
		when(ghPullRequest.getHead()).thenReturn(head);
		when(head.getRef()).thenReturn("feature");
		when(ghPullRequest.getCreatedAt()).thenReturn(new Date());
		when(ghPullRequest.getUpdatedAt()).thenReturn(new Date());
		when(ghPullRequest.getHtmlUrl()).thenReturn(new URL("https://github.com/owner/repo/pull/1"));
		when(commonHelper.extractPullRequestStats(ghPullRequest))
				.thenReturn(new GitHubCommonHelper.PullRequestStats(10, 2, 3, 5, 5));
		when(commonHelper.getPrPickupTime(ghPullRequest)).thenReturn(null);

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertEquals(1, result.size());
	}

	@Test
	void fetchMergeRequests_emptyList() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = null;
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(new ArrayList<>());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void fetchMergeRequests_ioException() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = null;
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenThrow(new IOException("API error"));

		assertThrows(PlatformApiException.class,
				() -> service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until));
	}

	@Test
	void fetchMergeRequests_withConversionError() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = null;
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHPullRequest> ghPullRequests = Arrays.asList(ghPullRequest);

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghPullRequests);
		when(ghPullRequest.getNumber()).thenReturn(1);
		when(ghPullRequest.getRepository()).thenThrow(new RuntimeException("Conversion error"));

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test
	void fetchMergeRequests_branchFilterNoMatch() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = "develop";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHPullRequest> ghPullRequests = Arrays.asList(ghPullRequest);

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghPullRequests);
		when(ghPullRequest.getBase()).thenReturn(base);
		when(base.getRef()).thenReturn("main");

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertEquals(0, result.size());
	}
}
