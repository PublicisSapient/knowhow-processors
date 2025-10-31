package com.publicissapient.knowhow.processor.scm.service.core.fetcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.core.PersistenceService;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformMergeRequestService;
import com.publicissapient.knowhow.processor.scm.service.platform.MergeRequestServiceLocator;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser.GitUrlInfo;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

@RunWith(MockitoJUnitRunner.class)
public class MergeRequestFetcherTest {

	@Mock
	private MergeRequestServiceLocator mergeRequestServiceLocator;

	@Mock
	private PersistenceService persistenceService;

	@Mock
	private GitUrlParser gitUrlParser;

	@Mock
	private GitPlatformMergeRequestService platformService;

	private MergeRequestFetcher mergeRequestFetcher;

	@Before
	public void setUp() {
		mergeRequestFetcher = new MergeRequestFetcher(mergeRequestServiceLocator, persistenceService, gitUrlParser);
		ReflectionTestUtils.setField(mergeRequestFetcher, "firstScanFromMonths", 6);
		ReflectionTestUtils.setField(mergeRequestFetcher, "maxMergeRequestsPerScan", 5000);
	}

	@Test
	public void testFetchMergeRequests_Success() throws PlatformApiException {
		ScanRequest scanRequest = createScanRequest(null, null);
		GitUrlInfo urlInfo = createGitUrlInfo();
		List<ScmMergeRequests> newMRs = Arrays.asList(createMergeRequest("1", ScmMergeRequests.MergeRequestState.OPEN));

		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(urlInfo);
		when(mergeRequestServiceLocator.getMergeRequestService(anyString())).thenReturn(platformService);
		when(platformService.fetchMergeRequests(anyString(), any(GitUrlInfo.class), anyString(), anyString(),
				any(LocalDateTime.class), any())).thenReturn(newMRs);

		List<ScmMergeRequests> result = mergeRequestFetcher.fetchMergeRequests(scanRequest);

		assertNotNull(result);
		assertEquals(1, result.size());
	}

	@Test
	public void testFetchMergeRequests_NoExistingOpenMRs() throws PlatformApiException {
		ScanRequest scanRequest = createScanRequest(null, null);
		GitUrlInfo urlInfo = createGitUrlInfo();
		List<ScmMergeRequests> newMRs = Arrays.asList(createMergeRequest("1", ScmMergeRequests.MergeRequestState.MERGED));

		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(urlInfo);
		when(mergeRequestServiceLocator.getMergeRequestService(anyString())).thenReturn(platformService);
		when(platformService.fetchMergeRequests(anyString(), any(GitUrlInfo.class), anyString(), anyString(),
				any(LocalDateTime.class), any())).thenReturn(newMRs);

		List<ScmMergeRequests> result = mergeRequestFetcher.fetchMergeRequests(scanRequest);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("MERGED", result.get(0).getState());
	}

	@Test
	public void testFetchMergeRequests_WithLastScanFrom() throws PlatformApiException {
		Long lastScanFrom = System.currentTimeMillis();
		ScanRequest scanRequest = createScanRequest(lastScanFrom, null);
		GitUrlInfo urlInfo = createGitUrlInfo();
		List<ScmMergeRequests> newMRs = Arrays.asList(createMergeRequest("1", ScmMergeRequests.MergeRequestState.OPEN));

		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(urlInfo);
		when(mergeRequestServiceLocator.getMergeRequestService(anyString())).thenReturn(platformService);
		when(platformService.fetchMergeRequests(anyString(), any(GitUrlInfo.class), anyString(), anyString(),
				any(LocalDateTime.class), any())).thenReturn(newMRs);

		List<ScmMergeRequests> result = mergeRequestFetcher.fetchMergeRequests(scanRequest);

		assertNotNull(result);
		assertEquals(1, result.size());
	}

	@Test
	public void testFetchMergeRequests_WithSinceDate() throws PlatformApiException {
		LocalDateTime since = LocalDateTime.now().minusDays(10);
		ScanRequest scanRequest = createScanRequest(null, since);
		GitUrlInfo urlInfo = createGitUrlInfo();
		List<ScmMergeRequests> newMRs = Arrays.asList(createMergeRequest("1", ScmMergeRequests.MergeRequestState.OPEN));

		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(urlInfo);
		when(mergeRequestServiceLocator.getMergeRequestService(anyString())).thenReturn(platformService);
		when(platformService.fetchMergeRequests(anyString(), any(GitUrlInfo.class), anyString(), anyString(),
				any(LocalDateTime.class), any())).thenReturn(newMRs);

		List<ScmMergeRequests> result = mergeRequestFetcher.fetchMergeRequests(scanRequest);

		assertNotNull(result);
		assertEquals(1, result.size());
	}

	@Test
	public void testFetchMergeRequests_DefaultFirstScanFrom() throws PlatformApiException {
		ScanRequest scanRequest = createScanRequest(0L, null);
		GitUrlInfo urlInfo = createGitUrlInfo();
		List<ScmMergeRequests> newMRs = Arrays.asList(createMergeRequest("1", ScmMergeRequests.MergeRequestState.OPEN));

		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(urlInfo);
		when(mergeRequestServiceLocator.getMergeRequestService(anyString())).thenReturn(platformService);
		when(platformService.fetchMergeRequests(anyString(), any(GitUrlInfo.class), anyString(), anyString(),
				any(LocalDateTime.class), any())).thenReturn(newMRs);

		List<ScmMergeRequests> result = mergeRequestFetcher.fetchMergeRequests(scanRequest);

		assertNotNull(result);
		assertEquals(1, result.size());
	}

	@Test
	public void testFetchMergeRequests_BitbucketTokenFormat() throws PlatformApiException {
		ScanRequest scanRequest = createBitbucketScanRequest();
		GitUrlInfo urlInfo = createGitUrlInfo();
		List<ScmMergeRequests> newMRs = Arrays.asList(createMergeRequest("1", ScmMergeRequests.MergeRequestState.OPEN));

		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(urlInfo);
		when(mergeRequestServiceLocator.getMergeRequestService(anyString())).thenReturn(platformService);
		when(platformService.fetchMergeRequests(anyString(), any(GitUrlInfo.class), anyString(),
				eq("testuser:testtoken"), any(LocalDateTime.class), any())).thenReturn(newMRs);

		List<ScmMergeRequests> result = mergeRequestFetcher.fetchMergeRequests(scanRequest);

		assertNotNull(result);
		assertEquals(1, result.size());
	}

	private ScanRequest createScanRequest(Long lastScanFrom, LocalDateTime since) {
		return ScanRequest.builder().repositoryUrl("https://github.com/test/repo").repositoryName("test-repo")
				.branchName("main").username("testuser").token("testtoken").toolType("GitHub")
				.toolConfigId(new ObjectId()).lastScanFrom(lastScanFrom).since(since).build();
	}

	private ScanRequest createBitbucketScanRequest() {
		return ScanRequest.builder().repositoryUrl("https://bitbucket.org/test/repo").repositoryName("test-repo")
				.branchName("main").username("testuser").token("testtoken").toolType("Bitbucket")
				.toolConfigId(new ObjectId()).build();
	}

	private GitUrlInfo createGitUrlInfo() {
		return new GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "test", "repo", "test", "github.com/test/repo.git");
	}

	private ScmMergeRequests createMergeRequest(String externalId, ScmMergeRequests.MergeRequestState state) {
		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setExternalId(externalId);
		mr.setState(state.toString());
		mr.setUpdatedOn(LocalDateTime.now().minusDays(5));
		return mr;
	}
}
