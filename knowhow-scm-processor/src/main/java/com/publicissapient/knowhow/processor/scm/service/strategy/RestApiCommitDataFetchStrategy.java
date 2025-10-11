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

import com.publicissapient.knowhow.processor.scm.constants.ScmConstants;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API-based implementation of CommitDataFetchStrategy. This strategy
 * fetches commit data using platform-specific REST APIs without cloning the
 * repository locally. Advantages: - Faster for small datasets - No local disk
 * space required - Platform-specific optimizations Disadvantages: - API rate
 * limiting - Limited by platform API capabilities - Requires platform-specific
 * implementations
 */
@Component("restApiCommitDataFetchStrategy")
@Slf4j
public class RestApiCommitDataFetchStrategy implements CommitDataFetchStrategy {

	private final Map<String, GitPlatformService> platformServices;

	@Autowired
	public RestApiCommitDataFetchStrategy(Map<String, GitPlatformService> platformServices) {
		this.platformServices = platformServices;
	}

	@Override
	public List<ScmCommits> fetchCommits(String toolType, String toolConfigId, GitUrlParser.GitUrlInfo gitUrlInfo,
			String branchName, RepositoryCredentials credentials, LocalDateTime since) throws DataProcessingException {

		log.info("Fetching commits using REST API strategy for repository: {}", gitUrlInfo.getOriginalUrl());

		try {
			// First try to get platform service by toolType (if toolConfigId is actually a
			// toolType)
			GitPlatformService platformService = getPlatformServiceByToolType(toolType);

			if (platformService == null) {
				// Fallback to URL-based detection
				log.debug("Could not determine platform from toolConfigId '{}', falling back to URL parsing", toolType);
				platformService = getPlatformService(gitUrlInfo.getOriginalUrl(), toolType);
			}

			if (platformService == null) {
				throw new DataProcessingException(
						"No platform service found for repository: " + gitUrlInfo.getOriginalUrl());
			}

			String token;
			if (toolType.equalsIgnoreCase(ScmConstants.BITBUCKET)) {
				token = credentials.getUsername() + ":" + credentials.getToken();
			} else {
				token = credentials.getToken();
			}

			// Fetch commits using platform service with context handling
			GitPlatformService finalPlatformService = platformService;
			List<ScmCommits> commitDetails = callPlatformServiceWithContext(platformService,
					gitUrlInfo.getOriginalUrl(),
					() -> finalPlatformService.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, // Pass token
																										 // from
																										 // credentials
							since, null));

			log.info("Successfully fetched {} commits from repository: {}", commitDetails.size(),
					gitUrlInfo.getOriginalUrl());
			return commitDetails;

		} catch (Exception e) {
			log.error("Error fetching commits from repository {}: {}", gitUrlInfo.getOriginalUrl(), e.getMessage(), e);
			throw new DataProcessingException("Failed to fetch commits using REST API strategy", e);
		}
	}

	@Override
	public boolean supports(String repositoryUrl, String toolType) {
		// Try to get platform service by URL parsing as fallback
		return getPlatformService(repositoryUrl, toolType) != null;
	}

	/**
	 * Checks if this strategy supports the given tool type.
	 * 
	 * @param toolType
	 *            the tool type to check
	 * @return true if supported, false otherwise
	 */
	public boolean supportsByToolType(String toolType) {
		return getPlatformServiceByToolType(toolType) != null;
	}

	@Override
	public String getStrategyName() {
		return "REST_API";
	}

	private GitPlatformService getPlatformService(String repositoryUrl, String toolType) {
		if (repositoryUrl == null) {
			log.debug("Repository URL is null");
			return null;
		}

		// Determine platform from URL
		String platform = toolType.toLowerCase();

		log.debug("Determined platform '{}' for URL: {}", platform, repositoryUrl);

		// Get platform service - use proper Spring bean naming convention (camelCase)
		String serviceName = platform.toLowerCase();
		GitPlatformService service = switch (serviceName) {
		case "github" -> platformServices.get("gitHubService");
		case "gitlab" -> platformServices.get("gitLabService");
		case "azure" -> platformServices.get("azureDevOpsService");
		case "bitbucket" -> platformServices.get("bitbucketService");
		default -> null;
		};

		if (service != null) {
			log.debug("Found platform service for platform: {}", platform);
		} else {
			log.warn("No platform service found for platform: {}. Available services: {}", platform,
					platformServices.keySet());
		}

		return service;
	}

	/**
	 * Gets platform service by tool type.
	 * 
	 * @param toolType
	 *            the tool type (e.g., "GITLAB", "GITHUB")
	 * @return the platform service or null if not found
	 */
	private GitPlatformService getPlatformServiceByToolType(String toolType) {
		if (toolType == null) {
			return null;
		}

		String serviceName = mapToolTypeToServiceName(toolType.toLowerCase());
		GitPlatformService service = platformServices.get(serviceName);

		if (service != null) {
			log.debug("Found platform service '{}' for toolType: {}", serviceName, toolType);
		} else {
			log.debug("No platform service found for toolType: {}. Available services: {}", toolType,
					platformServices.keySet());
		}

		return service;
	}

	/**
	 * Maps tool type to service name.
	 * 
	 * @param toolType
	 *            the tool type (lowercase)
	 * @return the service name
	 */
	private String mapToolTypeToServiceName(String toolType) {
		return switch (toolType) {
		case "github" -> "gitHubService";
		case "gitlab" -> "gitLabService";
		case "azure", "azurerepository" -> "azureDevOpsService";
		case "bitbucket" -> "bitbucketService";
		default -> toolType + "Service";
		};
	}

	/**
	 * Wrapper method to handle GitLab repository URL context setting. This ensures
	 * that GitLab service uses the correct base URL for on-premise instances.
	 *
	 * @param platformService
	 *            the platform service to call
	 * @param repositoryUrl
	 *            the repository URL to set as context
	 * @param serviceCall
	 *            the service call to execute
	 * @param <T>
	 *            the return type of the service call
	 * @return the result of the service call
	 * @throws Exception
	 *             if the service call fails
	 */
	private <T> T callPlatformServiceWithContext(GitPlatformService platformService, String repositoryUrl,
			PlatformServiceCall<T> serviceCall) throws Exception {
		// Check if this is a GitLab service and set repository URL context
		if (platformService instanceof com.publicissapient.knowhow.processor.scm.service.platform.gitlab.GitLabService gitLabService) {

			try {
				// Set the repository URL context for this thread
				gitLabService.setRepositoryUrlContext(repositoryUrl);
				log.debug("Set GitLab repository URL context: {}", repositoryUrl);

				// Execute the service call
				return serviceCall.call();

			} finally {
				// Always clear the context after the call
				gitLabService.clearRepositoryUrlContext();
				log.debug("Cleared GitLab repository URL context");
			}
		} else {
			// For non-GitLab services, just execute the call directly
			return serviceCall.call();
		}
	}

	/**
	 * Functional interface for platform service calls that can throw Exception.
	 */
	@FunctionalInterface
	private interface PlatformServiceCall<T> {
		T call() throws PlatformApiException;
	}
}