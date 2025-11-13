package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.knowhow.processor.scm.client.github.GitHubClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHUser;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubCommitsServiceImplTest {

	@Mock
	private GitHubClient gitHubClient;

	@Mock
	private GitHubCommonHelper commonHelper;

	@Mock
	private GHCommit ghCommit;

	@Mock
	private GHCommit.ShortInfo shortInfo;

	@Mock
	private GHUser ghUser;

	private GitHubCommitsServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new GitHubCommitsServiceImpl(gitHubClient, commonHelper);
	}

	@Test
	void fetchCommits_success() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHCommit> ghCommits = Arrays.asList(ghCommit);

		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghCommits);
		when(ghCommit.getSHA1()).thenReturn("abc123");
		when(ghCommit.getCommitShortInfo()).thenReturn(shortInfo);
		when(shortInfo.getMessage()).thenReturn("Test commit");
		when(ghCommit.getCommitDate()).thenReturn(new Date());
		when(ghCommit.getAuthor()).thenReturn(ghUser);
		when(ghUser.getLogin()).thenReturn("testuser");
		when(commonHelper.createUser(ghUser)).thenReturn(createUser());
		when(ghCommit.getParentSHA1s()).thenReturn(Arrays.asList("parent1"));
		when(ghCommit.getFiles()).thenReturn(new ArrayList<>());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
	}

	@Test
	void fetchCommits_emptyList() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(new ArrayList<>());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void fetchCommits_ioException() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenThrow(new IOException("API error"));

		assertThrows(PlatformApiException.class,
				() -> service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until));
	}

	@Test
	void fetchCommits_withConversionError() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHCommit> ghCommits = Arrays.asList(ghCommit);

		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghCommits);
		when(ghCommit.getCommitShortInfo()).thenThrow(new RuntimeException("Conversion error"));

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test
	void fetchCommits_withMergeCommit() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "repo", "https://github.com", "https://github.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<GHCommit> ghCommits = Arrays.asList(ghCommit);

		when(gitHubClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(ghCommits);
		when(ghCommit.getSHA1()).thenReturn("abc123");
		when(ghCommit.getCommitShortInfo()).thenReturn(shortInfo);
		when(shortInfo.getMessage()).thenReturn("Merge commit");
		when(ghCommit.getCommitDate()).thenReturn(new Date());
		when(ghCommit.getAuthor()).thenReturn(ghUser);
		when(ghUser.getLogin()).thenReturn("testuser");
		when(commonHelper.createUser(ghUser)).thenReturn(createUser());
		when(ghCommit.getParentSHA1s()).thenReturn(Arrays.asList("parent1", "parent2"));
		when(ghCommit.getFiles()).thenReturn(new ArrayList<>());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		ScmCommits commit = result.get(0);
		assertTrue(commit.getIsMergeCommit());
		assertEquals(2, commit.getParentShas().size());
	}

	private User createUser() {
		return User.builder().username("testuser").displayName("Test User").build();
	}
}
