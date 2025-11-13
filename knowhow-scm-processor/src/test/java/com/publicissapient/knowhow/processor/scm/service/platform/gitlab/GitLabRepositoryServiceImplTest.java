package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.knowhow.processor.scm.client.gitlab.GitLabClient;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.bson.types.ObjectId;
import org.gitlab4j.api.GitLabApiException;
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
class GitLabRepositoryServiceImplTest {

	@Mock
	private GitLabClient gitLabClient;

	private GitLabRepositoryServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new GitLabRepositoryServiceImpl(gitLabClient);
	}

	@Test
	void fetchRepositories_success() throws Exception {
		ScanRequest scanRequest = createScanRequest();
		List<ScmRepos> repos = Arrays.asList(createRepo());

		when(gitLabClient.fetchRepositories(anyString(), any(), anyString(), any())).thenReturn(repos);

		List<ScmRepos> result = service.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(gitLabClient).fetchRepositories(anyString(), any(), anyString(), any());
	}

	@Test
	void fetchRepositories_emptyList() throws Exception {
		ScanRequest scanRequest = createScanRequest();

		when(gitLabClient.fetchRepositories(anyString(), any(), anyString(), any())).thenReturn(new ArrayList<>());

		List<ScmRepos> result = service.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void fetchRepositories_gitLabApiException() throws Exception {
		ScanRequest scanRequest = createScanRequest();

		when(gitLabClient.fetchRepositories(anyString(), any(), anyString(), any()))
				.thenThrow(new GitLabApiException("API error"));

		assertThrows(PlatformApiException.class, () -> service.fetchRepositories(scanRequest));
	}

	private ScanRequest createScanRequest() {
		ScanRequest request = ScanRequest.builder().build();
		request.setToken("token123");
		request.setSince(LocalDateTime.now().minusDays(7));
		request.setBaseUrl("https://gitlab.com");
		request.setConnectionId(new ObjectId());
		return request;
	}

	private ScmRepos createRepo() {
		ScmRepos repo = ScmRepos.builder().build();
		repo.setRepositoryName("test-repo");
		repo.setUrl("https://gitlab.com/owner/test-repo");
		repo.setLastUpdated(System.currentTimeMillis());
		repo.setBranchList(Arrays.asList(createBranch()));
		return repo;
	}

	private ScmBranch createBranch() {
		ScmBranch branch = ScmBranch.builder().build();
		branch.setName("main");
		branch.setLastUpdatedAt(System.currentTimeMillis());
		return branch;
	}
}
