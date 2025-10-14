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
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;

import com.publicissapient.kpidashboard.common.repository.scm.ScmCommitsRepository;
import com.publicissapient.kpidashboard.common.repository.scm.ScmMergeRequestsRepository;
import com.publicissapient.kpidashboard.common.repository.scm.ScmUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for persisting Git metadata to the database. This service handles the
 * persistence operations for users, commits, and merge requests. It provides
 * methods for saving, updating, and querying Git metadata with proper
 * transaction management and error handling. Implements the Single
 * Responsibility Principle by focusing solely on data persistence.
 */
@Service
@Transactional
@Slf4j
public class PersistenceService {

	private final ScmUserRepository userRepository;
	private final ScmCommitsRepository commitRepository;
	private final ScmMergeRequestsRepository mergeRequestRepository;

	@Autowired
	public PersistenceService(ScmUserRepository userRepository, ScmCommitsRepository commitRepository,
			ScmMergeRequestsRepository mergeRequestRepository) {
		this.userRepository = userRepository;
		this.commitRepository = commitRepository;
		this.mergeRequestRepository = mergeRequestRepository;
	}

	// User operations

	/**
	 * Saves or updates a user with repository-specific unique constraint.
	 * 
	 * @param user
	 *            the user to save
	 * @return the saved user
	 * @throws DataProcessingException
	 *             if saving fails
	 */
	public User saveUser(User user) throws DataProcessingException {
		try {
			log.debug("Saving user: {} for repository: {}", user.getUsername(), user.getRepositoryName());

			// Check if user already exists by repository name and username
			Optional<User> existingUser = userRepository.findByProcessorItemIdAndUsername(user.getProcessorItemId(),
					user.getUsername());

			if (existingUser.isPresent()) {
				return existingUser.get();

			}
			// Save new user
			user.setCreatedAt(LocalDateTime.now());
			user.setUpdatedAt(LocalDateTime.now());
			return userRepository.save(user);

		} catch (DuplicateKeyException e) {
			log.warn("Duplicate key exception for user {}, attempting to find and update existing user",
					user.getUsername());
			throw new DataProcessingException("Failed to save user due to duplicate key", e);
		} catch (Exception e) {
			log.error("Error saving user {}: {}", user.getUsername(), e.getMessage(), e);
			throw new DataProcessingException("Failed to save user", e);
		}
	}

	/**
	 * Finds or creates a user by repository name and username/email.
	 * 
	 * @param repositoryName
	 *            the repository name
	 * @param username
	 *            the username
	 * @param email
	 *            the email
	 * @param displayName
	 *            the display name
	 * @return the found or created user
	 */
	public User findOrCreateUser(String repositoryName, String username, String email, String displayName,
			ObjectId processorItemId) {
		// Try to find by username first
		Optional<User> existingUser = userRepository.findByProcessorItemIdAndUsername(processorItemId, username);
		if (existingUser.isPresent()) {
			return existingUser.get();
		}

		// Create new user
		User newUser = User.builder().repositoryName(repositoryName).username(username).email(email)
				.displayName(displayName).processorItemId(processorItemId).active(true).createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now()).build();

