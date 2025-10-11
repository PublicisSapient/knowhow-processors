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

package com.publicissapient.knowhow.processor.scm.service.core.processor;

import com.publicissapient.knowhow.processor.scm.service.core.PersistenceService;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataReferenceUpdaterTest {

    @Mock
    private PersistenceService persistenceService;

    @InjectMocks
    private DataReferenceUpdater dataReferenceUpdater;

    private Map<String, User> userMap;
    private String repositoryName;
    private User authorUser;
    private User committerUser;
    private User reviewerUser1;
    private User reviewerUser2;

    @BeforeEach
    void setUp() {
        repositoryName = "test-repo";

        // Create test users
        authorUser = new User();
        authorUser.setId(new ObjectId());
        authorUser.setUsername("author1");
        authorUser.setEmail("author1@test.com");
        authorUser.setDisplayName("Author One");

        committerUser = new User();
        committerUser.setId(new ObjectId());
        committerUser.setUsername("committer1");
        committerUser.setEmail("committer1@test.com");
        committerUser.setDisplayName("Committer One");

        reviewerUser1 = new User();
        reviewerUser1.setId(new ObjectId());
        reviewerUser1.setUsername("reviewer1");
        reviewerUser1.setEmail("reviewer1@test.com");
        reviewerUser1.setDisplayName("Reviewer One");

        reviewerUser2 = new User();
        reviewerUser2.setId(new ObjectId());
        reviewerUser2.setUsername("reviewer2");
        reviewerUser2.setEmail("reviewer2@test.com");
        reviewerUser2.setDisplayName("Reviewer Two");

        userMap = new HashMap<>();
        userMap.put("author1", authorUser);
        userMap.put("committer1", committerUser);
        userMap.put("reviewer1", reviewerUser1);
        userMap.put("reviewer2", reviewerUser2);
    }

    @Test
    void testUpdateCommitsWithUserReferences_ValidAuthorAndCommitter_Success() {
        // Arrange
        ScmCommits commit = new ScmCommits();
        commit.setAuthorName("author1");
        commit.setCommitterName("committer1");
        List<ScmCommits> commits = List.of(commit);

        // Act
        dataReferenceUpdater.updateCommitsWithUserReferences(commits, userMap, repositoryName);

        // Assert
        assertEquals(repositoryName, commit.getRepositoryName());
        assertEquals(authorUser, commit.getCommitAuthor());
        assertEquals(committerUser, commit.getCommitter());
    }

    @Test
    void testUpdateCommitsWithUserReferences_AuthorFoundCommitterNotFound_PartialUpdate() {
        // Arrange
        ScmCommits commit = new ScmCommits();
        commit.setAuthorName("author1");
        commit.setCommitterName("unknown-committer");
        List<ScmCommits> commits = List.of(commit);

        // Act
        dataReferenceUpdater.updateCommitsWithUserReferences(commits, userMap, repositoryName);

        // Assert
        assertEquals(repositoryName, commit.getRepositoryName());
        assertEquals(authorUser, commit.getCommitAuthor());
        assertNull(commit.getCommitter());
        assertNull(commit.getCommitterId());
    }

    @Test
    void testUpdateCommitsWithUserReferences_AuthorFoundByEmail_Success() {
        // Arrange
        ScmCommits commit = new ScmCommits();
        commit.setAuthorName("unknown-author");
        commit.setCommitterName("committer1");

        // Add author by email to map
        userMap.put("unknown-author", authorUser);

        List<ScmCommits> commits = List.of(commit);

        // Act
        dataReferenceUpdater.updateCommitsWithUserReferences(commits, userMap, repositoryName);

        // Assert
        assertEquals(repositoryName, commit.getRepositoryName());
        assertEquals(authorUser, commit.getCommitAuthor());
    }

    @Test
    void testUpdateCommitsWithUserReferences_NoUsersFound_OnlyRepoNameSet() {
        // Arrange
        ScmCommits commit = new ScmCommits();
        commit.setAuthorName("unknown-author");
        commit.setCommitterName("unknown-committer");
        List<ScmCommits> commits = List.of(commit);

        // Act
        dataReferenceUpdater.updateCommitsWithUserReferences(commits, userMap, repositoryName);

        // Assert
        assertEquals(repositoryName, commit.getRepositoryName());
        assertNull(commit.getCommitAuthor());
        assertNull(commit.getCommitAuthorId());
        assertNull(commit.getCommitter());
        assertNull(commit.getCommitterId());
    }

    @Test
    void testUpdateCommitsWithUserReferences_NullUserNames_NoException() {
        // Arrange
        ScmCommits commit = new ScmCommits();
        commit.setAuthorName(null);
        commit.setCommitterName(null);
        List<ScmCommits> commits = List.of(commit);

        // Act & Assert (should not throw exception)
        assertDoesNotThrow(() ->
                dataReferenceUpdater.updateCommitsWithUserReferences(commits, userMap, repositoryName)
        );

        assertEquals(repositoryName, commit.getRepositoryName());
        assertNull(commit.getCommitAuthor());
        assertNull(commit.getCommitter());
    }

    @Test
    void testUpdateMergeRequestsWithUserReferences_ValidAuthorAndReviewers_Success() {
        // Arrange
        ScmMergeRequests mr = new ScmMergeRequests();
        mr.setAuthorId(authorUser);
        mr.setAuthorUserId("author1");
        mr.setReviewers(Arrays.asList("reviewer1", "reviewer2"));
        List<ScmMergeRequests> mergeRequests = List.of(mr);

        // Act
        dataReferenceUpdater.updateMergeRequestsWithUserReferences(mergeRequests, userMap, repositoryName);

        // Assert
        assertEquals(repositoryName, mr.getRepositoryName());
        assertEquals(authorUser, mr.getAuthorId());
        assertEquals(2, mr.getReviewerUsers().size());
        assertTrue(mr.getReviewerUsers().contains(reviewerUser1));
        assertTrue(mr.getReviewerUsers().contains(reviewerUser2));
    }

    @Test
    void testUpdateMergeRequestsWithUserReferences_AuthorNotInMap_CreatesNewUser() {
        // Arrange
        User newUser = new User();
        newUser.setId(new ObjectId());
        newUser.setUsername("newauthor");
        newUser.setEmail("newauthor@test.com");
        newUser.setDisplayName("New Author");

        ScmMergeRequests mr = new ScmMergeRequests();
        User tempAuthor = new User();
        tempAuthor.setEmail("newauthor@test.com");
        tempAuthor.setDisplayName("New Author");
        mr.setAuthorId(tempAuthor);
        mr.setAuthorUserId("newauthor");
        mr.setProcessorItemId(new ObjectId());

        List<ScmMergeRequests> mergeRequests = List.of(mr);

        when(persistenceService.findOrCreateUser(
                eq(repositoryName),
                eq("newauthor"),
                eq("newauthor@test.com"),
                eq("New Author"),
                any()
        )).thenReturn(newUser);

        // Act
        dataReferenceUpdater.updateMergeRequestsWithUserReferences(mergeRequests, userMap, repositoryName);

        // Assert
        assertEquals(repositoryName, mr.getRepositoryName());
        assertEquals(newUser, mr.getAuthorId());
        verify(persistenceService, times(1)).findOrCreateUser(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void testUpdateMergeRequestsWithUserReferences_PersistenceException_ThrowsDataProcessingException() {
        // Arrange
        ScmMergeRequests mr = new ScmMergeRequests();
        User tempAuthor = new User();
        tempAuthor.setEmail("error@test.com");
        tempAuthor.setDisplayName("Error Author");
        mr.setAuthorId(tempAuthor);
        mr.setAuthorUserId("errorauthor");
        mr.setProcessorItemId(new ObjectId());

        List<ScmMergeRequests> mergeRequests = List.of(mr);

        when(persistenceService.findOrCreateUser(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        DataProcessingException exception = assertThrows(DataProcessingException.class, () ->
                dataReferenceUpdater.updateMergeRequestsWithUserReferences(mergeRequests, userMap, repositoryName)
        );

        assertEquals("Failed to save user", exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception.getCause());
    }

    @Test
    void testUpdateMergeRequestsWithUserReferences_PartialReviewersFound_UpdatesFoundOnly() {
        // Arrange
        ScmMergeRequests mr = new ScmMergeRequests();
        mr.setAuthorId(authorUser);
        mr.setAuthorUserId("author1");
        mr.setReviewers(Arrays.asList("reviewer1", "unknown-reviewer", "reviewer2"));
        List<ScmMergeRequests> mergeRequests = List.of(mr);

        // Act
        dataReferenceUpdater.updateMergeRequestsWithUserReferences(mergeRequests, userMap, repositoryName);

        // Assert
        assertEquals(repositoryName, mr.getRepositoryName());
        assertEquals(2, mr.getReviewerUsers().size());
        assertTrue(mr.getReviewerUsers().contains(reviewerUser1));
        assertTrue(mr.getReviewerUsers().contains(reviewerUser2));
    }

    @Test
    void testUpdateMergeRequestsWithUserReferences_NullAuthorId_SkipsAuthorProcessing() {
        // Arrange
        ScmMergeRequests mr = new ScmMergeRequests();
        mr.setAuthorId(null);
        mr.setAuthorUserId(null);
        mr.setReviewers(List.of("reviewer1"));
        List<ScmMergeRequests> mergeRequests = List.of(mr);

        // Act
        dataReferenceUpdater.updateMergeRequestsWithUserReferences(mergeRequests, userMap, repositoryName);

        // Assert
        assertEquals(repositoryName, mr.getRepositoryName());
        assertNull(mr.getAuthorId());
        assertNull(mr.getAuthorUserId());
        assertEquals(1, mr.getReviewerUsers().size());
        verifyNoInteractions(persistenceService);
    }

    @Test
    void testUpdateMergeRequestsWithUserReferences_EmptyReviewers_NoReviewerProcessing() {
        // Arrange
        ScmMergeRequests mr = new ScmMergeRequests();
        mr.setAuthorId(authorUser);
        mr.setAuthorUserId("author1");
        mr.setReviewers(new ArrayList<>());
        List<ScmMergeRequests> mergeRequests = List.of(mr);

        // Act
        dataReferenceUpdater.updateMergeRequestsWithUserReferences(mergeRequests, userMap, repositoryName);

        // Assert
        assertEquals(repositoryName, mr.getRepositoryName());
        assertEquals(authorUser, mr.getAuthorId());
        assertNull(mr.getReviewerUsers());
        assertNull(mr.getReviewerUserIds());
    }

    @Test
    void testUpdateMergeRequestsWithUserReferences_NullReviewers_NoReviewerProcessing() {
        // Arrange
        ScmMergeRequests mr = new ScmMergeRequests();
        mr.setAuthorId(authorUser);
        mr.setAuthorUserId("author1");
        mr.setReviewers(null);
        List<ScmMergeRequests> mergeRequests = List.of(mr);

        // Act
        dataReferenceUpdater.updateMergeRequestsWithUserReferences(mergeRequests, userMap, repositoryName);

        // Assert
        assertEquals(repositoryName, mr.getRepositoryName());
        assertEquals(authorUser, mr.getAuthorId());
        assertNull(mr.getReviewerUsers());
        assertNull(mr.getReviewerUserIds());
    }

    @Test
    void testUpdateCommitsWithUserReferences_MultipleCommits_AllUpdated() {
        // Arrange
        ScmCommits commit1 = new ScmCommits();
        commit1.setAuthorName("author1");
        commit1.setCommitterName("committer1");

        ScmCommits commit2 = new ScmCommits();
        commit2.setAuthorName("reviewer1");
        commit2.setCommitterName("reviewer2");

        List<ScmCommits> commits = Arrays.asList(commit1, commit2);

        // Act
        dataReferenceUpdater.updateCommitsWithUserReferences(commits, userMap, repositoryName);

        // Assert
        // Verify first commit
        assertEquals(repositoryName, commit1.getRepositoryName());
        assertEquals(authorUser, commit1.getCommitAuthor());
        assertEquals(committerUser, commit1.getCommitter());

        // Verify second commit
        assertEquals(repositoryName, commit2.getRepositoryName());
        assertEquals(reviewerUser1, commit2.getCommitAuthor());
        assertEquals(reviewerUser2, commit2.getCommitter());
    }

    @Test
    void testUpdateMergeRequestsWithUserReferences_AllReviewersNotFound_EmptyReviewerLists() {
        // Arrange
        ScmMergeRequests mr = new ScmMergeRequests();
        mr.setAuthorId(authorUser);
        mr.setAuthorUserId("author1");
        mr.setReviewers(Arrays.asList("unknown1", "unknown2"));
        List<ScmMergeRequests> mergeRequests = List.of(mr);

        // Act
        dataReferenceUpdater.updateMergeRequestsWithUserReferences(mergeRequests, userMap, repositoryName);

        // Assert
        assertEquals(repositoryName, mr.getRepositoryName());
        assertEquals(authorUser, mr.getAuthorId());
        assertNull(mr.getReviewerUsers());
        assertNull(mr.getReviewerUserIds());
    }

    @Test
    void testConstructor_WithPersistenceService_ProperlyInitialized() {
        // Act
        DataReferenceUpdater updater = new DataReferenceUpdater(persistenceService);

        // Assert
        assertNotNull(updater);
        // Constructor test to ensure proper initialization
    }

    @Test
    void testUpdateCommitsWithUserReferences_EmptyCommitsList_NoProcessing() {
        // Arrange
        List<ScmCommits> commits = new ArrayList<>();

        // Act & Assert (should not throw exception)
        assertDoesNotThrow(() ->
                dataReferenceUpdater.updateCommitsWithUserReferences(commits, userMap, repositoryName)
        );
    }

    @Test
    void testUpdateMergeRequestsWithUserReferences_EmptyMergeRequestsList_NoProcessing() {
        // Arrange
        List<ScmMergeRequests> mergeRequests = new ArrayList<>();

        // Act & Assert (should not throw exception)
        assertDoesNotThrow(() ->
                dataReferenceUpdater.updateMergeRequestsWithUserReferences(mergeRequests, userMap, repositoryName)
        );

        verifyNoInteractions(persistenceService);
    }
}
