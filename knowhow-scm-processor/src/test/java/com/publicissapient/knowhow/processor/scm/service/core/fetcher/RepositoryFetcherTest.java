package com.publicissapient.knowhow.processor.scm.service.core.fetcher;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.dto.ScanResult;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.core.PersistenceService;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformRepositoryService;
import com.publicissapient.knowhow.processor.scm.service.platform.RepositoryServiceLocator;
import com.publicissapient.kpidashboard.common.model.scm.ScmConnectionTraceLog;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RepositoryFetcherTest {

	@Mock
	private RepositoryServiceLocator repositoryServiceLocator;

	@Mock
	private PersistenceService persistenceService;

	@Mock
	private GitPlatformRepositoryService platformService;

	private RepositoryFetcher repositoryFetcher;

	@Before
	public void setUp() {
		repositoryFetcher = new RepositoryFetcher(repositoryServiceLocator, persistenceService);
		ReflectionTestUtils.setField(repositoryFetcher, "firstScanFromMonths", 6);
	}

	@Test
	public void testFetchRepositories_Success() throws PlatformApiException {
		ScanRequest scanRequest = createScanRequest();
		List<ScmRepos> repos = Arrays.asList(createRepo("repo1"), createRepo("repo2"));

		when(persistenceService.getScmConnectionTraceLog(anyString())).thenReturn(null);
		when(repositoryServiceLocator.getRepositoryService(anyString())).thenReturn(platformService);
		when(platformService.fetchRepositories(any(ScanRequest.class))).thenReturn(repos);

		ScanResult result = repositoryFetcher.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertTrue(result.isSuccess());
		assertEquals(2, result.getRepositoriesFound());
		verify(persistenceService).saveRepositoryData(repos);
	}

	@Test
	public void testFetchRepositories_NoRepositoriesFound() throws PlatformApiException {
		ScanRequest scanRequest = createScanRequest();
		List<ScmRepos> emptyRepos = List.of();

		when(persistenceService.getScmConnectionTraceLog(anyString())).thenReturn(null);
		when(repositoryServiceLocator.getRepositoryService(anyString())).thenReturn(platformService);
		when(platformService.fetchRepositories(any(ScanRequest.class))).thenReturn(emptyRepos);

		ScanResult result = repositoryFetcher.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertFalse(result.isSuccess());
		assertEquals(0, result.getRepositoriesFound());
		verify(persistenceService, never()).saveRepositoryData(anyList());
	}

	@Test
	public void testFetchRepositories_WithExistingTraceLog() throws PlatformApiException {
		ScanRequest scanRequest = createScanRequest();
		ScmConnectionTraceLog traceLog = createTraceLog(true, System.currentTimeMillis());
		List<ScmRepos> repos = Arrays.asList(createRepo("repo1"));

		when(persistenceService.getScmConnectionTraceLog(anyString())).thenReturn(traceLog);
		when(repositoryServiceLocator.getRepositoryService(anyString())).thenReturn(platformService);
		when(platformService.fetchRepositories(any(ScanRequest.class))).thenReturn(repos);

		ScanResult result = repositoryFetcher.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertTrue(result.isSuccess());
		assertEquals(1, result.getRepositoriesFound());
		verify(persistenceService).saveRepositoryData(repos);
	}

	@Test
	public void testFetchRepositories_WithFailedTraceLog() throws PlatformApiException {
		ScanRequest scanRequest = createScanRequest();
		ScmConnectionTraceLog traceLog = createTraceLog(false, System.currentTimeMillis());
		List<ScmRepos> repos = Arrays.asList(createRepo("repo1"));

		when(persistenceService.getScmConnectionTraceLog(anyString())).thenReturn(traceLog);
		when(repositoryServiceLocator.getRepositoryService(anyString())).thenReturn(platformService);
		when(platformService.fetchRepositories(any(ScanRequest.class))).thenReturn(repos);

		ScanResult result = repositoryFetcher.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertTrue(result.isSuccess());
		assertEquals(1, result.getRepositoriesFound());
	}

	@Test
	public void testFetchRepositories_DefaultFirstScanFrom() throws PlatformApiException {
		ScanRequest scanRequest = createScanRequest();
		List<ScmRepos> repos = Arrays.asList(createRepo("repo1"));

		when(persistenceService.getScmConnectionTraceLog(anyString())).thenReturn(null);
		when(repositoryServiceLocator.getRepositoryService(anyString())).thenReturn(platformService);
		when(platformService.fetchRepositories(any(ScanRequest.class))).thenReturn(repos);

		ScanResult result = repositoryFetcher.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertTrue(result.isSuccess());
		assertNotNull(scanRequest.getSince());
	}

	private ScanRequest createScanRequest() {
		return ScanRequest.builder().repositoryUrl("https://github.com/test/repo").repositoryName("test-repo")
				.branchName("main").username("testuser").token("testtoken").toolType("GitHub")
				.connectionId(new ObjectId()).toolConfigId(new ObjectId()).build();
	}

	private ScmRepos createRepo(String name) {
		ScmRepos repo = ScmRepos.builder().build();
		repo.setRepositoryName(name);
		repo.setUrl("https://github.com/test/" + name);
		return repo;
	}

	private ScmConnectionTraceLog createTraceLog(boolean fetchSuccessful, long timestamp) {
		ScmConnectionTraceLog traceLog = new ScmConnectionTraceLog();
		traceLog.setFetchSuccessful(fetchSuccessful);
		traceLog.setLastSyncTimeTimeStamp(timestamp);
		return traceLog;
	}

	@Test(expected = PlatformApiException.class)
	public void testFetchRepositories_PlatformException() throws PlatformApiException {
		ScanRequest scanRequest = createScanRequest();

		when(persistenceService.getScmConnectionTraceLog(anyString())).thenReturn(null);
		when(repositoryServiceLocator.getRepositoryService(anyString())).thenReturn(platformService);
		when(platformService.fetchRepositories(any(ScanRequest.class)))
				.thenThrow(new PlatformApiException("API failed", null));

		repositoryFetcher.fetchRepositories(scanRequest);
	}

	@Test
	public void testFetchRepositories_NullTraceLog() throws PlatformApiException {
		ScanRequest scanRequest = createScanRequest();
		List<ScmRepos> repos = Arrays.asList(createRepo("repo1"));

		when(persistenceService.getScmConnectionTraceLog(anyString())).thenReturn(null);
		when(repositoryServiceLocator.getRepositoryService(anyString())).thenReturn(platformService);
		when(platformService.fetchRepositories(any(ScanRequest.class))).thenReturn(repos);

		ScanResult result = repositoryFetcher.fetchRepositories(scanRequest);

		assertNotNull(result);
		assertTrue(result.isSuccess());
		assertNotNull(scanRequest.getSince());
	}
}
