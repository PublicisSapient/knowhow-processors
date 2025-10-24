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

package com.publicissapient.knowhow.processor.scm.service.core.fetcher;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.service.core.PersistenceService;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformMergeRequestService;
import com.publicissapient.knowhow.processor.scm.service.platform.MergeRequestServiceLocator;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser.GitUrlInfo;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.constants.ScmConstants;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Responsible for fetching merge requests with optimized logic. Follows Single
 * Responsibility Principle.
 */
@Component
@Slf4j
public class MergeRequestFetcher {

	private final MergeRequestServiceLocator mergeRequestService;
	private final PersistenceService persistenceService;
	private final GitUrlParser gitUrlParser;

	@Value("${git.scanner.first-scan-from:6}")
	private int firstScanFromMonths;

	@Value("${git.scanner.pagination.max-merge-requests-per-scan:5000}")
	private int maxMergeRequestsPerScan;

	@Autowired
	public MergeRequestFetcher(MergeRequestServiceLocator mergeRequestService, PersistenceService persistenceService,
			GitUrlParser gitUrlParser) {
		this.mergeRequestService = mergeRequestService;
		this.persistenceService = persistenceService;
		this.gitUrlParser = gitUrlParser;
	}

	public List<ScmMergeRequests> fetchMergeRequests(ScanRequest scanRequest) throws PlatformApiException {

		GitUrlInfo urlInfo = gitUrlParser.parseGitUrl(scanRequest.getRepositoryUrl(), scanRequest.getToolType(),
				scanRequest.getUsername(), scanRequest.getRepositoryName());

		String identifier = scanRequest.getToolType() != null ? scanRequest.getToolType()
				: scanRequest.getRepositoryName();

		log.info("Starting optimized merge request fetch for identifier: {}", identifier);

		GitPlatformMergeRequestService platformService = mergeRequestService
				.getMergeRequestService(scanRequest.getToolType());

		// Fetch new merge requests
		List<ScmMergeRequests> newMergeRequests = fetchNewMergeRequests(scanRequest, platformService, urlInfo,
				identifier);
		log.info("Fetched {} new merge requests", newMergeRequests.size());

		// Fetch updates for existing open merge requests
		List<ScmMergeRequests> updatedOpenMergeRequests = fetchUpdatesForOpenMergeRequests(scanRequest, platformService,
				urlInfo, identifier);
		log.info("Fetched {} updated open merge requests", updatedOpenMergeRequests.size());

		// Combine and deduplicate results
		List<ScmMergeRequests> combinedMergeRequests = combineAndDeduplicateMergeRequests(newMergeRequests,
				updatedOpenMergeRequests);
		log.info("Combined total: {} unique merge requests after deduplication", combinedMergeRequests.size());

		return combinedMergeRequests;
	}

	private List<ScmMergeRequests> fetchNewMergeRequests(ScanRequest scanRequest,
			GitPlatformMergeRequestService platformService, GitUrlInfo urlInfo, String identifier)
			throws PlatformApiException {
		LocalDateTime mergeRequestsSince = calculateMergeRequestsSince(scanRequest);
		LocalDateTime mergeRequestsUntil = scanRequest.getUntil();

		log.info("Fetching merge requests updated since: {} for identifier: {}", mergeRequestsSince, identifier);

		String token = formatToken(scanRequest);

		return platformService.fetchMergeRequests(scanRequest.getToolConfigId().toString(), urlInfo,
				scanRequest.getBranchName(), token, mergeRequestsSince, mergeRequestsUntil);
	}

	private List<ScmMergeRequests> fetchUpdatesForOpenMergeRequests(ScanRequest scanRequest,
			GitPlatformMergeRequestService platformService, GitUrlInfo urlInfo, String identifier)
			throws PlatformApiException {

		List<ScmMergeRequests> existingOpenMRs = getExistingOpenMergeRequests(identifier);

		if (existingOpenMRs.isEmpty()) {
			log.debug("No existing open merge requests found for identifier: {}", identifier);
			return List.of();
		}

		log.info("Found {} existing open merge requests to update", existingOpenMRs.size());

		LocalDateTime updatesSince = calculateUpdateWindowStart(existingOpenMRs);

		log.debug("Fetching MR updates since: {} for {} existing open MRs (based on updated date)", updatesSince,
				existingOpenMRs.size());

		String token = formatToken(scanRequest);

		// Fetch merge requests updated since the calculated time to capture state
		// changes
		List<ScmMergeRequests> allRecentMRs = platformService.fetchMergeRequests(
				scanRequest.getToolConfigId().toString(), urlInfo, scanRequest.getBranchName(), token, updatesSince,
				null // No end date limit for updates
		);

		// Filter to only return MRs that correspond to our existing open MRs
		return filterRelevantUpdates(allRecentMRs, existingOpenMRs);
	}

