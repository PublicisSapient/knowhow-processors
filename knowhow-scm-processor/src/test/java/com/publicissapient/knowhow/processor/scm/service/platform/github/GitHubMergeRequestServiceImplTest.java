package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.knowhow.processor.scm.client.github.GitHubClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
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
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHPullRequest> ghPullRequests = Arrays.asList(ghPullRequest);

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghPullRequests);
		when(ghPullRequest.getNumber()).thenReturn(1);
		when(ghPullRequest.getTitle()).thenReturn("Test PR");
		when(ghPullRequest.getBody()).thenReturn("Test description");
		when(ghPullRequest.getRepository()).thenReturn(ghRepository);
		when(ghRepository.getFullName()).thenReturn("owner/repo");
		when(ghPullRequest.getHead()).thenReturn(head);
		when(head.getRef()).thenReturn("feature");
		when(ghPullRequest.getBase()).thenReturn(base);
		when(base.getRef()).thenReturn("main");
		when(ghPullRequest.getUpdatedAt()).thenReturn(new Date());
		when(ghPullRequest.getHtmlUrl()).thenReturn(new URL("https://github.com/owner/repo/pull/1"));

		GitHubCommonHelper.PullRequestStats stats = new GitHubCommonHelper.PullRequestStats(100, 5, 10, 60, 40);
		when(commonHelper.extractPullRequestStats(ghPullRequest)).thenReturn(stats);
		when(commonHelper.getPrPickupTime(ghPullRequest)).thenReturn(0L);

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, null, "token", since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(commonHelper).setPullRequestState(any(), eq(ghPullRequest));
		verify(commonHelper).setMergeAndCloseTimestamps(any(), eq(ghPullRequest));
		verify(commonHelper).setPullRequestAuthor(any(), eq(ghPullRequest));
	}

	@Test
	void fetchMergeRequests_withBranchFilter() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHPullRequest> ghPullRequests = Arrays.asList(ghPullRequest);

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghPullRequests);
		when(ghPullRequest.getBase()).thenReturn(base);
		when(base.getRef()).thenReturn("main");
		when(ghPullRequest.getNumber()).thenReturn(1);
		when(ghPullRequest.getTitle()).thenReturn("Test PR");
		when(ghPullRequest.getBody()).thenReturn("Test description");
		when(ghPullRequest.getRepository()).thenReturn(ghRepository);
		when(ghRepository.getFullName()).thenReturn("owner/repo");
		when(ghPullRequest.getHead()).thenReturn(head);
		when(head.getRef()).thenReturn("feature");
		when(ghPullRequest.getUpdatedAt()).thenReturn(new Date());
		when(ghPullRequest.getHtmlUrl()).thenReturn(new URL("https://github.com/owner/repo/pull/1"));

		GitHubCommonHelper.PullRequestStats stats = new GitHubCommonHelper.PullRequestStats(100, 5, 10, 60, 40);
		when(commonHelper.extractPullRequestStats(ghPullRequest)).thenReturn(stats);
		when(commonHelper.getPrPickupTime(ghPullRequest)).thenReturn(0L);

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", "token", since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
	}

	@Test
	void fetchMergeRequests_branchFilterExcludesPR() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHPullRequest> ghPullRequests = Arrays.asList(ghPullRequest);

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghPullRequests);
		when(ghPullRequest.getBase()).thenReturn(base);
		when(base.getRef()).thenReturn("main");

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, "develop", "token", since, until);

		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test
	void fetchMergeRequests_emptyList() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(new ArrayList<>());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, null, "token", since, until);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void fetchMergeRequests_ioException() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenThrow(new IOException("API error"));

		assertThrows(PlatformApiException.class,
				() -> service.fetchMergeRequests(toolConfigId, gitUrlInfo, null, "token", since, until));
	}

	@Test
	void fetchMergeRequests_withConversionError() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHPullRequest> ghPullRequests = Arrays.asList(ghPullRequest);

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghPullRequests);
		when(ghPullRequest.getRepository()).thenThrow(new RuntimeException("Conversion error"));

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, null, "token", since, until);

		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test
	void fetchMergeRequests_branchFilterWithException() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHPullRequest> ghPullRequests = Arrays.asList(ghPullRequest);

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghPullRequests);
		when(ghPullRequest.getBase()).thenThrow(new RuntimeException("Error getting base"));
		when(ghPullRequest.getNumber()).thenReturn(1);

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", "token", since, until);

		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test
	void fetchMergeRequests_emptyBranchName() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHPullRequest> ghPullRequests = Arrays.asList(ghPullRequest);

		when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghPullRequests);
		when(ghPullRequest.getNumber()).thenReturn(1);
		when(ghPullRequest.getTitle()).thenReturn("Test PR");
		when(ghPullRequest.getBody()).thenReturn("Test description");
		when(ghPullRequest.getRepository()).thenReturn(ghRepository);
		when(ghRepository.getFullName()).thenReturn("owner/repo");
		when(ghPullRequest.getHead()).thenReturn(head);
		when(head.getRef()).thenReturn("feature");
		when(ghPullRequest.getBase()).thenReturn(base);
		when(base.getRef()).thenReturn("main");
		when(ghPullRequest.getUpdatedAt()).thenReturn(new Date());
		when(ghPullRequest.getHtmlUrl()).thenReturn(new URL("https://github.com/owner/repo/pull/1"));

		GitHubCommonHelper.PullRequestStats stats = new GitHubCommonHelper.PullRequestStats(100, 5, 10, 60, 40);
		when(commonHelper.extractPullRequestStats(ghPullRequest)).thenReturn(stats);
		when(commonHelper.getPrPickupTime(ghPullRequest)).thenReturn(0L);

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, "   ", "token", since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
	}
}
