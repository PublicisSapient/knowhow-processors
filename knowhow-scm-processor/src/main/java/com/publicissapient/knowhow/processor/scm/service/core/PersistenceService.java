package com.publicissapient.knowhow.processor.scm.service.core;

import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;

import com.publicissapient.kpidashboard.common.repository.scm.ScmCommitsRepository;
import com.publicissapient.kpidashboard.common.repository.scm.ScmMergeRequestsRepository;
import com.publicissapient.kpidashboard.common.repository.scm.ScmUserRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Service for persisting Git metadata to the database.
 * 
 * This service handles the persistence operations for users, commits, and merge requests.
 * It provides methods for saving, updating, and querying Git metadata with proper
 * transaction management and error handling.
 * 
 * Implements the Single Responsibility Principle by focusing solely on data persistence.
 */
@Service
@Transactional
public class PersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceService.class);

    private final ScmUserRepository userRepository;
    private final ScmCommitsRepository commitRepository;
    private final ScmMergeRequestsRepository mergeRequestRepository;

    @Autowired
    public PersistenceService(ScmUserRepository userRepository,
                              ScmCommitsRepository commitRepository,
                              ScmMergeRequestsRepository mergeRequestRepository) {
        this.userRepository = userRepository;
        this.commitRepository = commitRepository;
        this.mergeRequestRepository = mergeRequestRepository;
    }

    // User operations
    
    /**
     * Saves or updates a user with repository-specific unique constraint.
     * 
     * @param user the user to save
     * @return the saved user
     * @throws DataProcessingException if saving fails
     */
    public User saveUser(User user) throws DataProcessingException {
        try {
            logger.debug("Saving user: {} for repository: {}", user.getUsername(), user.getRepositoryName());

            // Check if user already exists by repository name and username
            Optional<User> existingUser = userRepository.findByRepositoryNameAndUsername(
                user.getRepositoryName(), user.getUsername());

            if (existingUser.isPresent()) {
                // Update existing user
                User existing = existingUser.get();
                updateUserFields(existing, user);
                existing.setUpdatedAt(LocalDateTime.now());
                return userRepository.save(existing);
            } else if(user.getEmail() != null) {
                // Check by email as fallback
                Optional<User> existingByEmail = userRepository.findByRepositoryNameAndEmail(
                    user.getRepositoryName(), user.getEmail());

                if (existingByEmail.isPresent()) {
                    // Update existing user found by email
                    User existing = existingByEmail.get();
                    updateUserFields(existing, user);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return userRepository.save(existing);
                }
            }
            // Save new user
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user);

        } catch (DuplicateKeyException e) {
            logger.warn("Duplicate key exception for user {}, attempting to find and update existing user", 
                user.getUsername());
            // Try to find existing user and update
            Optional<User> existingUser = userRepository.findByRepositoryNameAndUsername(
                user.getRepositoryName(), user.getUsername());
            if (existingUser.isPresent()) {
                User existing = existingUser.get();
                updateUserFields(existing, user);
                existing.setUpdatedAt(LocalDateTime.now());
                return userRepository.save(existing);
            }
            throw new DataProcessingException("Failed to save user due to duplicate key", e);
        } catch (Exception e) {
            logger.error("Error saving user {}: {}", user.getUsername(), e.getMessage(), e);
            throw new DataProcessingException("Failed to save user", e);
        }
    }

    /**
     * Updates user fields from source to target user.
     */
    private void updateUserFields(User target, User source) {
        if (source.getDisplayName() != null) target.setDisplayName(source.getDisplayName());
        if (source.getEmail() != null) target.setEmail(source.getEmail());
        if (source.getAvatarUrl() != null) target.setAvatarUrl(source.getAvatarUrl());
        if (source.getProfileUrl() != null) target.setProfileUrl(source.getProfileUrl());
        if (source.getCompany() != null) target.setCompany(source.getCompany());
        if (source.getLocation() != null) target.setLocation(source.getLocation());
        if (source.getBio() != null) target.setBio(source.getBio());
        if (source.getBlogUrl() != null) target.setBlogUrl(source.getBlogUrl());
        if (source.getPublicRepos() != null) target.setPublicRepos(source.getPublicRepos());
        if (source.getFollowers() != null) target.setFollowers(source.getFollowers());
        if (source.getFollowing() != null) target.setFollowing(source.getFollowing());
        if (source.getActive() != null) target.setActive(source.getActive());
        if (source.getBot() != null) target.setBot(source.getBot());
        if (source.getExternalId() != null) target.setExternalId(source.getExternalId());
        if (source.getRepositories() != null) target.setRepositories(source.getRepositories());
        if (source.getPlatformData() != null) target.setPlatformData(source.getPlatformData());
        if (source.getLastSeenAt() != null) target.setLastSeenAt(source.getLastSeenAt());
    }

    /**
     * Finds or creates a user by repository name and username/email.
     * 
     * @param repositoryName the repository name
     * @param username the username
     * @param email the email
     * @param displayName the display name
     * @return the found or created user
     */
    public User findOrCreateUser(String repositoryName, String username, String email, String displayName) {
        // Try to find by username first
        Optional<User> existingUser = userRepository.findByRepositoryNameAndUsername(repositoryName, username);
        if (existingUser.isPresent()) {
            return existingUser.get();
        }

        // Try to find by email
        if (email != null) {
            existingUser = userRepository.findByRepositoryNameAndEmail(repositoryName, email);
            if (existingUser.isPresent()) {
                return existingUser.get();
            }
        }

        // Create new user
        User newUser = User.builder()
            .repositoryName(repositoryName)
            .username(username)
            .email(email)
            .displayName(displayName)
            .active(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        try {
            return userRepository.save(newUser);
        } catch (DuplicateKeyException e) {
            // Race condition - try to find again
            existingUser = userRepository.findByRepositoryNameAndUsername(repositoryName, username);
            return existingUser.orElseThrow(() -> new DataProcessingException("Failed to create user", e));
        }
    }

    /**
     * Saves multiple users in batch.
     * 
     * @param users the list of users to save
     * @return the list of saved users
     * @throws DataProcessingException if batch saving fails
     */
    public List<User> saveUsers(List<User> users) throws DataProcessingException {
        try {
            logger.debug("Batch saving {} users", users.size());
            
            LocalDateTime now = LocalDateTime.now();
            users.forEach(user -> {
                if (user.getCreatedAt() == null) {
                    user.setCreatedAt(now);
                }
                user.setUpdatedAt(now);
            });
            
            return userRepository.saveAll(users);
        } catch (Exception e) {
            logger.error("Error batch saving users: {}", e.getMessage(), e);
            throw new DataProcessingException("Failed to batch save users", e);
        }
    }

    /**
     * Finds a user by email.
     * 
     * @param email the user email
     * @return the user if found
     */
    @Transactional(readOnly = true)
    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Finds a user by repository name and email.
     * 
     * @param repositoryName the repository name
     * @param email the user email
     * @return the user if found
     */
    @Transactional(readOnly = true)
    public Optional<User> findUserByRepositoryAndEmail(String repositoryName, String email) {
        return userRepository.findByRepositoryNameAndEmail(repositoryName, email);
    }

    // Commit operations
    
    /**
     * Saves or updates a commit with tool configuration specific unique constraint.
     *
     * @param commitDetails the commit to save
     * @return the saved commit
     * @throws DataProcessingException if saving fails
     */
    public ScmCommits saveCommit(ScmCommits commitDetails) throws DataProcessingException {
        try {
            logger.debug("Saving commit: {} for toolConfigId: {}", commitDetails.getSha(), commitDetails.getProcessorItemId());

            // Check if commit already exists by toolConfigId and SHA
            Optional<ScmCommits> existingCommit = commitRepository.findByProcessorItemIdAndSha(
                commitDetails.getProcessorItemId(), commitDetails.getSha());

            if (existingCommit.isPresent()) {
                // Update existing commit
                ScmCommits existing = existingCommit.get();
                updateCommitFields(existing, commitDetails);
                existing.setUpdatedAt(LocalDateTime.now());
                return commitRepository.save(existing);
            } else {
                // Save new commit
                commitDetails.setCreatedAt(LocalDateTime.now());
                commitDetails.setUpdatedAt(LocalDateTime.now());
                return commitRepository.save(commitDetails);
            }
        } catch (DuplicateKeyException e) {
            logger.warn("Duplicate key exception for commit {}, attempting to find and update existing commit",
                commitDetails.getSha());
            // Try to find existing commit and update
            Optional<ScmCommits> existingCommit = commitRepository.findByProcessorItemIdAndSha(
                commitDetails.getProcessorItemId(), commitDetails.getSha());
            if (existingCommit.isPresent()) {
                ScmCommits existing = existingCommit.get();
                updateCommitFields(existing, commitDetails);
                existing.setUpdatedAt(LocalDateTime.now());
                return commitRepository.save(existing);
            }
            throw new DataProcessingException("Failed to save commit due to duplicate key", e);
        } catch (Exception e) {
            logger.error("Error saving commit {}: {}", commitDetails.getSha(), e.getMessage(), e);
            throw new DataProcessingException("Failed to save commit", e);
        }
    }

    /**
     * Updates commit fields from source to target commit.
     */
    private void updateCommitFields(ScmCommits target, ScmCommits source) {
        if (source.getCommitMessage() != null) target.setCommitMessage(source.getCommitMessage());
        if (source.getCommitAuthorId() != null) target.setCommitAuthorId(source.getCommitAuthorId());
        if (source.getCommitAuthor() != null) target.setCommitAuthor(source.getCommitAuthor());
        if (source.getCommitterId() != null) target.setCommitterId(source.getCommitterId());
        if (source.getCommitter() != null) target.setCommitter(source.getCommitter());
        if (source.getCommitTimestamp() != null) target.setCommitTimestamp(source.getCommitTimestamp());
        if (source.getAuthorName() != null) target.setAuthorName(source.getAuthorName());
        if (source.getAuthorEmail() != null) target.setAuthorEmail(source.getAuthorEmail());
        if (source.getCommitterName() != null) target.setCommitterName(source.getCommitterName());
        if (source.getCommitterEmail() != null) target.setCommitterEmail(source.getCommitterEmail());
        if (source.getAddedLines() != null) target.setAddedLines(source.getAddedLines());
        if (source.getRemovedLines() != null) target.setRemovedLines(source.getRemovedLines());
        if (source.getChangedLines() != null) target.setChangedLines(source.getChangedLines());
        if (source.getFileChanges() != null) target.setFileChanges(source.getFileChanges());
        if (source.getBranchName() != null) target.setBranchName(source.getBranchName());
        if (source.getTargetBranch() != null) target.setTargetBranch(source.getTargetBranch());
        if (source.getRepositoryName() != null) target.setRepositoryName(source.getRepositoryName());
        if (source.getParentShas() != null) target.setParentShas(source.getParentShas());
        if (source.getIsMergeCommit() != null) target.setIsMergeCommit(source.getIsMergeCommit());
        if (source.getFilesChanged() != null) target.setFilesChanged(source.getFilesChanged());
        if (source.getCommitUrl() != null) target.setCommitUrl(source.getCommitUrl());
        if (source.getTags() != null) target.setTags(source.getTags());
        if (source.getPlatformData() != null) target.setPlatformData(source.getPlatformData());
        if (source.getRepoSlug() != null) target.setRepoSlug(source.getRepoSlug());
    }

    /**
     * Saves multiple commits in batch.
     * 
     * @param commits the list of commits to save
     * @return the list of saved commits
     * @throws DataProcessingException if batch saving fails
     */
    /**
     * Saves multiple commits in batch with upsert logic.
     * If a commit with the same toolConfigId and sha exists, it will be updated.
     * Otherwise, a new commit will be created.
     *
     * @param commitDetails the list of commits to save
     * @return the list of saved commits
     * @throws DataProcessingException if batch saving fails
     */
    public List<ScmCommits> saveCommits(List<ScmCommits> commitDetails) throws DataProcessingException {
        try {
            logger.debug("Batch saving {} commits with upsert logic", commitDetails.size());

            LocalDateTime now = LocalDateTime.now();
            List<ScmCommits> savedCommitDetails = new ArrayList<>();

            for (ScmCommits commitDetail : commitDetails) {
                // Find existing commit by toolConfigId and sha
                Optional<ScmCommits> existingCommit = commitRepository.findByProcessorItemIdAndSha(
                    commitDetail.getProcessorItemId(), commitDetail.getSha());

                if (existingCommit.isPresent()) {
                    // Update existing commit
                    ScmCommits existing = existingCommit.get();
                    updateCommitFields(existing, commitDetail);
                    existing.setUpdatedAt(now);
                    savedCommitDetails.add(commitRepository.save(existing));
                    logger.debug("Updated existing commit: {} for toolConfigId: {}",
                        commitDetail.getSha(), commitDetail.getProcessorItemId());
                } else {
                    // Create new commit
                    if (commitDetail.getCreatedAt() == null) {
                        commitDetail.setCreatedAt(now);
                    }
                    commitDetail.setUpdatedAt(now);
                    savedCommitDetails.add(commitRepository.save(commitDetail));
                    logger.debug("Created new commit: {} for toolConfigId: {}",
                        commitDetail.getSha(), commitDetail.getProcessorItemId());
                }
            }

            logger.info("Successfully processed {} commits ({} updated, {} created)",
                commitDetails.size(),
                savedCommitDetails.size() - commitDetails.stream().mapToInt(c -> c.getId() == null ? 1 : 0).sum(),
                commitDetails.stream().mapToInt(c -> c.getId() == null ? 1 : 0).sum());

            return savedCommitDetails;
        } catch (Exception e) {
            logger.error("Error batch saving commits: {}", e.getMessage(), e);
            throw new DataProcessingException("Failed to batch save commits", e);
        }
    }

    /**
     * Finds commits by tool configuration ID.
     * 
     * @param toolConfigId the tool configuration ID
     * @param pageable the pagination information
     * @return page of commits
     */
    @Transactional(readOnly = true)
    public Page<ScmCommits> findCommitsByToolConfigId(ObjectId toolConfigId, Pageable pageable) {
        return commitRepository.findByProcessorItemId(toolConfigId, pageable);
    }

    /**
     * Finds commits by repository name.
     * 
     * @param repositoryName the repository name
     * @param pageable the pagination information
     * @return page of commits
     */
    @Transactional(readOnly = true)
    public Page<ScmCommits> findCommitsByRepositoryName(String repositoryName, Pageable pageable) {
        return commitRepository.findByRepositoryName(repositoryName, pageable);
    }

    // Merge Request operations

    /**
     * Saves or updates a merge request with repository-specific unique constraint.
     *
     * @param mergeRequests the merge request to save
     * @return the saved merge request
     * @throws DataProcessingException if saving fails
     */
    public ScmMergeRequests saveMergeRequest(ScmMergeRequests mergeRequests) throws DataProcessingException {
        try {
            logger.debug("Saving merge request: {} for toolConfigId: {}",
                mergeRequests.getExternalId(), mergeRequests.getProcessorItemId());

            // Check if merge request already exists by toolConfigId and external ID
            Optional<ScmMergeRequests> existingMR = mergeRequestRepository
                    .findByProcessorItemIdAndExternalId(mergeRequests.getProcessorItemId(), mergeRequests.getExternalId());

            if (existingMR.isPresent()) {
                // Update existing merge request
                ScmMergeRequests existing = existingMR.get();
                updateMergeRequestFields(existing, mergeRequests);
                existing.setUpdatedDate(LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
                return mergeRequestRepository.save(existing);
            } else {
                // Save new merge request
                mergeRequests.setUpdatedDate(LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
                return mergeRequestRepository.save(mergeRequests);
            }
        } catch (DuplicateKeyException e) {
            logger.warn("Duplicate key exception for merge request {}, attempting to find and update existing merge request",
                mergeRequests.getExternalId());
            // Try to find existing merge request and update
            Optional<ScmMergeRequests> existingMR = mergeRequestRepository
                    .findByProcessorItemIdAndExternalId(mergeRequests.getProcessorItemId(), mergeRequests.getExternalId());
            if (existingMR.isPresent()) {
                ScmMergeRequests existing = existingMR.get();
                updateMergeRequestFields(existing, mergeRequests);
                existing.setUpdatedDate(LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
                return mergeRequestRepository.save(existing);
            }
            throw new DataProcessingException("Failed to save merge request due to duplicate key", e);
        } catch (Exception e) {
            logger.error("Error saving merge request {}: {}", mergeRequests.getExternalId(), e.getMessage(), e);
            throw new DataProcessingException("Failed to save merge request", e);
        }
    }

    /**
     * Updates merge request fields from source to target merge request.
     */
    private void updateMergeRequestFields(ScmMergeRequests target, ScmMergeRequests source) {
        if (source.getTitle() != null) target.setTitle(source.getTitle());
        if (source.getSummary() != null) target.setSummary(source.getSummary());
        if (source.getState() != null) target.setState(source.getState());
        if (source.getFromBranch() != null) target.setFromBranch(source.getFromBranch());
        if (source.getToBranch() != null) target.setToBranch(source.getToBranch());
        if (source.getAuthorId() != null) target.setAuthorId(source.getAuthorId());
        if (source.getAuthorUserId() != null) target.setAuthorUserId(source.getAuthorUserId());
        if (source.getAssigneeUserIds() != null) target.setAssigneeUserIds(source.getAssigneeUserIds());
        if (source.getReviewerIds() != null) target.setReviewerIds(source.getReviewerIds());
        if (source.getReviewers() != null) target.setReviewers(source.getReviewers());
        if (source.getReviewerUsers() != null) target.setReviewerUsers(source.getReviewerUsers());
        if (source.getReviewerUserIds() != null) target.setReviewerUserIds(source.getReviewerUserIds());
        if (source.getMergedAt() != null) target.setMergedAt(source.getMergedAt());
        if (source.getClosedDate() != null) target.setClosedDate(source.getClosedDate());
        if (source.getCreatedDate() != null) target.setCreatedDate(source.getCreatedDate());
        if (source.getUpdatedOn() != null) target.setUpdatedOn(source.getUpdatedOn());
        if (source.getLinesChanged() != null) target.setLinesChanged(source.getLinesChanged());
        if (source.getPickedForReviewOn() != null) target.setPickedForReviewOn(source.getPickedForReviewOn());
        if (source.getFirstCommitDate() != null) target.setFirstCommitDate(source.getFirstCommitDate());
        if (source.getMergeCommitSha() != null) target.setMergeCommitSha(source.getMergeCommitSha());
        if (source.getCommitShas() != null) target.setCommitShas(source.getCommitShas());
        if (source.getCommitCount() != null) target.setCommitCount(source.getCommitCount());
        if (source.getFilesChanged() != null) target.setFilesChanged(source.getFilesChanged());
        if (source.getAddedLines() != null) target.setAddedLines(source.getAddedLines());
        if (source.getRemovedLines() != null) target.setRemovedLines(source.getRemovedLines());
        if (source.getLabels() != null) target.setLabels(source.getLabels());
        if (source.getMilestone() != null) target.setMilestone(source.getMilestone());
        if (source.getIsDraft() != null) target.setIsDraft(source.getIsDraft());
        if (source.getHasConflicts() != null) target.setHasConflicts(source.getHasConflicts());
        if (source.getIsMergeable() != null) target.setIsMergeable(source.getIsMergeable());
        if (source.getMergeRequestUrl() != null) target.setMergeRequestUrl(source.getMergeRequestUrl());
        if (source.getPlatformData() != null) target.setPlatformData(source.getPlatformData());
        if (source.getCommentCount() != null) target.setCommentCount(source.getCommentCount());
        if (source.getRepoSlug() != null) target.setRepoSlug(source.getRepoSlug());
        if (source.getState().equalsIgnoreCase(ScmMergeRequests.MergeRequestState.MERGED.name()))
            target.setClosed(true);
        else
            target.setOpen(true);
    }

    /**
     * Saves multiple merge requests in batch.
     * 
     * @param mergeRequests the list of merge requests to save
     * @return the list of saved merge requests
     * @throws DataProcessingException if batch saving fails
     */
    /**
     * Saves multiple merge requests in batch with upsert logic.
     * If a merge request with the same toolConfigId and externalId exists, it will be updated.
     * Otherwise, a new merge request will be created.
     *
     * @param mergeRequests the list of merge requests to save
     * @return the list of saved merge requests
     * @throws DataProcessingException if batch saving fails
     */
    public List<ScmMergeRequests> saveMergeRequests(List<ScmMergeRequests> mergeRequests) throws DataProcessingException {
        try {
            logger.debug("Batch saving {} merge requests with upsert logic", mergeRequests.size());

            LocalDateTime now = LocalDateTime.now();
            List<ScmMergeRequests> savedMergeRequests = new ArrayList<>();

            for (ScmMergeRequests mergeRequest : mergeRequests) {
                // Find existing merge request by toolConfigId and externalId
                Optional<ScmMergeRequests> existingMR = mergeRequestRepository.findByProcessorItemIdAndExternalId(
                    mergeRequest.getProcessorItemId(), mergeRequest.getExternalId());

                if (existingMR.isPresent()) {
                    // Update existing merge request
                    ScmMergeRequests existing = existingMR.get();
                    updateMergeRequestFields(existing, mergeRequest);
                    existing.setUpdatedDate(now.toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
                    savedMergeRequests.add(mergeRequestRepository.save(existing));
                    logger.debug("Updated existing merge request: {} for toolConfigId: {}",
                        mergeRequest.getExternalId(), mergeRequest.getProcessorItemId());
                } else {
                    mergeRequest.setUpdatedDate(now.toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
                    savedMergeRequests.add(mergeRequestRepository.save(mergeRequest));
                    logger.debug("Created new merge request: {} for toolConfigId: {}",
                        mergeRequest.getExternalId(), mergeRequest.getProcessorItemId());
                }
            }

            logger.info("Successfully processed {} merge requests ({} updated, {} created)",
                mergeRequests.size(),
                savedMergeRequests.size() - mergeRequests.stream().mapToInt(mr -> mr.getId() == null ? 1 : 0).sum(),
                mergeRequests.stream().mapToInt(mr -> mr.getId() == null ? 1 : 0).sum());

            return savedMergeRequests;
        } catch (Exception e) {
            logger.error("Error batch saving merge requests: {}", e.getMessage(), e);
            throw new DataProcessingException("Failed to batch save merge requests", e);
        }
    }

    /**
     * Finds merge requests by repository name.
     *
     * @param repositoryName the repository name
     * @param pageable the pagination information
     * @return page of merge requests
     */
    @Transactional(readOnly = true)
    public Page<ScmMergeRequests> findMergeRequestsByRepositoryName(String repositoryName, Pageable pageable) {
        return mergeRequestRepository.findByRepositoryName(repositoryName, pageable);
    }

    /**
     * Finds merge requests by tool configuration ID and state.
     *
     * @param toolConfigId the tool configuration ID
     * @param state the merge request state
     * @param pageable the pagination information
     * @return page of merge requests with the specified state
     */
    @Transactional(readOnly = true)
    public Page<ScmMergeRequests> findMergeRequestsByToolConfigIdAndState(ObjectId toolConfigId, ScmMergeRequests.MergeRequestState state, Pageable pageable) {
        return mergeRequestRepository.findByProcessorItemIdAndState(toolConfigId, state, pageable);
    }
}