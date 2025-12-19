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

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.service.core.PersistenceService;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Processes and extracts users from commits and merge requests. Follows Single
 * Responsibility Principle.
 */
@Component
@Slf4j
public class UserProcessor {

	private final PersistenceService persistenceService;

	@Autowired
	public UserProcessor(PersistenceService persistenceService) {
		this.persistenceService = persistenceService;
	}

	/**
	 * Processes users from commits and merge requests.
	 *
	 * @param commitDetails
	 *            list of commits
	 * @param mergeRequests
	 *            list of merge requests
	 * @param scanRequest
	 *            scan request details
	 * @return user processing result containing user map and all users
	 */
	public UserProcessingResult processUsers(List<ScmCommits> commitDetails, List<ScmMergeRequests> mergeRequests,
			ScanRequest scanRequest) {

		// Extract users from commits
		Set<User> usersFromCommits = extractUsersFromCommits(commitDetails, scanRequest.getRepositoryName());

		// Extract users from merge requests
		Set<User> usersFromMergeRequests = extractUsersFromMergeRequests(mergeRequests,
				scanRequest.getRepositoryName());

		// Combine all users
		Set<User> allUsers = new HashSet<>();
		allUsers.addAll(usersFromCommits);
		allUsers.addAll(usersFromMergeRequests);

		// Persist users and create user map
		Map<String, User> userMap = new HashMap<>();
		if (!allUsers.isEmpty()) {
			for (User user : allUsers) {
				if (user.getUsername() != null) {
					user.setProcessorItemId(scanRequest.getToolConfigId());
					User savedUser = persistenceService.saveUser(user);
					userMap.put(savedUser.getUsername(), savedUser);
				}
			}
			log.info("Processed {} unique users for repository: {} ({})", allUsers.size(),
					scanRequest.getRepositoryName(), scanRequest.getRepositoryUrl());
		}

		return new UserProcessingResult(userMap, allUsers);
	}

	/**
	 * Extracts unique users from commits.
	 *
	 * @param commitDetails
	 *            the list of commits
	 * @param repositoryName
	 *            the repository name
	 * @return set of unique users
	 */
	private Set<User> extractUsersFromCommits(List<ScmCommits> commitDetails, String repositoryName) {
		Set<User> users = new HashSet<>();

		for (ScmCommits commitDetail : commitDetails) {
			if (commitDetail.getCommitAuthor() != null) {
				User commitAuthor = commitDetail.getCommitAuthor();
				commitAuthor.setRepositoryName(repositoryName);
				commitAuthor.setActive(true);
				users.add(commitAuthor);
			}
		}

		return users;
	}

	/**
	 * Extracts unique users from merge requests.
	 *
	 * @param mergeRequests
	 *            the list of merge requests
	 * @param repositoryName
	 *            the repository name
	 * @return set of unique users
	 */
	private Set<User> extractUsersFromMergeRequests(List<ScmMergeRequests> mergeRequests, String repositoryName) {
		Set<User> users = new HashSet<>();

		for (ScmMergeRequests mr : mergeRequests) {
			// Extract author
			if (mr.getAuthorId() != null) {
				User author = mr.getAuthorId();
				author.setRepositoryName(repositoryName);
				author.setActive(true);
				users.add(author);
			}

			// Extract reviewers
			if (mr.getReviewers() != null) {
				for (String reviewer : mr.getReviewers()) {
					User user = User.builder().repositoryName(repositoryName).username(reviewer).displayName(reviewer)
							.active(true).build();
					users.add(user);
				}
			}
		}

		return users;
	}

	/**
	 * Result class for user processing.
	 */
	@Getter
    @AllArgsConstructor
    public static class UserProcessingResult {
		private final Map<String, User> userMap;
		private final Set<User> allUsers;

    }
}
