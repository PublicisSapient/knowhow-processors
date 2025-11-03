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

package com.publicissapient.knowhow.processor.scm.service.strategy;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Selects the appropriate commit data fetch strategy based on scan request
 * parameters. Implements the Strategy pattern for strategy selection. Follows
 * Single Responsibility Principle.
 */
@Component
@Slf4j
public class CommitStrategySelector {

	private final Map<String, CommitDataFetchStrategy> commitStrategies;

	private static final String REST_API_COMMIT_DATA_FETCH_STRATEGY = "restApiCommitDataFetchStrategy";

	@Autowired
	public CommitStrategySelector(Map<String, CommitDataFetchStrategy> commitStrategies) {
		this.commitStrategies = commitStrategies;

		// Log available strategies for debugging
		log.info("CommitStrategySelector initialized with {} commit strategies: {}", commitStrategies.size(),
				commitStrategies.keySet());
	}

	/**
	 * Selects the appropriate commit fetch strategy based on the scan request.
	 *
	 * @param scanRequest
	 *            the scan request containing strategy preferences
	 * @return the selected strategy or null if none found
	 */
	public CommitDataFetchStrategy selectStrategy(ScanRequest scanRequest) {
		log.debug("Determining commit strategy for repository: {}, isCloneEnabled: {}", scanRequest.getRepositoryUrl(),
				scanRequest.isCloneEnabled());

		// Check if a specific strategy is explicitly requested
		if (scanRequest.getCommitFetchStrategy() != null) {
			CommitDataFetchStrategy strategy = commitStrategies.get(scanRequest.getCommitFetchStrategy());
			if (strategy != null && supportsStrategy(strategy, scanRequest)) {
				log.debug("Using explicitly requested strategy: {}", scanRequest.getCommitFetchStrategy());
				return strategy;
			}
			log.warn("Requested strategy {} not found or doesn't support repository",
					scanRequest.getCommitFetchStrategy());
		}

		// Determine strategy based on cloneEnabled flag
		String strategyName = determineStrategyName(scanRequest);
		log.debug("Selected strategy based on configuration: {}", strategyName);

		CommitDataFetchStrategy strategy = commitStrategies.get(strategyName);

		if (strategy != null && supportsStrategy(strategy, scanRequest)) {
			log.debug("Successfully found and validated strategy: {}", strategyName);
			return strategy;
		} else {
			log.warn("Strategy {} not found or doesn't support repository URL: {}", strategyName,
					scanRequest.getRepositoryUrl());
		}

		// Fallback to any available strategy that supports the request
		return findFallbackStrategy(scanRequest);
	}

	/**
	 * Determines the strategy name based on scan request and configuration.
	 *
	 * @param scanRequest
	 *            the scan request
	 * @return the strategy name
	 */
	private String determineStrategyName(ScanRequest scanRequest) {
		// Priority order:
		// 1. If cloneEnabled is explicitly set, use it
		// 2. If useRestApiForCommits config is true, use REST API
		// 3. Otherwise use default strategy

		if (scanRequest.isCloneEnabled()) {
			return "jGitCommitDataFetchStrategy";
		} else {
			return REST_API_COMMIT_DATA_FETCH_STRATEGY;
		}
	}


	/**
	 * Finds a fallback strategy that supports the scan request.
	 *
	 * @param scanRequest
	 *            the scan request
	 * @return a fallback strategy or null if none found
	 */
	private CommitDataFetchStrategy findFallbackStrategy(ScanRequest scanRequest) {
		log.debug("Attempting to find fallback strategy for repository: {}", scanRequest.getRepositoryUrl());

		// Try REST API strategy first as it's more universal
		CommitDataFetchStrategy restApiStrategy = commitStrategies.get(REST_API_COMMIT_DATA_FETCH_STRATEGY);
		if (restApiStrategy != null && supportsStrategy(restApiStrategy, scanRequest)) {
			log.info("Using REST API strategy as fallback");
			return restApiStrategy;
		}

		// Try any available strategy
		return commitStrategies.values().stream().filter(s -> supportsStrategy(s, scanRequest)).findFirst()
				.orElse(null);
	}

	/**
	 * Checks if a strategy supports the given scan request. Uses toolType when
	 * available, falls back to URL-based checking.
	 *
	 * @param strategy
	 *            the strategy to check
	 * @param scanRequest
	 *            the scan request
	 * @return true if supported, false otherwise
	 */
	private boolean supportsStrategy(CommitDataFetchStrategy strategy, ScanRequest scanRequest) {
		try {

			// Fallback to URL-based support checking
			boolean supports = strategy.supports(scanRequest.getRepositoryUrl(), scanRequest.getToolType());
			if (supports) {
				log.debug("Strategy {} supports repository URL: {}", strategy.getStrategyName(),
						scanRequest.getRepositoryUrl());
			}
			return supports;

		} catch (Exception e) {
			log.error("Error checking strategy support for {}: {}", strategy.getStrategyName(), e.getMessage());
			return false;
		}
	}

}