		try {
			return userRepository.save(newUser);
		} catch (DuplicateKeyException e) {
			// Race condition - try to find again
			existingUser = userRepository.findByProcessorItemIdAndUsername(processorItemId, username);
			return existingUser.orElseThrow(() -> new DataProcessingException("Failed to create user", e));
		}
	}

    /**
     * Updates commit fields from source to target commit.
     */
    private void updateCommitFields(ScmCommits target, ScmCommits source) {
        updateCommitBasicFields(target, source);
        updateCommitAuthorFields(target, source);
        updateCommitMetricsFields(target, source);
        updateCommitMetadataFields(target, source);
    }

    private void updateCommitBasicFields(ScmCommits target, ScmCommits source) {
        updateField(source.getCommitMessage(), target::setCommitMessage);
        updateField(source.getCommitTimestamp(), target::setCommitTimestamp);
        updateField(source.getBranchName(), target::setBranchName);
        updateField(source.getTargetBranch(), target::setTargetBranch);
        updateField(source.getRepositoryName(), target::setRepositoryName);
    }

    private void updateCommitAuthorFields(ScmCommits target, ScmCommits source) {
        updateField(source.getCommitAuthorId(), target::setCommitAuthorId);
        updateField(source.getCommitAuthor(), target::setCommitAuthor);
        updateField(source.getCommitterId(), target::setCommitterId);
        updateField(source.getCommitter(), target::setCommitter);
        updateField(source.getAuthorName(), target::setAuthorName);
        updateField(source.getAuthorEmail(), target::setAuthorEmail);
        updateField(source.getCommitterName(), target::setCommitterName);
        updateField(source.getCommitterEmail(), target::setCommitterEmail);
    }

    private void updateCommitMetricsFields(ScmCommits target, ScmCommits source) {
        updateField(source.getAddedLines(), target::setAddedLines);
        updateField(source.getRemovedLines(), target::setRemovedLines);
        updateField(source.getFilesChanged(), target::setFilesChanged);
        updateField(source.getFileChanges(), target::setFileChanges);
    }

    private void updateCommitMetadataFields(ScmCommits target, ScmCommits source) {
        updateField(source.getParentShas(), target::setParentShas);
        updateField(source.getIsMergeCommit(), target::setIsMergeCommit);
        updateField(source.getCommitUrl(), target::setCommitUrl);
        updateField(source.getTags(), target::setTags);
        updateField(source.getPlatformData(), target::setPlatformData);
        updateField(source.getRepoSlug(), target::setRepoSlug);
    }


    /**
	 * Saves multiple commits in batch with upsert logic. If a commit with the same
	 * toolConfigId and sha exists, it will be updated. Otherwise, a new commit will
	 * be created.
	 *
	 * @param commitDetails
	 *            the list of commits to save
	 * @throws DataProcessingException
	 *             if batch saving fails
	 */
	public void saveCommits(List<ScmCommits> commitDetails) throws DataProcessingException {
		try {
			log.debug("Batch saving {} commits with upsert logic", commitDetails.size());

			LocalDateTime now = LocalDateTime.now();
			List<ScmCommits> savedCommitDetails = new ArrayList<>();

			for (ScmCommits commitDetail : commitDetails) {
				// Find existing commit by toolConfigId and sha
				Optional<ScmCommits> existingCommit = commitRepository
						.findByProcessorItemIdAndSha(commitDetail.getProcessorItemId(), commitDetail.getSha());

				if (existingCommit.isPresent()) {
					// Update existing commit
					ScmCommits existing = existingCommit.get();
					updateCommitFields(existing, commitDetail);
					existing.setUpdatedAt(now);
					savedCommitDetails.add(commitRepository.save(existing));
					log.debug("Updated existing commit: {} for toolConfigId: {}", commitDetail.getSha(),
							commitDetail.getProcessorItemId());
				} else {
					// Create new commit
					if (commitDetail.getCreatedAt() == null) {
						commitDetail.setCreatedAt(now);
					}
					commitDetail.setUpdatedAt(now);
					savedCommitDetails.add(commitRepository.save(commitDetail));
					log.debug("Created new commit: {} for toolConfigId: {}", commitDetail.getSha(),
							commitDetail.getProcessorItemId());
				}
			}

			log.info("Successfully processed {} commits ({} updated, {} created)", commitDetails.size(),
					savedCommitDetails.size() - commitDetails.stream().mapToInt(c -> c.getId() == null ? 1 : 0).sum(),
					commitDetails.stream().mapToInt(c -> c.getId() == null ? 1 : 0).sum());

		} catch (Exception e) {
			log.error("Error batch saving commits: {}", e.getMessage(), e);
			throw new DataProcessingException("Failed to batch save commits", e);
		}
	}

    /**
     * Updates merge request fields from source to target merge request.
     */
    private void updateMergeRequestFields(ScmMergeRequests target, ScmMergeRequests source) {
        updateBasicFields(target, source);
        updateUserFields(target, source);
        updateDateFields(target, source);
        updateMetricsFields(target, source);
        updateMetadataFields(target, source);
        updateStateFields(target, source);
    }

    private void updateBasicFields(ScmMergeRequests target, ScmMergeRequests source) {
        updateField(source.getTitle(), target::setTitle);
        updateField(source.getSummary(), target::setSummary);
        updateField(source.getState(), target::setState);
        updateField(source.getFromBranch(), target::setFromBranch);
        updateField(source.getToBranch(), target::setToBranch);
    }

    private void updateUserFields(ScmMergeRequests target, ScmMergeRequests source) {
        updateField(source.getAuthorId(), target::setAuthorId);
        updateField(source.getAuthorUserId(), target::setAuthorUserId);
        updateField(source.getAssigneeUserIds(), target::setAssigneeUserIds);
        updateField(source.getReviewerIds(), target::setReviewerIds);
        updateField(source.getReviewers(), target::setReviewers);
        updateField(source.getReviewerUsers(), target::setReviewerUsers);
        updateField(source.getReviewerUserIds(), target::setReviewerUserIds);
    }

    private void updateDateFields(ScmMergeRequests target, ScmMergeRequests source) {
        updateField(source.getMergedAt(), target::setMergedAt);
        updateField(source.getClosedDate(), target::setClosedDate);
        updateField(source.getCreatedDate(), target::setCreatedDate);
        updateField(source.getUpdatedOn(), target::setUpdatedOn);
        updateField(source.getPickedForReviewOn(), target::setPickedForReviewOn);
        updateField(source.getFirstCommitDate(), target::setFirstCommitDate);
    }

    private void updateMetricsFields(ScmMergeRequests target, ScmMergeRequests source) {
        updateField(source.getLinesChanged(), target::setLinesChanged);
        updateField(source.getCommitCount(), target::setCommitCount);
        updateField(source.getFilesChanged(), target::setFilesChanged);
        updateField(source.getAddedLines(), target::setAddedLines);
        updateField(source.getRemovedLines(), target::setRemovedLines);
        updateField(source.getCommentCount(), target::setCommentCount);
    }

    private void updateMetadataFields(ScmMergeRequests target, ScmMergeRequests source) {
        updateField(source.getMergeCommitSha(), target::setMergeCommitSha);
        updateField(source.getCommitShas(), target::setCommitShas);
        updateField(source.getLabels(), target::setLabels);
        updateField(source.getMilestone(), target::setMilestone);
        updateField(source.getIsDraft(), target::setIsDraft);
        updateField(source.getHasConflicts(), target::setHasConflicts);
        updateField(source.getIsMergeable(), target::setIsMergeable);
        updateField(source.getMergeRequestUrl(), target::setMergeRequestUrl);
        updateField(source.getPlatformData(), target::setPlatformData);
        updateField(source.getRepoSlug(), target::setRepoSlug);
    }

    private void updateStateFields(ScmMergeRequests target, ScmMergeRequests source) {
        if (source.getState() != null) {
            if (source.getState().equalsIgnoreCase(ScmMergeRequests.MergeRequestState.MERGED.name())) {
                target.setClosed(true);
            } else {
                target.setOpen(true);
            }
        }
    }

    private <T> void updateField(T value, java.util.function.Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }


    /**
	 * Saves multiple merge requests in batch with upsert logic. If a merge request
	 * with the same toolConfigId and externalId exists, it will be updated.
	 * Otherwise, a new merge request will be created.
	 *
	 * @param mergeRequests
	 *            the list of merge requests to save
	 * @throws DataProcessingException
	 *             if batch saving fails
	 */
	public void saveMergeRequests(List<ScmMergeRequests> mergeRequests) throws DataProcessingException {
		try {
			log.debug("Batch saving {} merge requests with upsert logic", mergeRequests.size());

			List<ScmMergeRequests> savedMergeRequests = new ArrayList<>();

			for (ScmMergeRequests mergeRequest : mergeRequests) {
				// Find existing merge request by toolConfigId and externalId
				Optional<ScmMergeRequests> existingMR = mergeRequestRepository.findByProcessorItemIdAndExternalId(
						mergeRequest.getProcessorItemId(), mergeRequest.getExternalId());

				if (existingMR.isPresent()) {
					// Update existing merge request
					ScmMergeRequests existing = existingMR.get();
					updateMergeRequestFields(existing, mergeRequest);
					savedMergeRequests.add(mergeRequestRepository.save(existing));
					log.debug("Updated existing merge request: {} for toolConfigId: {}", mergeRequest.getExternalId(),
							mergeRequest.getProcessorItemId());
				} else {
					savedMergeRequests.add(mergeRequestRepository.save(mergeRequest));
					log.debug("Created new merge request: {} for toolConfigId: {}", mergeRequest.getExternalId(),
							mergeRequest.getProcessorItemId());
				}
			}

			log.info("Successfully processed {} merge requests ({} updated, {} created)", mergeRequests.size(),
					savedMergeRequests.size() - mergeRequests.stream().mapToInt(mr -> mr.getId() == null ? 1 : 0).sum(),
					mergeRequests.stream().mapToInt(mr -> mr.getId() == null ? 1 : 0).sum());

		} catch (Exception e) {
			log.error("Error batch saving merge requests: {}", e.getMessage(), e);
			throw new DataProcessingException("Failed to batch save merge requests", e);
		}
	}

	/**
	 * Finds merge requests by tool configuration ID and state.
	 *
	 * @param toolConfigId
	 *            the tool configuration ID
	 * @param state
	 *            the merge request state
	 * @param pageable
	 *            the pagination information
	 * @return page of merge requests with the specified state
	 */
	@Transactional(readOnly = true)
	public Page<ScmMergeRequests> findMergeRequestsByToolConfigIdAndState(ObjectId toolConfigId,
			ScmMergeRequests.MergeRequestState state, Pageable pageable) {
		return mergeRequestRepository.findByProcessorItemIdAndState(toolConfigId, state, pageable);
	}
}