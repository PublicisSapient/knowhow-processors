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
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.service.strategy.CommitDataFetchStrategy;
import com.publicissapient.knowhow.processor.scm.service.strategy.CommitStrategySelector;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser.GitUrlInfo;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;

@RunWith(MockitoJUnitRunner.class)
public class CommitFetcherTest {

	@Mock
	private CommitStrategySelector strategySelector;

	@Mock
	private GitUrlParser gitUrlParser;

	@Mock
	private CommitDataFetchStrategy strategy;

	private CommitFetcher commitFetcher;

	@Before
	public void setUp() {
		commitFetcher = new CommitFetcher(strategySelector, gitUrlParser);
		ReflectionTestUtils.setField(commitFetcher, "firstScanFromMonths", 6);
	}

	@Test
	public void testFetchCommits_Success() throws DataProcessingException {
		ScanRequest scanRequest = createScanRequest(null, null);
		GitUrlInfo urlInfo = createGitUrlInfo();
		List<ScmCommits> expectedCommits = Arrays.asList(new ScmCommits());

		when(strategySelector.selectStrategy(scanRequest)).thenReturn(strategy);
		when(strategy.getStrategyName()).thenReturn("testStrategy");
		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(urlInfo);
		when(strategy.fetchCommits(anyString(), anyString(), any(GitUrlInfo.class), anyString(),
				any(CommitDataFetchStrategy.RepositoryCredentials.class), any(LocalDateTime.class)))
				.thenReturn(expectedCommits);

		List<ScmCommits> result = commitFetcher.fetchCommits(scanRequest);

		assertNotNull(result);
		assertEquals(expectedCommits, result);
		verify(strategySelector).selectStrategy(scanRequest);
		verify(strategy).fetchCommits(anyString(), anyString(), eq(urlInfo), anyString(),
				any(CommitDataFetchStrategy.RepositoryCredentials.class), any(LocalDateTime.class));
	}

	@Test(expected = DataProcessingException.class)
	public void testFetchCommits_NoStrategyFound() throws DataProcessingException {
		ScanRequest scanRequest = createScanRequest(null, null);

		when(strategySelector.selectStrategy(scanRequest)).thenReturn(null);

		commitFetcher.fetchCommits(scanRequest);
	}

	@Test(expected = DataProcessingException.class)
	public void testFetchCommits_InvalidGitUrl() throws DataProcessingException {
		ScanRequest scanRequest = createScanRequest(null, null);

		when(strategySelector.selectStrategy(scanRequest)).thenReturn(strategy);
		when(strategy.getStrategyName()).thenReturn("testStrategy");
		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(null);

		commitFetcher.fetchCommits(scanRequest);
	}

	@Test
	public void testFetchCommits_WithLastScanFrom() throws DataProcessingException {
		Long lastScanFrom = System.currentTimeMillis();
		ScanRequest scanRequest = createScanRequest(lastScanFrom, null);
		GitUrlInfo urlInfo = createGitUrlInfo();
		List<ScmCommits> expectedCommits = Arrays.asList(new ScmCommits());

		when(strategySelector.selectStrategy(scanRequest)).thenReturn(strategy);
		when(strategy.getStrategyName()).thenReturn("testStrategy");
		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(urlInfo);
		when(strategy.fetchCommits(anyString(), anyString(), any(GitUrlInfo.class), anyString(),
				any(CommitDataFetchStrategy.RepositoryCredentials.class), any(LocalDateTime.class)))
				.thenReturn(expectedCommits);

		List<ScmCommits> result = commitFetcher.fetchCommits(scanRequest);

		assertNotNull(result);
		assertEquals(expectedCommits, result);
	}

	@Test
	public void testFetchCommits_WithSinceDate() throws DataProcessingException {
		LocalDateTime since = LocalDateTime.now().minusDays(10);
		ScanRequest scanRequest = createScanRequest(null, since);
		GitUrlInfo urlInfo = createGitUrlInfo();
		List<ScmCommits> expectedCommits = Arrays.asList(new ScmCommits());

		when(strategySelector.selectStrategy(scanRequest)).thenReturn(strategy);
		when(strategy.getStrategyName()).thenReturn("testStrategy");
		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(urlInfo);
		when(strategy.fetchCommits(anyString(), anyString(), any(GitUrlInfo.class), anyString(),
				any(CommitDataFetchStrategy.RepositoryCredentials.class), any(LocalDateTime.class)))
				.thenReturn(expectedCommits);

		List<ScmCommits> result = commitFetcher.fetchCommits(scanRequest);

		assertNotNull(result);
		assertEquals(expectedCommits, result);
	}

	@Test
	public void testFetchCommits_WithDefaultFirstScanFrom() throws DataProcessingException {
		ScanRequest scanRequest = createScanRequest(0L, null);
		GitUrlInfo urlInfo = createGitUrlInfo();
		List<ScmCommits> expectedCommits = Arrays.asList(new ScmCommits());

		when(strategySelector.selectStrategy(scanRequest)).thenReturn(strategy);
		when(strategy.getStrategyName()).thenReturn("testStrategy");
		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(urlInfo);
		when(strategy.fetchCommits(anyString(), anyString(), any(GitUrlInfo.class), anyString(),
				any(CommitDataFetchStrategy.RepositoryCredentials.class), any(LocalDateTime.class)))
				.thenReturn(expectedCommits);

		List<ScmCommits> result = commitFetcher.fetchCommits(scanRequest);

		assertNotNull(result);
		assertEquals(expectedCommits, result);
	}

	private ScanRequest createScanRequest(Long lastScanFrom, LocalDateTime since) {
		return ScanRequest.builder().repositoryUrl("https://github.com/test/repo").repositoryName("test-repo")
				.branchName("main").username("testuser").token("testtoken").toolType("GitHub")
				.toolConfigId(new ObjectId()).lastScanFrom(lastScanFrom).since(since).build();
	}

	private GitUrlInfo createGitUrlInfo() {
		return new GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "test", "repo", "test", "github.com/test/repo.git");
	}

	@Test
	public void testFetchCommits_EmptyCommitsList() throws DataProcessingException {
		ScanRequest scanRequest = createScanRequest(null, null);
		GitUrlInfo urlInfo = createGitUrlInfo();
		List<ScmCommits> emptyCommits = List.of();

		when(strategySelector.selectStrategy(scanRequest)).thenReturn(strategy);
		when(strategy.getStrategyName()).thenReturn("testStrategy");
		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(urlInfo);
		when(strategy.fetchCommits(anyString(), anyString(), any(GitUrlInfo.class), anyString(),
				any(CommitDataFetchStrategy.RepositoryCredentials.class), any(LocalDateTime.class)))
				.thenReturn(emptyCommits);

		List<ScmCommits> result = commitFetcher.fetchCommits(scanRequest);

		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test(expected = DataProcessingException.class)
	public void testFetchCommits_StrategyThrowsException() throws DataProcessingException {
		ScanRequest scanRequest = createScanRequest(null, null);
		GitUrlInfo urlInfo = createGitUrlInfo();

		when(strategySelector.selectStrategy(scanRequest)).thenReturn(strategy);
		when(strategy.getStrategyName()).thenReturn("testStrategy");
		when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(urlInfo);
		when(strategy.fetchCommits(anyString(), anyString(), any(GitUrlInfo.class), anyString(),
				any(CommitDataFetchStrategy.RepositoryCredentials.class), any(LocalDateTime.class)))
				.thenThrow(new DataProcessingException("Strategy failed"));

		commitFetcher.fetchCommits(scanRequest);
	}
}
