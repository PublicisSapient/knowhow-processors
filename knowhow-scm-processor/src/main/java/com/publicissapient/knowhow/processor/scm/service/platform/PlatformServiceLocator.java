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

package com.publicissapient.knowhow.processor.scm.service.platform;

import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.service.platform.gitlab.GitLabService;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Locates and manages platform services. Implements Service Locator pattern.
 */
@Component
@Slf4j
public class PlatformServiceLocator {

	private final Map<String, GitPlatformService> platformServices;

	private static final String GITLAB = "gitlab";
	private static final String BITBUCKET = "bitbucket";

	@Autowired
	public PlatformServiceLocator(Map<String, GitPlatformService> platformServices) {
		this.platformServices = platformServices;
		log.info("PlatformServiceLocator initialized with {} platform services: {}", platformServices.size(),
				platformServices.keySet());
	}

	/**
	 * Gets the platform service for a scan request.
	 *
	 * @param scanRequest
	 *            the scan request
	 * @return the platform service or null if not found
	 */
	public GitPlatformService getPlatformService(ScanRequest scanRequest) {
		String serviceName = mapPlatformToServiceName(scanRequest.getToolType().toLowerCase());
		GitPlatformService service = platformServices.get(serviceName);

		if (service != null) {
			log.debug("Found platform service '{}' for toolType: {}", serviceName, scanRequest.getToolType());
		} else {
			log.warn("No platform service found for toolType: {}. Available services: {}", scanRequest.getToolType(),
					platformServices.keySet());
		}

		return service;
	}

	/**
	 * Gets the platform service for a repository URL (fallback method).
	 *
	 * @param repositoryUrl
	 *            the repository URL
	 * @return the platform service or null if not found
	 */
	public GitPlatformService getPlatformServiceByUrl(String repositoryUrl) {
		if (repositoryUrl == null) {
			return null;
		}

		String platform = determinePlatform(repositoryUrl);
		if (platform == null) {
			return null;
		}

		String serviceName = mapPlatformToServiceName(platform.toLowerCase());
		return platformServices.get(serviceName);
	}

	/**
	 * Sets repository context for platform services that need it.
	 *
	 * @param platformService
	 *            the platform service
	 * @param repositoryUrl
	 *            the repository URL
	 */
	public void setRepositoryContext(GitPlatformService platformService, String repositoryUrl) {
		if (platformService instanceof GitLabService gitLabService) {
			gitLabService.setRepositoryUrlContext(repositoryUrl);
			log.debug("Set GitLab repository URL context: {}", repositoryUrl);
		}
	}

	/**
	 * Clears repository context for platform services.
	 *
	 * @param platformService
	 *            the platform service
	 */
	public void clearRepositoryContext(GitPlatformService platformService) {
		if (platformService instanceof GitLabService gitLabService) {
			gitLabService.clearRepositoryUrlContext();
			log.debug("Cleared GitLab repository URL context");
		}
	}

	/**
	 * Wrapper method to handle platform service calls with context.
	 *
	 * @param platformService
	 *            the platform service to call
	 * @param repositoryUrl
	 *            the repository URL for context
	 * @param serviceCall
	 *            the service call to execute
	 * @param <T>
	 *            the return type of the service call
	 * @return the result of the service call
	 * @throws PlatformApiException
	 *             if the service call fails
	 */
	public <T> T callWithContext(GitPlatformService platformService, String repositoryUrl,
			PlatformServiceCall<T> serviceCall) throws PlatformApiException {
		try {
			setRepositoryContext(platformService, repositoryUrl);
			return serviceCall.call();
		} finally {
			clearRepositoryContext(platformService);
		}
	}

	/**
	 * Maps platform names to their corresponding Spring service bean names. This
	 * handles the discrepancy between generated keys and actual bean names.
	 *
	 * @param platform
	 *            the platform name (lowercase)
	 * @return the corresponding service bean name
	 */
	private String mapPlatformToServiceName(String platform) {
		return switch (platform) {
		case "github" -> "gitHubService";
		case GITLAB -> "gitLabService";
		case "azurerepository" -> "azureDevOpsService";
		case BITBUCKET -> "bitbucketService";
		default ->
			// Fallback to the original pattern for any new platforms
			platform + "Service";
		};
	}

	/**
	 * Determines the platform from a repository URL.
	 *
	 * @param repositoryUrl
	 *            the repository URL
	 * @return the platform name or null if not recognized
	 */
	private String determinePlatform(String repositoryUrl) {
		String lowerUrl = repositoryUrl.toLowerCase();

		if (lowerUrl.contains("github.com")) {
			return "github";
		} else if (lowerUrl.contains("gitlab.com") || lowerUrl.contains(GITLAB)) {
			return GITLAB;
		} else if (lowerUrl.contains("dev.azure.com") || lowerUrl.contains("visualstudio.com")) {
			return "azure";
		} else if (lowerUrl.contains("bitbucket.org") || lowerUrl.contains(BITBUCKET)) {
			return BITBUCKET;
		}

		return null;
	}

	/**
	 * Functional interface for platform service calls that can throw
	 * PlatformApiException.
	 */
	@FunctionalInterface
	public interface PlatformServiceCall<T> {
		T call() throws PlatformApiException;
	}
}
