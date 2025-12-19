package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.knowhow.processor.scm.client.github.GitHubClient;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHRepository;
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
class GitHubRepositoryServiceImplTest {

	@Mock
	private GitHubClient gitHubClient;

	@Mock
	private GitUrlParser gitUrlParser;

	@Mock
	private GHRepository ghRepository;

	private GitHubRepositoryServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new GitHubRepositoryServiceImpl(gitHubClient, gitUrlParser);
	}

	@Test
	void fetchRepositories_success() throws Exception {
		ScanRequest scanRequest = createScanRequest();
		List<GHRepository> ghRepositories = Arrays.asList(ghRepository);
		List<ScmBranch> branches = Arrays.asList(createBranch());
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");

		when(gitHubClient.fetchRepositories(anyString(), any())).thenReturn(ghRepositories);
		when(ghRepository.getHtmlUrl()).thenReturn(new URL("https://github.com/owner/repo"));
		when(ghRepository.getName()).thenReturn("repo");
		when(ghRepository.getUpdatedAt()).thenReturn(new Date());
		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(gitUrlInfo);
		when(gitHubClient.fetchBranchesWithLastCommitDate(anyString(), anyString(), anyString(), any()))
				.thenReturn(branches);

		List<ScmRepos> result = service.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(gitHubClient).fetchRepositories(anyString(), any());
	}

	@Test
	void fetchRepositories_emptyBranches() throws Exception {
		ScanRequest scanRequest = createScanRequest();
		List<GHRepository> ghRepositories = Arrays.asList(ghRepository);
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");

		when(gitHubClient.fetchRepositories(anyString(), any())).thenReturn(ghRepositories);
		when(ghRepository.getHtmlUrl()).thenReturn(new URL("https://github.com/owner/repo"));
		when(ghRepository.getName()).thenReturn("repo");
		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(gitUrlInfo);
		when(gitHubClient.fetchBranchesWithLastCommitDate(anyString(), anyString(), anyString(), any()))
				.thenReturn(null);

		List<ScmRepos> result = service.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test
	void fetchRepositories_emptyList() throws Exception {
		ScanRequest scanRequest = createScanRequest();

		when(gitHubClient.fetchRepositories(anyString(), any())).thenReturn(new ArrayList<>());

		List<ScmRepos> result = service.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void fetchRepositories_exception() throws Exception {
		ScanRequest scanRequest = createScanRequest();

		when(gitHubClient.fetchRepositories(anyString(), any())).thenThrow(new RuntimeException("API error"));

		assertThrows(PlatformApiException.class, () -> service.fetchRepositories(scanRequest));
	}

	@Test
	void fetchRepositories_branchFetchError() throws Exception {
		ScanRequest scanRequest = createScanRequest();
		List<GHRepository> ghRepositories = Arrays.asList(ghRepository);
		GitUrlParser.GitUrlInfo gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner",
				"repo", "https://github.com", "https://github.com/owner/repo.git");

		when(gitHubClient.fetchRepositories(anyString(), any())).thenReturn(ghRepositories);
		when(ghRepository.getHtmlUrl()).thenReturn(new URL("https://github.com/owner/repo"));
		when(ghRepository.getName()).thenReturn("repo");
		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(gitUrlInfo);
		when(gitHubClient.fetchBranchesWithLastCommitDate(anyString(), anyString(), anyString(), any()))
				.thenThrow(new IOException("Branch fetch error"));

		assertThrows(PlatformApiException.class, () -> service.fetchRepositories(scanRequest));
	}

	private ScanRequest createScanRequest() {
		ScanRequest request = ScanRequest.builder().build();
		request.setToken("token123");
		request.setSince(LocalDateTime.now().minusDays(7));
		request.setToolType("GitHub");
		request.setUsername("testuser");
		request.setConnectionId(new ObjectId());
		return request;
	}

	private ScmBranch createBranch() {
		ScmBranch branch = ScmBranch.builder().build();
		branch.setName("main");
		branch.setLastUpdatedAt(System.currentTimeMillis());
		return branch;
	}
}
