package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

import com.publicissapient.knowhow.processor.scm.client.bitbucket.BitbucketClient;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.bson.types.ObjectId;
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
class BitbucketRepositoryServiceImplTest {

	@Mock
	private BitbucketClient bitbucketClient;

	private BitbucketRepositoryServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new BitbucketRepositoryServiceImpl(bitbucketClient);
	}

	@Test
	void fetchRepositories_success() throws Exception {
		ScanRequest scanRequest = createScanRequest();
		ScmRepos repo = createScmRepo();
		List<ScmRepos> expectedRepos = Arrays.asList(repo);

		when(bitbucketClient.fetchRepositories(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(expectedRepos);

		List<ScmRepos> result = service.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(bitbucketClient).fetchRepositories(
				eq("https://bitbucket.org"),
				eq("testuser"),
				eq("token123"),
				eq(scanRequest.getSince()),
				eq(scanRequest.getConnectionId())
		);
	}

	@Test
	void fetchRepositories_emptyList() throws Exception {
		ScanRequest scanRequest = createScanRequest();

		when(bitbucketClient.fetchRepositories(anyString(), anyString(), anyString(), any(), any()))
				.thenReturn(new ArrayList<>());

		List<ScmRepos> result = service.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void fetchRepositories_throwsException() {
		ScanRequest scanRequest = createScanRequest();

		when(bitbucketClient.fetchRepositories(anyString(), anyString(), anyString(), any(), any()))
				.thenThrow(new RuntimeException("API error"));

		assertThrows(PlatformApiException.class, () -> service.fetchRepositories(scanRequest));
	}

	private ScanRequest createScanRequest() {
		ScanRequest request = ScanRequest.builder().build();
		request.setBaseUrl("https://bitbucket.org");
		request.setUsername("testuser");
		request.setToken("token123");
		request.setSince(LocalDateTime.now().minusDays(7));
		request.setConnectionId(new ObjectId());
		return request;
	}

	private ScmRepos createScmRepo() {
		ScmRepos repo = ScmRepos.builder().build();
		repo.setRepositoryName("test-repo");
		repo.setUrl("https://bitbucket.org/owner/test-repo");
		return repo;
	}
}
