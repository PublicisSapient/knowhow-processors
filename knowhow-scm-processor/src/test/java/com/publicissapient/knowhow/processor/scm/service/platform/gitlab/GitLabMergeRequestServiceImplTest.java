package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.knowhow.processor.scm.client.gitlab.GitLabClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Author;
import org.gitlab4j.api.models.MergeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitLabMergeRequestServiceImplTest {

	@Mock
	private GitLabClient gitLabClient;

	@Mock
	private GitLabCommonHelper commonHelper;

	@Mock
	private MergeRequest mergeRequest;

	private GitLabMergeRequestServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new GitLabMergeRequestServiceImpl(gitLabClient, commonHelper);
	}

	@Test
	void fetchMergeRequests_success() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner",
				"repo", "https://gitlab.com", "https://gitlab.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<MergeRequest> mergeRequests = Arrays.asList(mergeRequest);

		when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(),
				anyString())).thenReturn(mergeRequests);
		when(mergeRequest.getIid()).thenReturn(1L);
		when(mergeRequest.getTitle()).thenReturn("Test MR");
		when(mergeRequest.getDescription()).thenReturn("Test description");
		when(mergeRequest.getState()).thenReturn("opened");
		when(mergeRequest.getSourceBranch()).thenReturn("feature");
		when(mergeRequest.getTargetBranch()).thenReturn("main");
		when(mergeRequest.getCreatedAt()).thenReturn(new Date());
		when(mergeRequest.getUpdatedAt()).thenReturn(new Date());
		when(mergeRequest.getWebUrl()).thenReturn("https://gitlab.com/owner/repo/-/merge_requests/1");
		when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
				.thenReturn(0L);
		when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
				.thenReturn(new ArrayList<>());
		when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
				.thenReturn(new ArrayList<>());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(gitLabClient).fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(),
				anyString());
	}

	@Test
	void fetchMergeRequests_emptyList() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner",
				"repo", "https://gitlab.com", "https://gitlab.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(),
				anyString())).thenReturn(new ArrayList<>());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void fetchMergeRequests_gitLabApiException() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner",
				"repo", "https://gitlab.com", "https://gitlab.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(),
				anyString())).thenThrow(new GitLabApiException("API error"));

		assertThrows(PlatformApiException.class,
				() -> service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until));
	}

	@Test
	void fetchMergeRequests_withConversionError() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner",
				"repo", "https://gitlab.com", "https://gitlab.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<MergeRequest> mergeRequests = Arrays.asList(mergeRequest);

		when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(),
				anyString())).thenReturn(mergeRequests);
		when(mergeRequest.getIid()).thenReturn(1L);
		when(mergeRequest.getTitle()).thenThrow(new RuntimeException("Conversion error"));

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test
	void fetchMergeRequests_withAuthor() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner",
				"repo", "https://gitlab.com", "https://gitlab.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<MergeRequest> mergeRequests = Arrays.asList(mergeRequest);
		Author author = new Author();
		author.setUsername("testuser");
		author.setEmail("test@example.com");
		author.setName("Test User");

		when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(),
				anyString())).thenReturn(mergeRequests);
		when(mergeRequest.getIid()).thenReturn(1L);
		when(mergeRequest.getTitle()).thenReturn("Test MR");
		when(mergeRequest.getDescription()).thenReturn("Test description");
		when(mergeRequest.getState()).thenReturn("opened");
		when(mergeRequest.getSourceBranch()).thenReturn("feature");
		when(mergeRequest.getTargetBranch()).thenReturn("main");
		when(mergeRequest.getCreatedAt()).thenReturn(new Date());
		when(mergeRequest.getUpdatedAt()).thenReturn(new Date());
		when(mergeRequest.getWebUrl()).thenReturn("https://gitlab.com/owner/repo/-/merge_requests/1");
		when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
				.thenReturn(0L);
		when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
				.thenReturn(new ArrayList<>());
		when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
				.thenReturn(new ArrayList<>());

		List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since,
				until);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(commonHelper).setMergeRequestAuthor(any(), eq(mergeRequest));
	}
}
