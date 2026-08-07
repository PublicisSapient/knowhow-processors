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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RemoteRepositoryException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.dto.ScanResult;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.core.PersistenceService;
import com.publicissapient.knowhow.processor.scm.service.core.fetcher.CommitFetcher;
import com.publicissapient.knowhow.processor.scm.service.core.fetcher.MergeRequestFetcher;
import com.publicissapient.knowhow.processor.scm.service.core.processor.DataReferenceUpdater;
import com.publicissapient.knowhow.processor.scm.service.core.processor.UserProcessor;
import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import com.publicissapient.kpidashboard.common.model.scm.User;

import lombok.extern.slf4j.Slf4j;

/**
 * Executes scan commands by orchestrating various components. Implements the Command pattern
 * executor.
 */
@Component
@Slf4j
public class ScanCommandExecutor {

	private final PersistenceService persistenceService;
	private final CommitFetcher commitFetcher;
	private final MergeRequestFetcher mergeRequestFetcher;
	private final UserProcessor userProcessor;
	private final DataReferenceUpdater dataReferenceUpdater;

	@Autowired
	public ScanCommandExecutor(
			PersistenceService persistenceService,
			CommitFetcher commitFetcher,
			MergeRequestFetcher mergeRequestFetcher,
			UserProcessor userProcessor,
			DataReferenceUpdater dataReferenceUpdater) {
		this.persistenceService = persistenceService;
		this.commitFetcher = commitFetcher;
		this.mergeRequestFetcher = mergeRequestFetcher;
		this.userProcessor = userProcessor;
		this.dataReferenceUpdater = dataReferenceUpdater;
	}

	/**
	 * Executes the scan command.
	 *
	 * @param command the scan command to execute
	 * @return scan results
	 * @throws DataProcessingException if scanning fails
	 */
	public ScanResult execute(ScanCommand command) throws DataProcessingException {
		ScanRequest scanRequest = command.getScanRequest();
		long startTime = System.currentTimeMillis();

		ScanResult.ScanResultBuilder resultBuilder =
				ScanResult.builder()
						.repositoryUrl(scanRequest.getRepositoryUrl())
						.repositoryName(scanRequest.getRepositoryName())
						.startTime(System.currentTimeMillis());

		// Fetch commits — only access failures (TransportException, RemoteRepositoryException)
		// and bad commit object (IncorrectObjectTypeException) are suppressed so MR fetch still runs;
		// all other errors propagate immediately
		List<ScmCommits> commitDetails = List.of();
		try {
			commitDetails = commitFetcher.fetchCommits(scanRequest);
			resultBuilder.commitsFound(commitDetails.size());
		} catch (DataProcessingException e) {
			if (!isKnownSkippableException(e)) {
				throw e;
			}
			log.error(
					"Commit fetch failed for {} ({}), continuing with MR fetch: {}",
					scanRequest.getRepositoryName(),
					scanRequest.getRepositoryUrl(),
					e.getMessage(),
					e);
			resultBuilder.commitsFound(0);
		} catch (Exception e) {
			log.error(
					"Unexpected error during commit fetch for {} ({})",
					scanRequest.getRepositoryName(),
					scanRequest.getRepositoryUrl(),
					e);
			throw new DataProcessingException("Repository scan failed", e);
		}

		// Fetch merge requests — only HTTP 4xx access failures (PlatformApiException wrapping
		// WebClientResponseException) are suppressed; all other errors propagate immediately
		List<ScmMergeRequests> mergeRequests = List.of();
		try {
			mergeRequests = mergeRequestFetcher.fetchMergeRequests(scanRequest);
			resultBuilder.mergeRequestsFound(mergeRequests.size());
		} catch (PlatformApiException e) {
			if (!isKnownSkippableException(e)) {
				throw e;
			}
			log.error(
					"MR fetch failed for {} ({}), skipping: {}",
					scanRequest.getRepositoryName(),
					scanRequest.getRepositoryUrl(),
					e.getMessage(),
					e);
			resultBuilder.mergeRequestsFound(0);
		} catch (Exception e) {
			log.error(
					"Unexpected error during MR fetch for {} ({})",
					scanRequest.getRepositoryName(),
					scanRequest.getRepositoryUrl(),
					e);
			throw new DataProcessingException("Repository scan failed", e);
		}

		try {
			// Process users
			UserProcessor.UserProcessingResult userResult =
					userProcessor.processUsers(commitDetails, mergeRequests, scanRequest);

			Map<String, User> userMap = userResult.getUserMap();
			Set<User> allUsers = userResult.getAllUsers();

			// Update references
			dataReferenceUpdater.updateCommitsWithUserReferences(
					commitDetails, userMap, scanRequest.getRepositoryName());
			dataReferenceUpdater.updateMergeRequestsWithUserReferences(
					mergeRequests, userMap, scanRequest.getRepositoryName());

			// Persist data
			persistData(commitDetails, mergeRequests, scanRequest);

			long duration = System.currentTimeMillis() - startTime;
			return resultBuilder
					.endTime(System.currentTimeMillis())
					.durationMs(duration)
					.success(true)
					.usersFound(allUsers.size())
					.build();

		} catch (Exception e) {
			log.error(
					"Failed to scan repository: {} ({})",
					scanRequest.getRepositoryName(),
					scanRequest.getRepositoryUrl(),
					e);
			throw new DataProcessingException("Repository scan failed", e);
		}
	}

