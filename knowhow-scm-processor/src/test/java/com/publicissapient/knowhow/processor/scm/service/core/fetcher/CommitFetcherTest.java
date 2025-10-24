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

package com.publicissapient.knowhow.processor.scm.service.core.fetcher;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.service.strategy.CommitDataFetchStrategy;
import com.publicissapient.knowhow.processor.scm.service.strategy.CommitStrategySelector;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser.GitUrlInfo;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommitFetcherTest {

    @Mock
    private CommitStrategySelector strategySelector;

    @Mock
    private PlatformServiceLocator platformServiceLocator;

    @Mock
    private GitUrlParser gitUrlParser;

    @Mock
    private CommitDataFetchStrategy mockStrategy;

    @Mock
    private GitPlatformService mockPlatformService;

    @InjectMocks
    private CommitFetcher commitFetcher;

    private ScanRequest scanRequest;
    private GitUrlInfo gitUrlInfo;
    private List<ScmCommits> expectedCommits;

    @BeforeEach
    void setUp() {
        // Set the firstScanFromMonths value
        ReflectionTestUtils.setField(commitFetcher, "firstScanFromMonths", 6);

        // Initialize test data
        scanRequest = ScanRequest.builder().repositoryName("test-repo")
                .repositoryUrl("https://github.com/test/test-repo.git")
                .toolType("GitHub")
                .toolConfigId(new ObjectId())
                .branchName("master")
                .username("testUser")
                .token("***test**token**").build();

        gitUrlInfo = new GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "test", "test-repo", "test", "https://github.com/test/test-repo.git");

        ScmCommits commit = new ScmCommits();
        commit.setRevisionNumber("abc123");
        expectedCommits = List.of(commit);
    }

    @Test
    void testFetchCommits_WithLastScanTimestamp_Success() throws DataProcessingException {
        // Arrange
        long lastScanTimestamp = System.currentTimeMillis();
        scanRequest.setLastScanFrom(lastScanTimestamp);

        when(strategySelector.selectStrategy(scanRequest)).thenReturn(mockStrategy);
        when(mockStrategy.getStrategyName()).thenReturn("TestStrategy");
        when(platformServiceLocator.getPlatformService(scanRequest)).thenReturn(mockPlatformService);
        when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(gitUrlInfo);
        when(mockStrategy.fetchCommits(any(), any(), any(), any(), any(), any())).thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = commitFetcher.fetchCommits(scanRequest);

        // Assert
        assertNotNull(result);
        assertEquals(expectedCommits, result);
        verify(platformServiceLocator).setRepositoryContext(mockPlatformService, scanRequest.getRepositoryUrl());
        verify(mockStrategy).fetchCommits(
                eq(scanRequest.getToolType()),
                eq(scanRequest.getToolConfigId().toString()),
                eq(gitUrlInfo),
                eq(scanRequest.getBranchName()),
                any(CommitDataFetchStrategy.RepositoryCredentials.class),
                eq(LocalDateTime.ofEpochSecond(lastScanTimestamp / 1000, 0, ZoneOffset.UTC))
        );
    }

    @Test
    void testFetchCommits_WithSinceDate_Success() throws DataProcessingException {
        // Arrange
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(7);
        scanRequest.setSince(sinceDate);
        scanRequest.setLastScanFrom(null);

        when(strategySelector.selectStrategy(scanRequest)).thenReturn(mockStrategy);
        when(mockStrategy.getStrategyName()).thenReturn("TestStrategy");
        when(platformServiceLocator.getPlatformService(scanRequest)).thenReturn(mockPlatformService);
        when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(gitUrlInfo);
        when(mockStrategy.fetchCommits(any(), any(), any(), any(), any(), any())).thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = commitFetcher.fetchCommits(scanRequest);

        // Assert
        assertNotNull(result);
        assertEquals(expectedCommits, result);
        verify(mockStrategy).fetchCommits(
                eq(scanRequest.getToolType()),
                eq(scanRequest.getToolConfigId().toString()),
                eq(gitUrlInfo),
                eq(scanRequest.getBranchName()),
                any(CommitDataFetchStrategy.RepositoryCredentials.class),
                eq(sinceDate)
        );
    }

    @Test
    void testFetchCommits_FirstScan_Success() throws DataProcessingException {
        // Arrange
        scanRequest.setLastScanFrom(null);
        scanRequest.setSince(null);

        when(strategySelector.selectStrategy(scanRequest)).thenReturn(mockStrategy);
        when(mockStrategy.getStrategyName()).thenReturn("TestStrategy");
        when(platformServiceLocator.getPlatformService(scanRequest)).thenReturn(mockPlatformService);
        when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(gitUrlInfo);
        when(mockStrategy.fetchCommits(any(), any(), any(), any(), any(), any())).thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = commitFetcher.fetchCommits(scanRequest);

        // Assert
        assertNotNull(result);
        assertEquals(expectedCommits, result);
        verify(mockStrategy).fetchCommits(
                eq(scanRequest.getToolType()),
                eq(scanRequest.getToolConfigId().toString()),
                eq(gitUrlInfo),
                eq(scanRequest.getBranchName()),
                any(CommitDataFetchStrategy.RepositoryCredentials.class),
                argThat(dateTime -> {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime sixMonthsAgo = now.minusMonths(6);
                    // Allow 1 second tolerance for test execution time
                    return dateTime.isAfter(sixMonthsAgo.minusSeconds(1)) &&
                            dateTime.isBefore(sixMonthsAgo.plusSeconds(1));
                })
        );
    }

    @Test
    void testFetchCommits_NoStrategyFound_ThrowsException() {
        // Arrange
        when(strategySelector.selectStrategy(scanRequest)).thenReturn(null);

        // Act & Assert
        DataProcessingException exception = assertThrows(
                DataProcessingException.class,
                () -> commitFetcher.fetchCommits(scanRequest)
        );
        assertEquals("No suitable commit fetch strategy found for repository: " + scanRequest.getRepositoryUrl(),
                exception.getMessage());
    }

    @Test
    void testFetchCommits_NoPlatformService_ThrowsException() {
        // Arrange
        when(strategySelector.selectStrategy(scanRequest)).thenReturn(mockStrategy);
        when(mockStrategy.getStrategyName()).thenReturn("TestStrategy");
        when(platformServiceLocator.getPlatformService(scanRequest)).thenReturn(null);

        // Act & Assert
        DataProcessingException exception = assertThrows(
                DataProcessingException.class,
                () -> commitFetcher.fetchCommits(scanRequest)
        );
        assertEquals("No platform service found for toolType: " + scanRequest.getToolType(),
                exception.getMessage());
    }

    @Test
    void testFetchCommits_InvalidUrl_ThrowsException() throws DataProcessingException {
        // Arrange
        when(strategySelector.selectStrategy(scanRequest)).thenReturn(mockStrategy);
        when(mockStrategy.getStrategyName()).thenReturn("TestStrategy");
        when(platformServiceLocator.getPlatformService(scanRequest)).thenReturn(mockPlatformService);
        when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(null);

        // Act & Assert
        DataProcessingException exception = assertThrows(
                DataProcessingException.class,
                () -> commitFetcher.fetchCommits(scanRequest)
        );
        assertEquals("Invalid repository URL: " + scanRequest.getRepositoryName(),
                exception.getMessage());
    }

    @Test
    void testCalculateCommitsSince_WithLastScanFrom() {
        // Arrange
        long timestamp = System.currentTimeMillis();
        scanRequest.setLastScanFrom(timestamp);

        // Act
        LocalDateTime result = ReflectionTestUtils.invokeMethod(
                commitFetcher, "calculateCommitsSince", scanRequest
        );

        // Assert
        assertNotNull(result);
        assertEquals(LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC), result);
    }

    @Test
    void testCalculateCommitsSince_WithSinceDate() {
        // Arrange
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(10);
        scanRequest.setSince(sinceDate);
        scanRequest.setLastScanFrom(0L);

        // Act
        LocalDateTime result = ReflectionTestUtils.invokeMethod(
                commitFetcher, "calculateCommitsSince", scanRequest
        );

        // Assert
        assertNotNull(result);
        assertEquals(sinceDate, result);
    }

    @Test
    void testCalculateCommitsSince_FirstScan() {
        // Arrange
        scanRequest.setLastScanFrom(null);
        scanRequest.setSince(null);

        // Act
        LocalDateTime result = ReflectionTestUtils.invokeMethod(
                commitFetcher, "calculateCommitsSince", scanRequest
        );

        // Assert
        assertNotNull(result);
        LocalDateTime expectedDate = LocalDateTime.now().minusMonths(6);
        // Allow 1 second tolerance
        assertTrue(result.isAfter(expectedDate.minusSeconds(1)) &&
                result.isBefore(expectedDate.plusSeconds(1)));
    }

    @Test
    void testParseGitUrl_ValidUrl_Success() throws DataProcessingException {
        // Arrange
        CommitDataFetchStrategy.RepositoryCredentials credentials =
                CommitDataFetchStrategy.RepositoryCredentials.builder()
                        .username("testuser")
                        .token("test-token")
                        .build();

        when(gitUrlParser.parseGitUrl(
                scanRequest.getRepositoryUrl(),
                scanRequest.getToolType(),
                credentials.getUsername(),
                scanRequest.getRepositoryName()
        )).thenReturn(gitUrlInfo);

        // Act
        GitUrlInfo result = ReflectionTestUtils.invokeMethod(
                commitFetcher, "parseGitUrl", scanRequest, credentials
        );

        // Assert
        assertNotNull(result);
        assertEquals(gitUrlInfo, result);
    }

    @Test
    void testParseGitUrl_NullUrlInfo_ThrowsException() throws DataProcessingException {
        // Arrange
        CommitDataFetchStrategy.RepositoryCredentials credentials =
                CommitDataFetchStrategy.RepositoryCredentials.builder()
                        .username("testuser")
                        .token("test-token")
                        .build();

        when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(null);

        // Act & Assert
        DataProcessingException exception = assertThrows(
                DataProcessingException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        commitFetcher, "parseGitUrl", scanRequest, credentials
                )
        );
        assertEquals("Invalid repository URL: " + scanRequest.getRepositoryName(),
                exception.getMessage());
    }
}
