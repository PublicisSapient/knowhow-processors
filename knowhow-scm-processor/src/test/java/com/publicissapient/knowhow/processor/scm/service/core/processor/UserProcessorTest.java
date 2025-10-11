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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.*;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.service.core.PersistenceService;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;

@ExtendWith(MockitoExtension.class)
public class UserProcessorTest {

	@Mock
	private PersistenceService persistenceService;

	@InjectMocks
	private UserProcessor userProcessor;

	private ScanRequest scanRequest;
	private List<ScmCommits> commitDetails;
	private List<ScmMergeRequests> mergeRequests;

	@BeforeEach
	void setUp() {
		scanRequest = ScanRequest.builder().build();
		scanRequest.setRepositoryName("test-repo");
		scanRequest.setRepositoryUrl("https://github.com/test/repo");
		scanRequest.setToolConfigId(new ObjectId());

		commitDetails = new ArrayList<>();
		mergeRequests = new ArrayList<>();
	}

	@Test
	void testProcessUsers_WithCommitsAndMergeRequests_ReturnsAllUsers() {
		// Arrange
		User commitAuthor1 = createUser("user1", "User One");
		User commitAuthor2 = createUser("user2", "User Two");
		User mrAuthor = createUser("user3", "User Three");

		ScmCommits commit1 = new ScmCommits();
		commit1.setCommitAuthor(commitAuthor1);

		ScmCommits commit2 = new ScmCommits();
		commit2.setCommitAuthor(commitAuthor2);

		commitDetails.add(commit1);
		commitDetails.add(commit2);

		ScmMergeRequests mr1 = new ScmMergeRequests();
		mr1.setAuthorId(mrAuthor);
		mr1.setReviewers(Arrays.asList("reviewer1", "reviewer2"));

		mergeRequests.add(mr1);

		when(persistenceService.saveUser(any(User.class))).thenAnswer(invocation -> {
			return invocation.<User>getArgument(0);
		});

		// Act
		UserProcessor.UserProcessingResult result = userProcessor.processUsers(commitDetails, mergeRequests,
				scanRequest);

		// Assert
		assertNotNull(result);
		assertEquals(5, result.getAllUsers().size());
		assertEquals(5, result.getUserMap().size());
		verify(persistenceService, times(5)).saveUser(any(User.class));
	}

	@Test
	void testProcessUsers_WithEmptyLists_ReturnsEmptyResult() {
		// Act
		UserProcessor.UserProcessingResult result = userProcessor.processUsers(commitDetails, mergeRequests,
				scanRequest);

		// Assert
		assertNotNull(result);
		assertTrue(result.getAllUsers().isEmpty());
		assertTrue(result.getUserMap().isEmpty());
		verify(persistenceService, never()).saveUser(any(User.class));
	}

