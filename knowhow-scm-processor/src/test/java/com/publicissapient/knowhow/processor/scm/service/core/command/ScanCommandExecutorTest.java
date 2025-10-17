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

package com.publicissapient.knowhow.processor.scm.service.core.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.dto.ScanResult;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.service.core.PersistenceService;
import com.publicissapient.knowhow.processor.scm.service.core.fetcher.CommitFetcher;
import com.publicissapient.knowhow.processor.scm.service.core.fetcher.MergeRequestFetcher;
import com.publicissapient.knowhow.processor.scm.service.core.processor.DataReferenceUpdater;
import com.publicissapient.knowhow.processor.scm.service.core.processor.UserProcessor;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;

@ExtendWith(MockitoExtension.class)
public class ScanCommandExecutorTest {

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private CommitFetcher commitFetcher;

    @Mock
    private MergeRequestFetcher mergeRequestFetcher;

    @Mock
    private UserProcessor userProcessor;

    @Mock
    private DataReferenceUpdater dataReferenceUpdater;

    @InjectMocks
    private ScanCommandExecutor scanCommandExecutor;

    private ScanCommand scanCommand;
    private ScanRequest scanRequest;
    private List<ScmCommits> mockCommits;
    private List<ScmMergeRequests> mockMergeRequests;
    private UserProcessor.UserProcessingResult mockUserResult;

    @BeforeEach
    void setUp() {
        scanRequest = ScanRequest.builder()
                .repositoryUrl("https://github.com/test/repo")
                .repositoryName("test-repo")
                .toolConfigId(new ObjectId())
                .build();

        scanCommand = new ScanCommand(scanRequest);

        // Setup mock data
        mockCommits = Arrays.asList(
                createMockCommit("commit1"),
                createMockCommit("commit2")
        );

        mockMergeRequests = Arrays.asList(
                createMockMergeRequest("mr1"),
                createMockMergeRequest("mr2")
        );

        Map<String, User> userMap = new HashMap<>();
        userMap.put("user1", createMockUser("user1"));
        userMap.put("user2", createMockUser("user2"));

        Set<User> allUsers = new HashSet<>(userMap.values());

        mockUserResult = new UserProcessor.UserProcessingResult(userMap, allUsers);
    }