	/**
	 * Gets existing open merge requests from the database for the given identifier.
	 */
	private List<ScmMergeRequests> getExistingOpenMergeRequests(String identifier) {
		try {
			// Use pagination to handle large datasets efficiently
			Pageable pageable = PageRequest.of(0, maxMergeRequestsPerScan);
			Page<ScmMergeRequests> openMRsPage = persistenceService.findMergeRequestsByToolConfigIdAndState(
					new ObjectId(identifier), ScmMergeRequests.MergeRequestState.OPEN, pageable);

			List<ScmMergeRequests> allOpenMRs = new ArrayList<>(openMRsPage.getContent());

			// If there are more pages, fetch them (but limit to reasonable amount)
			int maxPages = 10; // Limit to prevent excessive memory usage
			int currentPage = 1;

			while (openMRsPage.hasNext() && currentPage < maxPages) {
				pageable = PageRequest.of(currentPage, maxMergeRequestsPerScan);
				openMRsPage = persistenceService.findMergeRequestsByToolConfigIdAndState(new ObjectId(identifier),
						ScmMergeRequests.MergeRequestState.OPEN, pageable);
				allOpenMRs.addAll(openMRsPage.getContent());
				currentPage++;
			}

			return allOpenMRs;
		} catch (Exception e) {
			log.warn("Failed to fetch existing open merge requests for identifier {}: {}", identifier, e.getMessage());
			return List.of();
		}
	}

	/**
	 * Calculates the start date for fetching updates based on existing open MRs.
	 * Uses the oldest creation date among open MRs, with a reasonable minimum
	 * window.
	 */
	private LocalDateTime calculateUpdateWindowStart(List<ScmMergeRequests> existingOpenMRs) {
		// Find the oldest creation date among open MRs
		LocalDateTime oldestCreationDate = existingOpenMRs.stream().map(ScmMergeRequests::getUpdatedOn)
				.filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(LocalDateTime.now().minusMonths(3));

		// Ensure we don't go back more than 6 months for performance reasons
		LocalDateTime maxLookback = LocalDateTime.now().minusMonths(6);

		return oldestCreationDate.isBefore(maxLookback) ? maxLookback : oldestCreationDate;
	}

	/**
	 * Filters the recent MRs to only include those that correspond to existing open
	 * MRs.
	 */
	private List<ScmMergeRequests> filterRelevantUpdates(List<ScmMergeRequests> allRecentMRs,
			List<ScmMergeRequests> existingOpenMRs) {
		// Create a set of external IDs from existing open MRs for efficient lookup
		Set<String> existingOpenMRIds = existingOpenMRs.stream().map(ScmMergeRequests::getExternalId)
				.collect(Collectors.toSet());

		// Filter recent MRs to only include those that match existing open MRs
		return allRecentMRs.stream().filter(mr -> existingOpenMRIds.contains(mr.getExternalId())).toList();
	}

	/**
	 * Combines new merge requests and updated open merge requests, removing
	 * duplicates. Priority is given to the updated versions over new versions.
	 */
	private List<ScmMergeRequests> combineAndDeduplicateMergeRequests(List<ScmMergeRequests> newMergeRequests,
			List<ScmMergeRequests> updatedOpenMergeRequests) {

		// Use a map to deduplicate by external ID, giving priority to updated versions
		Map<String, ScmMergeRequests> mergeRequestMap = new HashMap<>();

		// First add new merge requests
		for (ScmMergeRequests mr : newMergeRequests) {
			if (mr.getExternalId() != null) {
				mergeRequestMap.put(mr.getExternalId(), mr);
			}
		}

		// Then add/overwrite with updated open merge requests (these take priority)
		for (ScmMergeRequests mr : updatedOpenMergeRequests) {
			if (mr.getExternalId() != null) {
				mergeRequestMap.put(mr.getExternalId(), mr);
				log.debug("Updated MR #{} with latest status: {}", mr.getExternalId(), mr.getState());
			}
		}

		return new ArrayList<>(mergeRequestMap.values());
	}

	/**
	 * Calculates the merge requests since date based on scan request parameters.
	 */
	private LocalDateTime calculateMergeRequestsSince(ScanRequest scanRequest) {
		if (scanRequest.getLastScanFrom() != null && scanRequest.getLastScanFrom() != 0L) {
			// If lastScanFrom is provided, use it as the start date for merge requests
			// (based on updated date)
			LocalDateTime mergeRequestsSince = LocalDateTime.ofEpochSecond(scanRequest.getLastScanFrom() / 1000, 0,
					java.time.ZoneOffset.UTC);
			log.debug("Using lastScanFrom timestamp for merge requests (updated date filter): {}", mergeRequestsSince);
			return mergeRequestsSince;
		} else if (scanRequest.getSince() != null) {
			// Use the provided since date if available (based on updated date)
			log.debug("Using provided since date for merge requests (updated date filter): {}", scanRequest.getSince());
			return scanRequest.getSince();
		} else {
			// Use firstScanFromMonths as fallback (based on updated date)
			LocalDateTime mergeRequestsSince = LocalDateTime.now().minusMonths(firstScanFromMonths);
			log.debug("Using firstScanFrom ({} months) for merge requests (updated date filter): {}",
					firstScanFromMonths, mergeRequestsSince);
			return mergeRequestsSince;
		}
	}

	/**
	 * Formats the token based on the tool type.
	 */
	private String formatToken(ScanRequest scanRequest) {
		if (scanRequest.getToolType().equalsIgnoreCase(ScmConstants.BITBUCKET)) {
			return scanRequest.getUsername() + ":" + scanRequest.getToken();
		} else {
			return scanRequest.getToken();
		}
	}
}