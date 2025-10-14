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
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Updates data references between entities. Follows Single Responsibility
 * Principle.
 */
@Component
@Slf4j
public class DataReferenceUpdater {

    private static final String FAILED_TO_SAVE_USER = "Failed to save user";

    private final PersistenceService persistenceService;

    @Autowired
    public DataReferenceUpdater(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * Updates commits with user references and repository name.
     *
     * @param commitDetails
     *            the list of commits to update
     * @param userMap
     *            the map of users by username/email
     * @param repositoryName
     *            the repository name
     */
    public void updateCommitsWithUserReferences(List<ScmCommits> commitDetails, Map<String, User> userMap,
                                                String repositoryName) {
        for (ScmCommits commitDetail : commitDetails) {
            commitDetail.setRepositoryName(repositoryName);

            // Set author reference
            if (commitDetail.getAuthorName() != null) {
                User author = findUserByName(userMap, commitDetail.getAuthorName());
                if (author != null) {
                    commitDetail.setCommitAuthor(author);
                    commitDetail.setCommitAuthorId(String.valueOf(author.getId()));
                }
            }

            // Set committer reference
            if (commitDetail.getCommitterName() != null) {
                User committer = findUserByName(userMap, commitDetail.getCommitterName());
                if (committer != null) {
                    commitDetail.setCommitter(committer);
                    commitDetail.setCommitterId(String.valueOf(committer.getId()));
                }
            }
        }
    }

    /**
     * Updates merge requests with user references and repository name.
     *
     * @param mergeRequests
     *            the list of merge requests to update
     * @param userMap
     *            the map of users by username/email
     * @param repositoryName
     *            the repository name
     */
    public void updateMergeRequestsWithUserReferences(List<ScmMergeRequests> mergeRequests, Map<String, User> userMap,
                                                      String repositoryName) {
        for (ScmMergeRequests mr : mergeRequests) {
            mr.setRepositoryName(repositoryName);

            // Set author reference
            if (mr.getAuthorId() != null) {
                User author = userMap.get(mr.getAuthorUserId());
                if (author != null) {
                    mr.setAuthorId(author);
                    mr.setAuthorUserId(String.valueOf(author.getId()));
                } else {
                    createAndSetAuthorUser(mr, repositoryName);
                }
            }

            // Set reviewer references if available
            if (mr.getReviewers() != null && !mr.getReviewers().isEmpty()) {
                setReviewerReferences(mr, userMap);
            }
        }
    }

    /**
     * Finds a user by name in the user map.
     * CHANGE: Extracted method to eliminate duplicate code
     *
     * @param userMap the map of users
     * @param userName the user name to search for
     * @return the found user or null
     */
    private User findUserByName(Map<String, User> userMap, String userName) {
        return userMap.get(userName);
    }

    /**
     * Creates and sets the author user for a merge request.
     * CHANGE: Extracted method to improve readability and reduce complexity
     *
     * @param mr the merge request
     * @param repositoryName the repository name
     */
    private void createAndSetAuthorUser(ScmMergeRequests mr, String repositoryName) {
        try {
            User user = persistenceService.findOrCreateUser(
                    repositoryName,
                    mr.getAuthorUserId(),
                    mr.getAuthorId().getEmail(),
                    mr.getAuthorId().getDisplayName(),
                    mr.getProcessorItemId()
            );
            mr.setAuthorId(user);
            mr.setAuthorUserId(String.valueOf(user.getId()));
        } catch (Exception e) {
            log.error("Error saving user {}: {}", mr.getAuthorUserId(), e.getMessage(), e);
            throw new DataProcessingException(FAILED_TO_SAVE_USER, e);
        }
    }

    /**
     * Sets reviewer references for a merge request.
     * CHANGE: Extracted method to improve readability
     *
     * @param mr the merge request
     * @param userMap the map of users
     */
    private void setReviewerReferences(ScmMergeRequests mr, Map<String, User> userMap) {
        List<User> reviewerUsers = new ArrayList<>();
        List<String> reviewerUserIds = new ArrayList<>();

        for (String reviewerName : mr.getReviewers()) {
            User reviewerUser = userMap.get(reviewerName);
            if (reviewerUser != null) {
                reviewerUsers.add(reviewerUser);
                reviewerUserIds.add(String.valueOf(reviewerUser.getId()));
            }
        }

        if (!reviewerUsers.isEmpty()) {
            mr.setReviewerUsers(reviewerUsers);
            mr.setReviewerUserIds(reviewerUserIds);
        }
    }
}