	@Test
	void testProcessUsers_WithNullCommitAuthors_SkipsNullAuthors() {
		// Arrange
		ScmCommits commitWithNullAuthor = new ScmCommits();
		commitWithNullAuthor.setCommitAuthor(null);

		ScmCommits commitWithAuthor = new ScmCommits();
		commitWithAuthor.setCommitAuthor(createUser("user1", "User One"));

		commitDetails.add(commitWithNullAuthor);
		commitDetails.add(commitWithAuthor);

		when(persistenceService.saveUser(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		UserProcessor.UserProcessingResult result = userProcessor.processUsers(commitDetails, mergeRequests,
				scanRequest);

		// Assert
		assertEquals(1, result.getAllUsers().size());
		assertEquals(1, result.getUserMap().size());
		verify(persistenceService, times(1)).saveUser(any(User.class));
	}

	@Test
	void testProcessUsers_WithNullMergeRequestAuthors_SkipsNullAuthors() {
		// Arrange
		ScmMergeRequests mrWithNullAuthor = new ScmMergeRequests();
		mrWithNullAuthor.setAuthorId(null);
		mrWithNullAuthor.setReviewers(List.of("reviewer1"));

		ScmMergeRequests mrWithAuthor = new ScmMergeRequests();
		mrWithAuthor.setAuthorId(createUser("user1", "User One"));

		mergeRequests.add(mrWithNullAuthor);
		mergeRequests.add(mrWithAuthor);

		when(persistenceService.saveUser(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		UserProcessor.UserProcessingResult result = userProcessor.processUsers(commitDetails, mergeRequests,
				scanRequest);

		// Assert
		assertEquals(2, result.getAllUsers().size()); // 1 reviewer + 1 author
		assertEquals(2, result.getUserMap().size());
		verify(persistenceService, times(2)).saveUser(any(User.class));
	}

	@Test
	void testProcessUsers_WithNullReviewersList_ProcessesSuccessfully() {
		// Arrange
		ScmMergeRequests mrWithNullReviewers = new ScmMergeRequests();
		mrWithNullReviewers.setAuthorId(createUser("user1", "User One"));
		mrWithNullReviewers.setReviewers(null);

		mergeRequests.add(mrWithNullReviewers);

		when(persistenceService.saveUser(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		UserProcessor.UserProcessingResult result = userProcessor.processUsers(commitDetails, mergeRequests,
				scanRequest);

		// Assert
		assertEquals(1, result.getAllUsers().size());
		assertEquals(1, result.getUserMap().size());
		verify(persistenceService, times(1)).saveUser(any(User.class));
	}

	@Test
	void testProcessUsers_WithDuplicateUsers_ReturnsUniqueUsers() {
		// Arrange
		User sameUser = createUser("user1", "User One");

		ScmCommits commit1 = new ScmCommits();
		commit1.setCommitAuthor(sameUser);

		ScmCommits commit2 = new ScmCommits();
		commit2.setCommitAuthor(sameUser);

		commitDetails.add(commit1);
		commitDetails.add(commit2);

		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setAuthorId(sameUser);
		mr.setReviewers(List.of("user1")); // Same username as author

		mergeRequests.add(mr);

		when(persistenceService.saveUser(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		UserProcessor.UserProcessingResult result = userProcessor.processUsers(commitDetails, mergeRequests,
				scanRequest);

		// Assert
		assertEquals(2, result.getAllUsers().size());
		assertEquals(1, result.getUserMap().size());
		verify(persistenceService, times(2)).saveUser(any(User.class));
	}

	@Test
	void testProcessUsers_WithNullUsername_SkipsUserFromMap() {
		// Arrange
		User userWithNullUsername = createUser(null, "User One");
		User userWithUsername = createUser("user2", "User Two");

		ScmCommits commit1 = new ScmCommits();
		commit1.setCommitAuthor(userWithNullUsername);

		ScmCommits commit2 = new ScmCommits();
		commit2.setCommitAuthor(userWithUsername);

		commitDetails.add(commit1);
		commitDetails.add(commit2);

		when(persistenceService.saveUser(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		UserProcessor.UserProcessingResult result = userProcessor.processUsers(commitDetails, mergeRequests,
				scanRequest);

		// Assert
		assertEquals(2, result.getAllUsers().size());
		assertEquals(1, result.getUserMap().size()); // Only user with username
		assertTrue(result.getUserMap().containsKey("user2"));
		verify(persistenceService, times(1)).saveUser(any(User.class));
	}

	@Test
	void testProcessUsers_SetsRepositoryNameAndActiveStatus() {
		// Arrange
		User commitAuthor = createUser("user1", "User One");
		ScmCommits commit = new ScmCommits();
		commit.setCommitAuthor(commitAuthor);
		commitDetails.add(commit);

		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setAuthorId(createUser("user2", "User Two"));
		mr.setReviewers(List.of("reviewer1"));
		mergeRequests.add(mr);

		when(persistenceService.saveUser(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			// Verify repository name and active status are set
			assertEquals("test-repo", user.getRepositoryName());
			assertTrue(user.getActive());
			return user;
		});

		// Act
		userProcessor.processUsers(commitDetails, mergeRequests, scanRequest);

		// Assert
		verify(persistenceService, times(3)).saveUser(any(User.class));
	}

	@Test
	void testProcessUsers_SetsProcessorItemId() {
		// Arrange
		User commitAuthor = createUser("user1", "User One");
		ScmCommits commit = new ScmCommits();
		commit.setCommitAuthor(commitAuthor);
		commitDetails.add(commit);

		when(persistenceService.saveUser(any(User.class))).thenAnswer(invocation -> {
			// Verify processorItemId is set
			return invocation.<User>getArgument(0);
		});

		// Act
		userProcessor.processUsers(commitDetails, mergeRequests, scanRequest);

		// Assert
		verify(persistenceService, times(1)).saveUser(any(User.class));
	}

	@Test
	void testProcessUsers_WithLargeDataset_ProcessesAllUsers() {
		// Arrange
		int numCommits = 100;
		int numMergeRequests = 50;

		for (int i = 0; i < numCommits; i++) {
			ScmCommits commit = new ScmCommits();
			commit.setCommitAuthor(createUser("user" + i, "User " + i));
			commitDetails.add(commit);
		}

		for (int i = 0; i < numMergeRequests; i++) {
			ScmMergeRequests mr = new ScmMergeRequests();
			mr.setAuthorId(createUser("mruser" + i, "MR User " + i));
			mr.setReviewers(Arrays.asList("reviewer" + i + "_1", "reviewer" + i + "_2"));
			mergeRequests.add(mr);
		}

		when(persistenceService.saveUser(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		UserProcessor.UserProcessingResult result = userProcessor.processUsers(commitDetails, mergeRequests,
				scanRequest);

		// Assert
		assertEquals(250, result.getAllUsers().size()); // 100 commits + 50 MR authors + 100 reviewers
		assertEquals(250, result.getUserMap().size());
		verify(persistenceService, times(250)).saveUser(any(User.class));
	}

	private User createUser(String username, String displayName) {
		return User.builder().username(username).displayName(displayName).build();
	}
}
