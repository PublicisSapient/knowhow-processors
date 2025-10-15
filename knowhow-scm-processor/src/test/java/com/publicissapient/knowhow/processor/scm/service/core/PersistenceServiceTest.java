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

package com.publicissapient.knowhow.processor.scm.service.core;

import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import com.publicissapient.kpidashboard.common.repository.scm.ScmCommitsRepository;
import com.publicissapient.kpidashboard.common.repository.scm.ScmMergeRequestsRepository;
import com.publicissapient.kpidashboard.common.repository.scm.ScmUserRepository;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistenceServiceTest {

    @Mock
    private ScmUserRepository userRepository;

    @Mock
    private ScmCommitsRepository commitRepository;

    @Mock
    private ScmMergeRequestsRepository mergeRequestRepository;

    @InjectMocks
    private PersistenceService persistenceService;

    private ObjectId processorItemId;
    private User testUser;
    private ScmCommits testCommit;
    private ScmMergeRequests testMergeRequest;

    @BeforeEach
    void setUp() {
        processorItemId = new ObjectId();

        testUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .repositoryName("test-repo")
                .processorItemId(processorItemId)
                .active(true)
                .build();

        testCommit = new ScmCommits();
        testCommit.setSha("abc123");
        testCommit.setProcessorItemId(processorItemId);
        testCommit.setCommitMessage("Test commit");
        testCommit.setAuthorName("Test Author");
        testCommit.setAuthorEmail("author@test.com");
        testCommit.setBranchName("main");
        testCommit.setRepositoryName("test-repo");

        testMergeRequest = new ScmMergeRequests();
        testMergeRequest.setExternalId("MR-123");
        testMergeRequest.setProcessorItemId(processorItemId);
        testMergeRequest.setTitle("Test MR");
        testMergeRequest.setState("OPEN");
        testMergeRequest.setFromBranch("feature");
        testMergeRequest.setToBranch("main");
    }

    @Test
    void testSaveUser_NewUser_Success() throws DataProcessingException {
        // Arrange
        when(userRepository.findByProcessorItemIdAndUsername(any(), any())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(new ObjectId());
            return savedUser;
        });

        // Act
        User result = persistenceService.saveUser(testUser);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        assertEquals(testUser.getUsername(), result.getUsername());
        verify(userRepository).save(any(User.class));
    }

