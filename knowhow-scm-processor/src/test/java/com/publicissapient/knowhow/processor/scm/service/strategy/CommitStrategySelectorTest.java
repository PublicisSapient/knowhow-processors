/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.knowhow.processor.scm.service.strategy;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommitStrategySelectorTest {

    @Mock
    private CommitDataFetchStrategy jGitStrategy;

    @Mock
    private RestApiCommitDataFetchStrategy restApiStrategy;

    @Mock
    private CommitDataFetchStrategy customStrategy;

    private CommitStrategySelector strategySelector;
    private Map<String, CommitDataFetchStrategy> strategies;

    @BeforeEach
    void setUp() {
        strategies = new HashMap<>();
        strategies.put("jGitCommitDataFetchStrategy", jGitStrategy);
        strategies.put("restApiCommitDataFetchStrategy", restApiStrategy);
        strategies.put("customStrategy", customStrategy);

        strategySelector = new CommitStrategySelector(strategies);

        // Set default values for @Value fields
        ReflectionTestUtils.setField(strategySelector, "defaultCommitStrategy", "jgit");
        ReflectionTestUtils.setField(strategySelector, "useRestApiForCommits", false);
    }

    @Test
    void testSelectStrategy_WithExplicitStrategyRequest_ReturnsRequestedStrategy() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCommitFetchStrategy("customStrategy");

        when(customStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(customStrategy, result);
        verify(customStrategy).supports(scanRequest.getRepositoryUrl(), null);
    }

    @Test
    void testSelectStrategy_WithInvalidExplicitStrategy_FallsBackToDefault() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCommitFetchStrategy("nonExistentStrategy");
        scanRequest.setCloneEnabled(true);

        when(jGitStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(jGitStrategy, result);
    }

    @Test
    void testSelectStrategy_WithUnsupportedExplicitStrategy_FallsBackToDefault() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCommitFetchStrategy("customStrategy");
        scanRequest.setCloneEnabled(true);

        when(customStrategy.supports(anyString(), any())).thenReturn(false);
        when(jGitStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(jGitStrategy, result);
    }

    @Test
    void testSelectStrategy_WithCloneEnabled_ReturnsJGitStrategy() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(true);

        when(jGitStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(jGitStrategy, result);
    }

    @Test
    void testSelectStrategy_WithCloneDisabled_ReturnsRestApiStrategy() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(false);

        when(restApiStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(restApiStrategy, result);
    }

    @Test
    void testSelectStrategy_WithUseRestApiConfig_ReturnsRestApiStrategy() {
        // Arrange
        ReflectionTestUtils.setField(strategySelector, "useRestApiForCommits", true);

        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(false);

        when(restApiStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(restApiStrategy, result);
    }

    @Test
    void testSelectStrategy_WithDefaultJGitConfig_ReturnsJGitStrategy() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(false);

        ReflectionTestUtils.setField(strategySelector, "useRestApiForCommits", false);
        ReflectionTestUtils.setField(strategySelector, "defaultCommitStrategy", "jgit");

        when(jGitStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(jGitStrategy, result);
    }

    @Test
    void testSelectStrategy_WithDefaultRestApiConfig_ReturnsRestApiStrategy() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(false);

        ReflectionTestUtils.setField(strategySelector, "useRestApiForCommits", false);
        ReflectionTestUtils.setField(strategySelector, "defaultCommitStrategy", "restapi");

        when(restApiStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(restApiStrategy, result);
    }

    @Test
    void testSelectStrategy_NoSuitableStrategy_FallsBackToRestApi() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(true);

        when(jGitStrategy.supports(anyString(), any())).thenReturn(false);
        when(restApiStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(restApiStrategy, result);
    }

    @Test
    void testSelectStrategy_NoSupportingStrategies_ReturnsNull() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(true);

        when(jGitStrategy.supports(anyString(), any())).thenReturn(false);
        when(restApiStrategy.supports(anyString(), any())).thenReturn(false);
        when(customStrategy.supports(anyString(), any())).thenReturn(false);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertNull(result);
    }

    @Test
    void testSupportsStrategy_RestApiStrategyWithToolType_ReturnsTrue() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setToolType("GitHub");

//        when(restApiStrategy.supportsByToolType("GitHub")).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(restApiStrategy, result);
//        verify(restApiStrategy).supportsByToolType("GitHub");
    }

    @Test
    void testSupportsStrategy_ByUrl_ReturnsTrue() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(true);

        when(jGitStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(jGitStrategy, result);
        verify(jGitStrategy).supports(scanRequest.getRepositoryUrl(), null);
    }

    @Test
    void testSupportsStrategy_ExceptionThrown_ReturnsFalse() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(true);

        when(jGitStrategy.supports(anyString(), any())).thenThrow(new RuntimeException("Test exception"));
        when(restApiStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(restApiStrategy, result);
    }

    @Test
    void testMapStrategyNameToBeanName_JGit_ReturnsBeanName() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(false);

        ReflectionTestUtils.setField(strategySelector, "defaultCommitStrategy", "jgit");
        when(jGitStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(jGitStrategy, result);
    }

    @Test
    void testMapStrategyNameToBeanName_Rest_ReturnsBeanName() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(false);

        ReflectionTestUtils.setField(strategySelector, "defaultCommitStrategy", "rest");
        when(restApiStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(restApiStrategy, result);
    }

    @Test
    void testMapStrategyNameToBeanName_Unknown_ReturnsAsIs() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(false);

        ReflectionTestUtils.setField(strategySelector, "defaultCommitStrategy", "customStrategy");
        when(customStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(customStrategy, result);
    }

    @Test
    void testConstructor_LogsAvailableStrategies() {
        // Arrange & Act
        CommitStrategySelector selector = new CommitStrategySelector(strategies);

        // Assert
        assertNotNull(selector);
        // Constructor logging is verified through manual inspection or log capture
    }

    @Test
    void testSelectStrategy_WithRestApiStrategyAndToolType_ButNotSupported() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setToolType("GitLab");
        scanRequest.setCloneEnabled(false);

