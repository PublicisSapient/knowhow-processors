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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes scan commands by orchestrating various components. Implements the
 * Command pattern executor.
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
	public ScanCommandExecutor(PersistenceService persistenceService, CommitFetcher commitFetcher,
			MergeRequestFetcher mergeRequestFetcher, UserProcessor userProcessor,
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
	 * @param command
	 *            the scan command to execute
	 * @return scan results
	 * @throws DataProcessingException
	 *             if scanning fails
	 */
	public ScanResult execute(ScanCommand command) throws DataProcessingException {
		ScanRequest scanRequest = command.getScanRequest();
		long startTime = System.currentTimeMillis();

		ScanResult.ScanResultBuilder resultBuilder = ScanResult.builder().repositoryUrl(scanRequest.getRepositoryUrl())
				.repositoryName(scanRequest.getRepositoryName()).startTime(System.currentTimeMillis());

		try {
			// Fetch commits
			List<ScmCommits> commitDetails = commitFetcher.fetchCommits(scanRequest);
			resultBuilder.commitsFound(commitDetails.size());

			// Fetch merge requests
			List<ScmMergeRequests> mergeRequests = mergeRequestFetcher.fetchMergeRequests(scanRequest);
			resultBuilder.mergeRequestsFound(mergeRequests.size());

			// Process users
			UserProcessor.UserProcessingResult userResult = userProcessor.processUsers(commitDetails, mergeRequests,
					scanRequest);

			Map<String, User> userMap = userResult.getUserMap();
			Set<User> allUsers = userResult.getAllUsers();

			// Update references
			dataReferenceUpdater.updateCommitsWithUserReferences(commitDetails, userMap,
					scanRequest.getRepositoryName());
			dataReferenceUpdater.updateMergeRequestsWithUserReferences(mergeRequests, userMap,
					scanRequest.getRepositoryName());

			// Persist data
			persistData(commitDetails, mergeRequests, scanRequest);

			long duration = System.currentTimeMillis() - startTime;
			return resultBuilder.endTime(System.currentTimeMillis()).durationMs(duration).success(true)
					.usersFound(allUsers.size()).build();

		} catch (Exception e) {
			log.error("Failed to scan repository: {} ({})", scanRequest.getRepositoryName(),
					scanRequest.getRepositoryUrl(), e);
			throw new DataProcessingException("Repository scan failed", e);
		}
	}

	private void persistData(List<ScmCommits> commitDetails, List<ScmMergeRequests> mergeRequests, ScanRequest scanRequest) {
		// Persist commits
		if (!commitDetails.isEmpty()) {
			commitDetails.forEach(commit -> commit.setProcessorItemId(scanRequest.getToolConfigId()));
			persistenceService.saveCommits(commitDetails);
			log.info("Persisted {} commits for repository: {} ({})", commitDetails.size(),
					scanRequest.getRepositoryName(), scanRequest.getRepositoryUrl());
		}

		// Persist merge requests
		if (!mergeRequests.isEmpty()) {
			persistenceService.saveMergeRequests(mergeRequests);
			log.info("Persisted {} merge requests for repository: {} ({})", mergeRequests.size(),
					scanRequest.getRepositoryName(), scanRequest.getRepositoryUrl());
		}

	}
}