//    @Test
//    void testSaveUser_ExistingUser_ReturnsExisting() throws DataProcessingException {
//        // Arrange
//        User existingUser = User.builder()// CHANGE: Remove String.valueOf() - id should be ObjectId not String
//                .username("testuser")
//                .email("test@example.com")
//                .displayName("Test User")
//                .repositoryName("test-repo")
//                .processorItemId(processorItemId)
//                .active(true)
//                .build();
//
//        // Create a new testUser with the same processorItemId for the saveUser call
//        User userToSave = User.builder()
//                .username("testuser")
//                .email("test@example.com")
//                .displayName("Test User")
//                .repositoryName("test-repo")
//                .processorItemId(processorItemId)
//                .active(true)
//                .build();
//
//        when(userRepository.findByProcessorItemIdAndUsername(processorItemId, "testuser"))
//                .thenReturn(Optional.of(existingUser));
//
//        // Act
//        User result = persistenceService.saveUser(userToSave);
//
//        // Assert
//        assertEquals(existingUser.getId(), result.getId());
//        verify(userRepository, never()).save(any(User.class));
//    }


    @Test
    void testSaveUser_DuplicateKeyException_ThrowsDataProcessingException() {
        // Arrange
        when(userRepository.findByProcessorItemIdAndUsername(any(), any())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("Duplicate key"));

        // Act & Assert
        DataProcessingException exception = assertThrows(DataProcessingException.class,
                () -> persistenceService.saveUser(testUser));
        assertTrue(exception.getMessage().contains("Failed to save user due to duplicate key"));
    }

    @Test
    void testSaveUser_GenericException_ThrowsDataProcessingException() {
        // Arrange
        when(userRepository.findByProcessorItemIdAndUsername(any(), any())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        DataProcessingException exception = assertThrows(DataProcessingException.class,
                () -> persistenceService.saveUser(testUser));
        assertTrue(exception.getMessage().contains("Failed to save user"));
    }

    @Test
    void testFindOrCreateUser_ExistingUser_ReturnsUser() {
        // Arrange
        User existingUser = testUser;
        existingUser.setId(new ObjectId());
        when(userRepository.findByProcessorItemIdAndUsername(processorItemId, "testuser"))
                .thenReturn(Optional.of(existingUser));

        // Act
        User result = persistenceService.findOrCreateUser("test-repo", "testuser",
                "test@example.com", "Test User", processorItemId);

        // Assert
        assertEquals(existingUser.getId(), result.getId());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testFindOrCreateUser_NewUser_CreatesAndReturns() {
        // Arrange
        when(userRepository.findByProcessorItemIdAndUsername(processorItemId, "testuser"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(new ObjectId());
            return savedUser;
        });

        // Act
        User result = persistenceService.findOrCreateUser("test-repo", "testuser",
                "test@example.com", "Test User", processorItemId);

        // Assert
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertEquals("test@example.com", result.getEmail());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testFindOrCreateUser_DuplicateKeyOnSave_RetriesAndReturns() {
        // Arrange
        User existingUser = testUser;
        existingUser.setId(new ObjectId());
        when(userRepository.findByProcessorItemIdAndUsername(processorItemId, "testuser"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("Duplicate"));

        // Act
        User result = persistenceService.findOrCreateUser("test-repo", "testuser",
                "test@example.com", "Test User", processorItemId);

        // Assert
        assertEquals(existingUser.getId(), result.getId());
        verify(userRepository, times(2)).findByProcessorItemIdAndUsername(processorItemId, "testuser");
    }

    @Test
    void testFindOrCreateUser_DuplicateKeyAndNotFound_ThrowsException() {
        // Arrange
        when(userRepository.findByProcessorItemIdAndUsername(processorItemId, "testuser"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("Duplicate"));

        // Act & Assert
        assertThrows(DataProcessingException.class,
                () -> persistenceService.findOrCreateUser("test-repo", "testuser",
                        "test@example.com", "Test User", processorItemId));
    }

    @Test
    void testSaveCommits_AllNewCommits_Success() throws DataProcessingException {
        // Arrange
        List<ScmCommits> commits = List.of(testCommit);
        when(commitRepository.findByProcessorItemIdAndSha(processorItemId, "abc123"))
                .thenReturn(Optional.empty());
        when(commitRepository.save(any(ScmCommits.class))).thenAnswer(invocation -> {
            ScmCommits saved = invocation.getArgument(0);
            saved.setId(new ObjectId());
            return saved;
        });

        // Act
        persistenceService.saveCommits(commits);

        // Assert
        verify(commitRepository).save(argThat(commit ->
                commit.getCreatedAt() != null && commit.getUpdatedAt() != null));
    }

    @Test
    void testSaveCommits_MixedCommits_UpdatesAndCreates() throws DataProcessingException {
        // Arrange
        ScmCommits existingCommit = new ScmCommits();
        existingCommit.setId(new ObjectId());
        existingCommit.setSha("abc123");
        existingCommit.setProcessorItemId(processorItemId);
        existingCommit.setCommitMessage("Old message");
        existingCommit.setAddedLines(3);

        ScmCommits newCommit = new ScmCommits();
        newCommit.setSha("def456");
        newCommit.setProcessorItemId(processorItemId);
        newCommit.setCommitMessage("New commit");
        newCommit.setAddedLines(4);

        List<ScmCommits> commits = Arrays.asList(testCommit, newCommit);

        when(commitRepository.findByProcessorItemIdAndSha(processorItemId, "abc123"))
                .thenReturn(Optional.of(existingCommit));
        when(commitRepository.findByProcessorItemIdAndSha(processorItemId, "def456"))
                .thenReturn(Optional.empty());
        when(commitRepository.save(any(ScmCommits.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        persistenceService.saveCommits(commits);

        // Assert
        verify(commitRepository, times(2)).save(any(ScmCommits.class));
        verify(commitRepository).save(argThat(commit ->
                "Test commit".equals(commit.getCommitMessage()) && commit.getId() != null));
    }

    @Test
    void testSaveCommits_Exception_ThrowsDataProcessingException() {
        // Arrange
        List<ScmCommits> commits = List.of(testCommit);
        when(commitRepository.findByProcessorItemIdAndSha(any(), any()))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        DataProcessingException exception = assertThrows(DataProcessingException.class,
                () -> persistenceService.saveCommits(commits));
        assertTrue(exception.getMessage().contains("Failed to batch save commits"));
    }

    @Test
    void testSaveMergeRequests_AllNew_Success() throws DataProcessingException {
        // Arrange
        List<ScmMergeRequests> mergeRequests = List.of(testMergeRequest);
        when(mergeRequestRepository.findByProcessorItemIdAndExternalId(processorItemId, "MR-123"))
                .thenReturn(Optional.empty());
        when(mergeRequestRepository.save(any(ScmMergeRequests.class))).thenAnswer(invocation -> {
            ScmMergeRequests saved = invocation.getArgument(0);
            saved.setId(new ObjectId());
            return saved;
        });

        // Act
        persistenceService.saveMergeRequests(mergeRequests);

        // Assert
        verify(mergeRequestRepository).save(any(ScmMergeRequests.class));
    }

    @Test
    void testSaveMergeRequests_Mixed_UpdatesAndCreates() throws DataProcessingException {
        // Arrange
        ScmMergeRequests existingMR = new ScmMergeRequests();
        existingMR.setId(new ObjectId());
        existingMR.setExternalId("MR-123");
        existingMR.setProcessorItemId(processorItemId);
        existingMR.setTitle("Old title");
        existingMR.setState("OPEN");

        ScmMergeRequests newMR = new ScmMergeRequests();
        newMR.setExternalId("MR-456");
        newMR.setProcessorItemId(processorItemId);
        newMR.setTitle("New MR");
        newMR.setState("OPEN");

        List<ScmMergeRequests> mergeRequests = Arrays.asList(testMergeRequest, newMR);

        when(mergeRequestRepository.findByProcessorItemIdAndExternalId(processorItemId, "MR-123"))
                .thenReturn(Optional.of(existingMR));
        when(mergeRequestRepository.findByProcessorItemIdAndExternalId(processorItemId, "MR-456"))
                .thenReturn(Optional.empty());
        when(mergeRequestRepository.save(any(ScmMergeRequests.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        persistenceService.saveMergeRequests(mergeRequests);

        // Assert
        verify(mergeRequestRepository, times(2)).save(any(ScmMergeRequests.class));
        verify(mergeRequestRepository).save(argThat(mr ->
                "Test MR".equals(mr.getTitle()) && mr.getId() != null));
    }

    @Test
    void testSaveMergeRequests_Exception_ThrowsDataProcessingException() {
        // Arrange
        List<ScmMergeRequests> mergeRequests = List.of(testMergeRequest);
        when(mergeRequestRepository.findByProcessorItemIdAndExternalId(any(), any()))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        DataProcessingException exception = assertThrows(DataProcessingException.class,
                () -> persistenceService.saveMergeRequests(mergeRequests));
        assertTrue(exception.getMessage().contains("Failed to batch save merge requests"));
    }

    @Test
    void testFindMergeRequestsByToolConfigIdAndState_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<ScmMergeRequests> mergeRequests = List.of(testMergeRequest);
        Page<ScmMergeRequests> expectedPage = new PageImpl<>(mergeRequests);

        when(mergeRequestRepository.findByProcessorItemIdAndState(processorItemId,
                ScmMergeRequests.MergeRequestState.OPEN, pageable))
                .thenReturn(expectedPage);

        // Act
        Page<ScmMergeRequests> result = persistenceService.findMergeRequestsByToolConfigIdAndState(
                processorItemId, ScmMergeRequests.MergeRequestState.OPEN, pageable);

        // Assert
        assertEquals(expectedPage, result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    void testUpdateCommitFields_AllFieldsNonNull_UpdatesAll() throws DataProcessingException {
        // Arrange
        ScmCommits target = new ScmCommits();
        target.setId(new ObjectId());
        target.setSha("abc123");
        target.setProcessorItemId(processorItemId);

        ScmCommits source = createFullyPopulatedCommit();

        when(commitRepository.findByProcessorItemIdAndSha(source.getProcessorItemId(), source.getSha()))
                .thenReturn(Optional.of(target));
        when(commitRepository.save(any(ScmCommits.class))).thenAnswer(invocation -> {
            ScmCommits saved = invocation.getArgument(0);
            // Verify all fields were updated
            assertEquals(source.getCommitMessage(), saved.getCommitMessage());
            assertEquals(source.getCommitAuthorId(), saved.getCommitAuthorId());
            assertEquals(source.getCommitAuthor(), saved.getCommitAuthor());
            assertEquals(source.getCommitterId(), saved.getCommitterId());
            assertEquals(source.getCommitter(), saved.getCommitter());
            assertEquals(source.getCommitTimestamp(), saved.getCommitTimestamp());
            assertEquals(source.getAuthorName(), saved.getAuthorName());
            assertEquals(source.getAuthorEmail(), saved.getAuthorEmail());
            assertEquals(source.getCommitterName(), saved.getCommitterName());
            assertEquals(source.getCommitterEmail(), saved.getCommitterEmail());
            assertEquals(source.getAddedLines(), saved.getAddedLines());
            assertEquals(source.getRemovedLines(), saved.getRemovedLines());
            assertEquals(source.getChangedLines(), saved.getChangedLines());
            assertEquals(source.getFileChanges(), saved.getFileChanges());
            assertEquals(source.getBranchName(), saved.getBranchName());
            assertEquals(source.getTargetBranch(), saved.getTargetBranch());
            assertEquals(source.getRepositoryName(), saved.getRepositoryName());
            assertEquals(source.getParentShas(), saved.getParentShas());
            assertEquals(source.getIsMergeCommit(), saved.getIsMergeCommit());
            assertEquals(source.getFilesChanged(), saved.getFilesChanged());
            assertEquals(source.getCommitUrl(), saved.getCommitUrl());
            assertEquals(source.getTags(), saved.getTags());
            assertEquals(source.getPlatformData(), saved.getPlatformData());
            assertEquals(source.getRepoSlug(), saved.getRepoSlug());
            return saved;
        });

        // Act
        persistenceService.saveCommits(List.of(source));

        // Assert
        verify(commitRepository).save(any(ScmCommits.class));
    }

    @Test
    void testUpdateCommitFields_SomeFieldsNull_UpdatesOnlyNonNull() throws DataProcessingException {
        // Arrange
        ScmCommits target = new ScmCommits();
        target.setId(new ObjectId());
        target.setSha("abc123");
        target.setProcessorItemId(processorItemId);
        target.setCommitMessage("Original message");
        target.setAuthorName("Original author");

        ScmCommits source = new ScmCommits();
        source.setSha("abc123");
        source.setProcessorItemId(processorItemId);
        source.setCommitMessage("Updated message");
        // authorName is null, should not update

        when(commitRepository.findByProcessorItemIdAndSha(processorItemId, "abc123"))
                .thenReturn(Optional.of(target));
        when(commitRepository.save(any(ScmCommits.class))).thenAnswer(invocation -> {
            ScmCommits saved = invocation.getArgument(0);
            assertEquals("Updated message", saved.getCommitMessage());
            assertEquals("Original author", saved.getAuthorName()); // Should remain unchanged
            return saved;
        });

        // Act
        persistenceService.saveCommits(List.of(source));

        // Assert
        verify(commitRepository).save(any(ScmCommits.class));
    }

    @Test
    void testUpdateMergeRequestFields_AllFieldsNonNull_UpdatesAll() throws DataProcessingException {
        // Arrange
        ScmMergeRequests target = new ScmMergeRequests();
        target.setId(new ObjectId());
        target.setExternalId("MR-123");
        target.setProcessorItemId(processorItemId);

        ScmMergeRequests source = createFullyPopulatedMergeRequest();

        when(mergeRequestRepository.findByProcessorItemIdAndExternalId(source.getProcessorItemId(), source.getExternalId()))
                .thenReturn(Optional.of(target));
        when(mergeRequestRepository.save(any(ScmMergeRequests.class))).thenAnswer(invocation -> {
            ScmMergeRequests saved = invocation.getArgument(0);
            // Verify all fields were updated
            assertEquals(source.getTitle(), saved.getTitle());
            assertEquals(source.getSummary(), saved.getSummary());
            assertEquals(source.getState(), saved.getState());
            assertEquals(source.getFromBranch(), saved.getFromBranch());
            assertEquals(source.getToBranch(), saved.getToBranch());
            assertEquals(source.getAuthorId(), saved.getAuthorId());
            assertEquals(source.getAuthorUserId(), saved.getAuthorUserId());
            assertEquals(source.getAssigneeUserIds(), saved.getAssigneeUserIds());
            assertEquals(source.getReviewerIds(), saved.getReviewerIds());
            assertEquals(source.getReviewers(), saved.getReviewers());
            assertEquals(source.getReviewerUsers(), saved.getReviewerUsers());
            assertEquals(source.getReviewerUserIds(), saved.getReviewerUserIds());
            assertEquals(source.getMergedAt(), saved.getMergedAt());
            assertEquals(source.getClosedDate(), saved.getClosedDate());
            assertEquals(source.getCreatedDate(), saved.getCreatedDate());
            assertEquals(source.getUpdatedOn(), saved.getUpdatedOn());
            assertEquals(source.getLinesChanged(), saved.getLinesChanged());
            assertEquals(source.getPickedForReviewOn(), saved.getPickedForReviewOn());
            assertEquals(source.getFirstCommitDate(), saved.getFirstCommitDate());
            assertEquals(source.getMergeCommitSha(), saved.getMergeCommitSha());
            assertEquals(source.getCommitShas(), saved.getCommitShas());
            assertEquals(source.getCommitCount(), saved.getCommitCount());
            assertEquals(source.getFilesChanged(), saved.getFilesChanged());
            assertEquals(source.getAddedLines(), saved.getAddedLines());
            assertEquals(source.getRemovedLines(), saved.getRemovedLines());
            assertEquals(source.getLabels(), saved.getLabels());
            assertEquals(source.getMilestone(), saved.getMilestone());
            assertEquals(source.getIsDraft(), saved.getIsDraft());
            assertEquals(source.getHasConflicts(), saved.getHasConflicts());
            assertEquals(source.getIsMergeable(), saved.getIsMergeable());
            assertEquals(source.getMergeRequestUrl(), saved.getMergeRequestUrl());
            assertEquals(source.getPlatformData(), saved.getPlatformData());
            assertEquals(source.getCommentCount(), saved.getCommentCount());
            assertEquals(source.getRepoSlug(), saved.getRepoSlug());
            return saved;
        });

        // Act
        persistenceService.saveMergeRequests(List.of(source));

        // Assert
        verify(mergeRequestRepository).save(any(ScmMergeRequests.class));
    }

    @Test
    void testUpdateMergeRequestFields_StateMerged_SetsClosed() throws DataProcessingException {
        // Arrange
        ScmMergeRequests target = new ScmMergeRequests();
        target.setId(new ObjectId());
        target.setExternalId("MR-123");
        target.setProcessorItemId(processorItemId);

        ScmMergeRequests source = new ScmMergeRequests();
        source.setExternalId("MR-123");
        source.setProcessorItemId(processorItemId);
        source.setState("MERGED");

        when(mergeRequestRepository.findByProcessorItemIdAndExternalId(processorItemId, "MR-123"))
                .thenReturn(Optional.of(target));
        when(mergeRequestRepository.save(any(ScmMergeRequests.class))).thenAnswer(invocation -> {
            ScmMergeRequests saved = invocation.getArgument(0);
            assertTrue(saved.isClosed());
            assertFalse(saved.isOpen());
            return saved;
        });

        // Act
        persistenceService.saveMergeRequests(List.of(source));

        // Assert
        verify(mergeRequestRepository).save(any(ScmMergeRequests.class));
    }

    @Test
    void testUpdateMergeRequestFields_StateNotMerged_SetsOpen() throws DataProcessingException {
        // Arrange
        ScmMergeRequests target = new ScmMergeRequests();
        target.setId(new ObjectId());
        target.setExternalId("MR-123");
        target.setProcessorItemId(processorItemId);

        ScmMergeRequests source = new ScmMergeRequests();
        source.setExternalId("MR-123");
        source.setProcessorItemId(processorItemId);
        source.setState("OPEN");

        when(mergeRequestRepository.findByProcessorItemIdAndExternalId(processorItemId, "MR-123"))
                .thenReturn(Optional.of(target));
        when(mergeRequestRepository.save(any(ScmMergeRequests.class))).thenAnswer(invocation -> {
            ScmMergeRequests saved = invocation.getArgument(0);
            assertTrue(saved.isOpen());
            assertFalse(saved.isClosed());
            return saved;
        });

        // Act
        persistenceService.saveMergeRequests(List.of(source));

        // Assert
        verify(mergeRequestRepository).save(any(ScmMergeRequests.class));
    }

    // Helper methods
    private ScmCommits createFullyPopulatedCommit() {
        ScmCommits commit = new ScmCommits();
        commit.setSha("abc123");
        commit.setProcessorItemId(processorItemId);
        commit.setCommitMessage("Test commit message");
        commit.setCommitAuthorId("author123");
        commit.setCommitAuthor(new User());
        commit.setCommitterId("committer123");
        commit.setCommitter(new User());
        commit.setCommitTimestamp(Instant.now().toEpochMilli());
        commit.setAuthorName("Author Name");
        commit.setAuthorEmail("author@test.com");
        commit.setCommitterName("Committer Name");
        commit.setCommitterEmail("committer@test.com");
        commit.setAddedLines(100);
        commit.setRemovedLines(50);
        commit.setChangedLines(150);
        commit.setBranchName("feature-branch");
        commit.setTargetBranch("main");
        commit.setRepositoryName("test-repo");
        commit.setParentShas(Arrays.asList("parent1", "parent2"));
        commit.setIsMergeCommit(false);
        commit.setFilesChanged(2);
        commit.setCommitUrl("https://git.example.com/commit/abc123");
        commit.setTags(Arrays.asList("v1.0", "release"));
        commit.setRepoSlug("test-repo-slug");
        return commit;
    }

    private ScmMergeRequests createFullyPopulatedMergeRequest() {
        ScmMergeRequests mr = new ScmMergeRequests();
        mr.setExternalId("MR-123");
        mr.setProcessorItemId(processorItemId);
        mr.setTitle("Test Merge Request");
        mr.setSummary("Test summary");
        mr.setState("OPEN");
        mr.setFromBranch("feature-branch");
        mr.setToBranch("main");
        mr.setAuthorId(new User());
        mr.setAuthorUserId(new ObjectId().toString());
        mr.setAssigneeUserIds(List.of(new ObjectId()));
        mr.setReviewerIds(Arrays.asList("reviewer1", "reviewer2"));
        mr.setReviewers(Arrays.asList("Reviewer One", "Reviewer Two"));
        mr.setMergedAt(LocalDateTime.now());
        mr.setClosedDate(Instant.now().toEpochMilli());
        mr.setCreatedDate(Instant.now().toEpochMilli());
        mr.setUpdatedOn(LocalDateTime.now());
        mr.setLinesChanged(200);
        mr.setPickedForReviewOn(Instant.now().toEpochMilli());
        mr.setFirstCommitDate(LocalDateTime.now());
        mr.setMergeCommitSha("merge123");
        mr.setCommitShas(Arrays.asList("commit1", "commit2"));
        mr.setCommitCount(2);
        mr.setFilesChanged(5);
        mr.setAddedLines(150);
        mr.setRemovedLines(50);
        mr.setLabels(Arrays.asList("bug", "enhancement"));
        mr.setMilestone("v1.0");
        mr.setIsDraft(false);
        mr.setHasConflicts(false);
        mr.setIsMergeable(true);
        mr.setMergeRequestUrl("https://git.example.com/mr/123");
        mr.setPlatformData(new HashMap<>());
        mr.setCommentCount(10);
        mr.setRepoSlug("test-repo-slug");
        return mr;
    }
}