//        when(restApiStrategy.supportsByToolType("GitLab")).thenReturn(false);
        when(restApiStrategy.supports(anyString(), anyString())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(restApiStrategy, result);
//        verify(restApiStrategy).supportsByToolType("GitLab");
        verify(restApiStrategy).supports(scanRequest.getRepositoryUrl(), "GitLab");
    }

    @Test
    void testSelectStrategy_FallbackIteratesAllStrategies() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(true);

        when(jGitStrategy.supports(anyString(), any())).thenReturn(false);
        when(restApiStrategy.supports(anyString(), any())).thenReturn(false);
        when(customStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(customStrategy, result);
        verify(jGitStrategy, atLeastOnce()).supports(anyString(), any());
        verify(restApiStrategy, atLeastOnce()).supports(anyString(), any());
        verify(customStrategy).supports(anyString(), any());
    }

    @Test
    void testSelectStrategy_ExplicitStrategyWithException_FallsBack() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCommitFetchStrategy("customStrategy");
        scanRequest.setCloneEnabled(true);

        when(customStrategy.supports(anyString(), any())).thenThrow(new RuntimeException("Strategy error"));
        when(jGitStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(jGitStrategy, result);
    }

    @Test
    void testSelectStrategy_WithNullToolType() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setToolType(null);
        scanRequest.setCloneEnabled(false);

        when(restApiStrategy.supports(anyString(), isNull())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(restApiStrategy, result);
        verify(restApiStrategy).supports(scanRequest.getRepositoryUrl(), null);
    }

    @Test
    void testSelectStrategy_EmptyStrategiesMap() {
        // Arrange
        CommitStrategySelector emptySelector = new CommitStrategySelector(new HashMap<>());
        ReflectionTestUtils.setField(emptySelector, "defaultCommitStrategy", "jgit");
        ReflectionTestUtils.setField(emptySelector, "useRestApiForCommits", false);

        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(true);

        // Act
        CommitDataFetchStrategy result = emptySelector.selectStrategy(scanRequest);

        // Assert
        assertNull(result);
    }

    @Test
    void testSelectStrategy_AllStrategiesThrowException() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(true);

        when(jGitStrategy.supports(anyString(), any())).thenThrow(new RuntimeException("JGit error"));
        when(restApiStrategy.supports(anyString(), any())).thenThrow(new RuntimeException("REST error"));
        when(customStrategy.supports(anyString(), any())).thenThrow(new RuntimeException("Custom error"));

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertNull(result);
    }

    @Test
    void testSelectStrategy_WithRestApiConfigAndCloneEnabled() {
        // Arrange
        ReflectionTestUtils.setField(strategySelector, "useRestApiForCommits", true);

        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(true); // Clone enabled should take precedence

        when(jGitStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(jGitStrategy, result); // Clone enabled takes precedence over config
    }

    @Test
    void testSelectStrategy_CaseInsensitiveStrategyMapping() {
        // Arrange
        ScanRequest scanRequest = ScanRequest.builder().build();
        scanRequest.setRepositoryUrl("https://github.com/test/repo.git");
        scanRequest.setCloneEnabled(false);

        ReflectionTestUtils.setField(strategySelector, "defaultCommitStrategy", "RESTAPI");
        when(restApiStrategy.supports(anyString(), any())).thenReturn(true);

        // Act
        CommitDataFetchStrategy result = strategySelector.selectStrategy(scanRequest);

        // Assert
        assertSame(restApiStrategy, result);
    }
}