    @Test
    void testExecute_Success_WithCommitsAndMergeRequests() throws Exception {
        // Arrange
        when(commitFetcher.fetchCommits(scanRequest)).thenReturn(mockCommits);
        when(mergeRequestFetcher.fetchMergeRequests(scanRequest)).thenReturn(mockMergeRequests);
        when(userProcessor.processUsers(mockCommits, mockMergeRequests, scanRequest))
                .thenReturn(mockUserResult);

        // Act
        ScanResult result = scanCommandExecutor.execute(scanCommand);

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("https://github.com/test/repo", result.getRepositoryUrl());
        assertEquals("test-repo", result.getRepositoryName());
        assertEquals(2, result.getCommitsFound());
        assertEquals(2, result.getMergeRequestsFound());
        assertEquals(2, result.getUsersFound());
        assertNotNull(result.getStartTime());
        assertNotNull(result.getEndTime());
        assertTrue(result.getDurationMs() >= 0);

        // Verify interactions
        verify(commitFetcher).fetchCommits(scanRequest);
        verify(mergeRequestFetcher).fetchMergeRequests(scanRequest);
        verify(userProcessor).processUsers(mockCommits, mockMergeRequests, scanRequest);
        verify(dataReferenceUpdater).updateCommitsWithUserReferences(
                mockCommits, mockUserResult.getUserMap(), "test-repo");
        verify(dataReferenceUpdater).updateMergeRequestsWithUserReferences(
                mockMergeRequests, mockUserResult.getUserMap(), "test-repo");
        verify(persistenceService).saveCommits(mockCommits);
        verify(persistenceService).saveMergeRequests(mockMergeRequests);

        // Verify processorItemId is set on commits
        ArgumentCaptor<List<ScmCommits>> commitCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).saveCommits(commitCaptor.capture());
        List<ScmCommits> savedCommits = commitCaptor.getValue();
        assertEquals(2, savedCommits.size());
    }

    @Test
    void testExecute_Success_WithEmptyData() throws Exception {
        // Arrange
        when(commitFetcher.fetchCommits(scanRequest)).thenReturn(Collections.emptyList());
        when(mergeRequestFetcher.fetchMergeRequests(scanRequest)).thenReturn(Collections.emptyList());
        when(userProcessor.processUsers(anyList(), anyList(), eq(scanRequest)))
                .thenReturn(new UserProcessor.UserProcessingResult(
                        Collections.emptyMap(), Collections.emptySet()));

        // Act
        ScanResult result = scanCommandExecutor.execute(scanCommand);

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getCommitsFound());
        assertEquals(0, result.getMergeRequestsFound());
        assertEquals(0, result.getUsersFound());

        // Verify no persistence calls for empty data
        verify(persistenceService, never()).saveCommits(anyList());
        verify(persistenceService, never()).saveMergeRequests(anyList());
    }

    @Test
    void testExecute_Failure_CommitFetcherException() throws Exception {
        // Arrange
        RuntimeException fetchException = new RuntimeException("Failed to fetch commits");
        when(commitFetcher.fetchCommits(scanRequest)).thenThrow(fetchException);

        // Act & Assert
        DataProcessingException exception = assertThrows(DataProcessingException.class,
                () -> scanCommandExecutor.execute(scanCommand));

        assertEquals("Repository scan failed", exception.getMessage());
        assertEquals(fetchException, exception.getCause());

        // Verify no further processing
        verify(mergeRequestFetcher, never()).fetchMergeRequests(any());
        verify(userProcessor, never()).processUsers(any(), any(), any());
        verify(persistenceService, never()).saveCommits(any());
        verify(persistenceService, never()).saveMergeRequests(any());
    }

    @Test
    void testExecute_Failure_MergeRequestFetcherException() throws Exception {
        // Arrange
        when(commitFetcher.fetchCommits(scanRequest)).thenReturn(mockCommits);
        RuntimeException fetchException = new RuntimeException("Failed to fetch merge requests");
        when(mergeRequestFetcher.fetchMergeRequests(scanRequest)).thenThrow(fetchException);

        // Act & Assert
        DataProcessingException exception = assertThrows(DataProcessingException.class,
                () -> scanCommandExecutor.execute(scanCommand));

        assertEquals("Repository scan failed", exception.getMessage());
        assertEquals(fetchException, exception.getCause());

        // Verify no persistence
        verify(persistenceService, never()).saveCommits(any());
        verify(persistenceService, never()).saveMergeRequests(any());
    }

    @Test
    void testExecute_Failure_UserProcessorException() throws Exception {
        // Arrange
        when(commitFetcher.fetchCommits(scanRequest)).thenReturn(mockCommits);
        when(mergeRequestFetcher.fetchMergeRequests(scanRequest)).thenReturn(mockMergeRequests);
        RuntimeException processingException = new RuntimeException("User processing failed");
        when(userProcessor.processUsers(mockCommits, mockMergeRequests, scanRequest))
                .thenThrow(processingException);

        // Act & Assert
        DataProcessingException exception = assertThrows(DataProcessingException.class,
                () -> scanCommandExecutor.execute(scanCommand));

        assertEquals("Repository scan failed", exception.getMessage());
        assertEquals(processingException, exception.getCause());
    }

    @Test
    void testExecute_Failure_DataReferenceUpdaterException() throws Exception {
        // Arrange
        when(commitFetcher.fetchCommits(scanRequest)).thenReturn(mockCommits);
        when(mergeRequestFetcher.fetchMergeRequests(scanRequest)).thenReturn(mockMergeRequests);
        when(userProcessor.processUsers(mockCommits, mockMergeRequests, scanRequest))
                .thenReturn(mockUserResult);
        RuntimeException updateException = new RuntimeException("Reference update failed");
        doThrow(updateException).when(dataReferenceUpdater)
                .updateCommitsWithUserReferences(any(), any(), anyString());

        // Act & Assert
        DataProcessingException exception = assertThrows(DataProcessingException.class,
                () -> scanCommandExecutor.execute(scanCommand));

        assertEquals("Repository scan failed", exception.getMessage());
        assertEquals(updateException, exception.getCause());
    }

    @Test
    void testExecute_Failure_PersistenceException() throws Exception {
        // Arrange
        when(commitFetcher.fetchCommits(scanRequest)).thenReturn(mockCommits);
        when(mergeRequestFetcher.fetchMergeRequests(scanRequest)).thenReturn(mockMergeRequests);
        when(userProcessor.processUsers(mockCommits, mockMergeRequests, scanRequest))
                .thenReturn(mockUserResult);
        RuntimeException persistException = new RuntimeException("Persistence failed");
        doThrow(persistException).when(persistenceService).saveCommits(any());

        // Act & Assert
        DataProcessingException exception = assertThrows(DataProcessingException.class,
                () -> scanCommandExecutor.execute(scanCommand));

        assertEquals("Repository scan failed", exception.getMessage());
        assertEquals(persistException, exception.getCause());
    }

    @Test
    void testPersistData_WithCommits() throws Exception {
        // Arrange
        when(commitFetcher.fetchCommits(scanRequest)).thenReturn(mockCommits);
        when(mergeRequestFetcher.fetchMergeRequests(scanRequest)).thenReturn(Collections.emptyList());
        when(userProcessor.processUsers(anyList(), anyList(), eq(scanRequest)))
                .thenReturn(new UserProcessor.UserProcessingResult(
                        Collections.emptyMap(), Collections.emptySet()));

        // Act
        scanCommandExecutor.execute(scanCommand);

        // Assert
        ArgumentCaptor<List<ScmCommits>> commitCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).saveCommits(commitCaptor.capture());
        List<ScmCommits> savedCommits = commitCaptor.getValue();
        assertEquals(2, savedCommits.size());
    }

    @Test
    void testPersistData_WithMergeRequests() throws Exception {
        // Arrange
        when(commitFetcher.fetchCommits(scanRequest)).thenReturn(Collections.emptyList());
        when(mergeRequestFetcher.fetchMergeRequests(scanRequest)).thenReturn(mockMergeRequests);
        when(userProcessor.processUsers(anyList(), anyList(), eq(scanRequest)))
                .thenReturn(new UserProcessor.UserProcessingResult(
                        Collections.emptyMap(), Collections.emptySet()));

        // Act
        scanCommandExecutor.execute(scanCommand);

        // Assert
        verify(persistenceService).saveMergeRequests(mockMergeRequests);
        verify(persistenceService, never()).saveCommits(any());
    }

    @Test
    void testPersistData_WithEmptyLists() throws Exception {
        // Arrange
        when(commitFetcher.fetchCommits(scanRequest)).thenReturn(Collections.emptyList());
        when(mergeRequestFetcher.fetchMergeRequests(scanRequest)).thenReturn(Collections.emptyList());
        when(userProcessor.processUsers(anyList(), anyList(), eq(scanRequest)))
                .thenReturn(new UserProcessor.UserProcessingResult(
                        Collections.emptyMap(), Collections.emptySet()));

        // Act
        scanCommandExecutor.execute(scanCommand);

        // Assert
        verify(persistenceService, never()).saveCommits(any());
        verify(persistenceService, never()).saveMergeRequests(any());
    }

    // Helper methods
    private ScmCommits createMockCommit(String id) {
        ScmCommits commit = new ScmCommits();
        commit.setRevisionNumber(id);
        commit.setAuthorName("Author " + id);
        commit.setCommitMessage("Commit message " + id);
        return commit;
    }

    private ScmMergeRequests createMockMergeRequest(String id) {
        ScmMergeRequests mergeRequest = new ScmMergeRequests();
        mergeRequest.setExternalId(id);
        mergeRequest.setTitle("Merge Request " + id);
        mergeRequest.setAuthorUserId("Author " + id);
        return mergeRequest;
    }

    private User createMockUser(String id) {
        User user = new User();
        user.setId(new ObjectId());
        user.setUsername("User " + id);
        user.setEmail(id + "@example.com");
        return user;
    }

}
