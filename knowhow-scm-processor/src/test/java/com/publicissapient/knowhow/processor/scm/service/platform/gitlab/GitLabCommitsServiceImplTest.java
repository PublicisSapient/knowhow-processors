package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.knowhow.processor.scm.client.gitlab.GitLabClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CommitStats;
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
class GitLabCommitsServiceImplTest {

	@Mock
	private GitLabClient gitLabClient;

	@Mock
	private GitLabCommonHelper commonHelper;

	@Mock
	private Commit commit;

	@Mock
	private CommitStats commitStats;

	private GitLabCommitsServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new GitLabCommitsServiceImpl(gitLabClient, commonHelper);
	}

	@Test
	void fetchCommits_success() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner",
				"repo", "https://gitlab.com", "https://gitlab.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<Commit> commits = Arrays.asList(commit);

		when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
				.thenReturn(commits);
		when(commit.getId()).thenReturn("abc123");
		when(commit.getMessage()).thenReturn("Test commit");
		when(commit.getCreatedAt()).thenReturn(new Date());
		when(commit.getAuthorName()).thenReturn("testuser");
		when(commit.getAuthorEmail()).thenReturn("test@example.com");
		when(commit.getCommitterName()).thenReturn("testuser");
		when(commit.getCommittedDate()).thenReturn(new Date());
		when(commit.getParentIds()).thenReturn(Arrays.asList("parent1"));
		when(commit.getStats()).thenReturn(commitStats);
		when(commitStats.getAdditions()).thenReturn(10);
		when(commitStats.getDeletions()).thenReturn(5);
		when(commitStats.getTotal()).thenReturn(15);
		when(commonHelper.createUser(anyString(), anyString(), anyString())).thenReturn(createUser());
		when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(new ArrayList<>());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(gitLabClient).fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(),
				anyString());
	}

	@Test
	void fetchCommits_emptyList() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner",
				"repo", "https://gitlab.com", "https://gitlab.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
				.thenReturn(new ArrayList<>());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void fetchCommits_gitLabApiException() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner",
				"repo", "https://gitlab.com", "https://gitlab.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
				.thenThrow(new GitLabApiException("API error"));

		assertThrows(PlatformApiException.class,
				() -> service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until));
	}

	@Test
	void fetchCommits_withConversionError() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner",
				"repo", "https://gitlab.com", "https://gitlab.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<Commit> commits = Arrays.asList(commit);

		when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
				.thenReturn(commits);
		when(commit.getId()).thenReturn("abc123");
		when(commit.getMessage()).thenThrow(new RuntimeException("Conversion error"));

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test
	void fetchCommits_withMergeCommit() throws Exception {
		String toolConfigId = "507f1f77bcf86cd799439011";
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner",
				"repo", "https://gitlab.com", "https://gitlab.com/owner/repo.git");
		String branchName = "main";
		String token = "token123";
		LocalDateTime since = LocalDateTime.now().minusDays(7);
		LocalDateTime until = LocalDateTime.now();

		List<Commit> commits = Arrays.asList(commit);

		when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
				.thenReturn(commits);
		when(commit.getId()).thenReturn("abc123");
		when(commit.getMessage()).thenReturn("Merge commit");
		when(commit.getCreatedAt()).thenReturn(new Date());
		when(commit.getAuthorName()).thenReturn("testuser");
		when(commit.getAuthorEmail()).thenReturn(null);
		when(commit.getCommitterName()).thenReturn(null);
		when(commit.getCommittedDate()).thenReturn(new Date());
		when(commit.getParentIds()).thenReturn(Arrays.asList("parent1", "parent2"));
		when(commit.getStats()).thenReturn(commitStats);
		when(commitStats.getAdditions()).thenReturn(10);
		when(commitStats.getDeletions()).thenReturn(5);
		when(commitStats.getTotal()).thenReturn(15);
		when(commonHelper.createUser(anyString(), any(), any())).thenReturn(createUser());
		when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(new ArrayList<>());

		List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

		assertNotNull(result);
		assertEquals(1, result.size());
		ScmCommits scmCommit = result.get(0);
		assertTrue(scmCommit.getIsMergeCommit());
		assertEquals(2, scmCommit.getParentShas().size());
	}

	private User createUser() {
		return User.builder().username("testuser").displayName("Test User").email("test@example.com").build();
	}
}
