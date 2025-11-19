package com.publicissapient.knowhow.processor.scm.service.strategy;

import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.service.platform.CommitsServiceLocator;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformCommitsService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
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
class RestApiCommitDataFetchStrategyTest {

	@Mock
	private CommitsServiceLocator commitsServiceLocator;

	@Mock
	private GitPlatformCommitsService commitsService;

	private RestApiCommitDataFetchStrategy strategy;

	@BeforeEach
	void setUp() {
		strategy = new RestApiCommitDataFetchStrategy(commitsServiceLocator);
	}

	@Test
	void fetchCommits_success() throws Exception {
		String toolType = "github";
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = "main";
		CommitDataFetchStrategy.RepositoryCredentials credentials = new CommitDataFetchStrategy.RepositoryCredentials.Builder().token("token123").username("user").password("pass").build();
		LocalDateTime since = LocalDateTime.now().minusDays(7);

		List<ScmCommits> commits = Arrays.asList(new ScmCommits());

		when(commitsServiceLocator.getCommitsService(toolType)).thenReturn(commitsService);
		when(commitsService.fetchCommits(anyString(), any(), anyString(), anyString(), any(), any()))
				.thenReturn(commits);

		List<ScmCommits> result = strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo, branchName, credentials,
				since);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(commitsServiceLocator).getCommitsService(toolType);
		verify(commitsService).fetchCommits(anyString(), any(), anyString(), anyString(), any(), any());
	}

	@Test
	void fetchCommits_bitbucket() throws Exception {
		String toolType = "bitbucket";
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner",
				"repo", "https://bitbucket.org", "https://bitbucket.org/owner/repo.git");
		String branchName = "main";
        CommitDataFetchStrategy.RepositoryCredentials credentials = new CommitDataFetchStrategy.RepositoryCredentials.Builder().token("token123").username("user").password("pass").build();
		LocalDateTime since = LocalDateTime.now().minusDays(7);

		List<ScmCommits> commits = Arrays.asList(new ScmCommits());

		when(commitsServiceLocator.getCommitsService(toolType)).thenReturn(commitsService);
		when(commitsService.fetchCommits(anyString(), any(), anyString(), eq("user:token123"), any(), any()))
				.thenReturn(commits);

		List<ScmCommits> result = strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo, branchName, credentials,
				since);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(commitsService).fetchCommits(anyString(), any(), anyString(), eq("user:token123"), any(), any());
	}

	@Test
	void fetchCommits_serviceNotFound() {
		String toolType = "unknown";
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = "main";
        CommitDataFetchStrategy.RepositoryCredentials credentials = new CommitDataFetchStrategy.RepositoryCredentials.Builder().token("token123").username("user").password("pass").build();
		LocalDateTime since = LocalDateTime.now().minusDays(7);

		when(commitsServiceLocator.getCommitsService(toolType)).thenReturn(null);

		assertThrows(DataProcessingException.class,
				() -> strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo, branchName, credentials, since));
	}

	@Test
	void fetchCommits_exception() throws Exception {
		String toolType = "github";
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = "main";
        CommitDataFetchStrategy.RepositoryCredentials credentials = new CommitDataFetchStrategy.RepositoryCredentials.Builder().token("token123").username("user").password("pass").build();
		LocalDateTime since = LocalDateTime.now().minusDays(7);

		when(commitsServiceLocator.getCommitsService(toolType)).thenReturn(commitsService);
		when(commitsService.fetchCommits(anyString(), any(), anyString(), anyString(), any(), any()))
				.thenThrow(new RuntimeException("API error"));

		assertThrows(DataProcessingException.class,
				() -> strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo, branchName, credentials, since));
	}

	@Test
	void supports_true() {
		String toolType = "github";
		when(commitsServiceLocator.getCommitsService(toolType)).thenReturn(commitsService);

		boolean result = strategy.supports("https://github.com/owner/repo.git", toolType);

		assertTrue(result);
	}

	@Test
	void supports_false() {
		String toolType = "unknown";
		when(commitsServiceLocator.getCommitsService(toolType)).thenReturn(null);

		boolean result = strategy.supports("https://github.com/owner/repo.git", toolType);

		assertFalse(result);
	}

	@Test
	void getStrategyName() {
		assertEquals("REST_API", strategy.getStrategyName());
	}
}
