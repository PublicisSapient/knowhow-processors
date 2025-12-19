package com.publicissapient.knowhow.processor.scm.service.strategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;

@RunWith(MockitoJUnitRunner.class)
public class CommitStrategySelectorTest {

	@Mock
	private CommitDataFetchStrategy jGitStrategy;

	@Mock
	private CommitDataFetchStrategy restApiStrategy;

	private CommitStrategySelector selector;
	private Map<String, CommitDataFetchStrategy> strategies;

	@Before
	public void setUp() {
		strategies = new HashMap<>();
		strategies.put("jGitCommitDataFetchStrategy", jGitStrategy);
		strategies.put("restApiCommitDataFetchStrategy", restApiStrategy);

		when(jGitStrategy.getStrategyName()).thenReturn("jGitCommitDataFetchStrategy");
		when(restApiStrategy.getStrategyName()).thenReturn("restApiCommitDataFetchStrategy");

		selector = new CommitStrategySelector(strategies);
	}

	@Test
	public void testSelectStrategy_WithCloneEnabled() {
		ScanRequest request = ScanRequest.builder().repositoryUrl("https://github.com/test/repo").cloneEnabled(true)
				.build();

		when(jGitStrategy.supports("https://github.com/test/repo", null)).thenReturn(true);

		CommitDataFetchStrategy result = selector.selectStrategy(request);

		assertEquals(jGitStrategy, result);
	}

	@Test
	public void testSelectStrategy_WithExplicitStrategy() {
		ScanRequest request = ScanRequest.builder().repositoryUrl("https://github.com/test/repo")
				.commitFetchStrategy("restApiCommitDataFetchStrategy").cloneEnabled(false).build();

		when(restApiStrategy.supports("https://github.com/test/repo", null)).thenReturn(true);

		CommitDataFetchStrategy result = selector.selectStrategy(request);

		assertEquals(restApiStrategy, result);
	}



	@Test
	public void testSelectStrategy_FallbackToRestApi() {
		ScanRequest request = ScanRequest.builder().repositoryUrl("https://github.com/test/repo").cloneEnabled(false)
				.build();

		when(restApiStrategy.supports("https://github.com/test/repo", null)).thenReturn(true);

		CommitDataFetchStrategy result = selector.selectStrategy(request);

		assertEquals(restApiStrategy, result);
	}

	@Test
	public void testSelectStrategy_NoSupportingStrategy() {
		ScanRequest request = ScanRequest.builder().repositoryUrl("https://unsupported.com/repo").cloneEnabled(false)
				.build();

		when(jGitStrategy.supports("https://unsupported.com/repo", null)).thenReturn(false);
		when(restApiStrategy.supports("https://unsupported.com/repo", null)).thenReturn(false);

		CommitDataFetchStrategy result = selector.selectStrategy(request);

		assertNull(result);
	}

	@Test
	public void testSelectStrategy_InvalidExplicitStrategy() {
		ScanRequest request = ScanRequest.builder().repositoryUrl("https://github.com/test/repo")
				.commitFetchStrategy("invalidStrategy").cloneEnabled(false).build();

		when(jGitStrategy.supports("https://github.com/test/repo", null)).thenReturn(true);

		CommitDataFetchStrategy result = selector.selectStrategy(request);

		assertNotNull(result);
	}
}