	/**
	 * Returns true only for exceptions that represent a known, repo-specific failure that should be
	 * skipped rather than aborting the overall scan. Walks the full cause chain looking for:
	 *
	 * <ul>
	 *   <li>{@link TransportException} / {@link RemoteRepositoryException} — credential or access
	 *       failure
	 *   <li>{@link IncorrectObjectTypeException} — branch tip points to a non-commit object
	 *   <li>{@link WebClientResponseException} with a 4xx status — REST API access/permission denial
	 * </ul>
	 *
	 * All other exceptions (network timeouts, JSON parse errors, unexpected runtime errors) return
	 * false and will propagate to fail the tool connection.
	 */
	private static boolean isKnownSkippableException(Throwable t) {
		Throwable current = t;
		while (current != null) {
			if (current instanceof TransportException
					|| current instanceof RemoteRepositoryException
					|| current instanceof IncorrectObjectTypeException) {
				return true;
			}
			if (current instanceof WebClientResponseException wce
					&& wce.getStatusCode().is4xxClientError()) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private void persistData(
			List<ScmCommits> commitDetails,
			List<ScmMergeRequests> mergeRequests,
			ScanRequest scanRequest) {
		// Upsert scm_repository entry keyed by clone URL — ensures IS/application and HEL/application
		// get separate entries even though both repos share the same repository name
		List<ScmBranch> branchList =
				scanRequest.getBranchName() != null
						? List.of(ScmBranch.builder().name(scanRequest.getBranchName()).isActive(true).build())
						: List.of();
		ScmRepos scmRepo =
				ScmRepos.builder()
						.url(scanRequest.getRepositoryUrl())
						.repositoryName(scanRequest.getRepositoryName())
						.connectionId(scanRequest.getConnectionId())
						.lastUpdated(System.currentTimeMillis())
						.branchList(branchList)
						.build();
		persistenceService.saveRepositoryData(List.of(scmRepo));
		log.info(
				"Upserted scm_repository entry for: {} ({})",
				scanRequest.getRepositoryName(),
				scanRequest.getRepositoryUrl());

		// Persist commits
		if (!commitDetails.isEmpty()) {
			commitDetails.forEach(commit -> commit.setProcessorItemId(scanRequest.getToolConfigId()));
			persistenceService.saveCommits(commitDetails);
			log.info(
					"Persisted {} commits for repository: {} ({})",
					commitDetails.size(),
					scanRequest.getRepositoryName(),
					scanRequest.getRepositoryUrl());
		}

		// Persist merge requests
		if (!mergeRequests.isEmpty()) {
			persistenceService.saveMergeRequests(mergeRequests);
			log.info(
					"Persisted {} merge requests for repository: {} ({})",
					mergeRequests.size(),
					scanRequest.getRepositoryName(),
					scanRequest.getRepositoryUrl());
		}
	}
}
