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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class RepositoryServiceLocator {

	private final Map<String, GitPlatformRepositoryService> repositoryServices;

	@Autowired(required = false)
	public RepositoryServiceLocator(Map<String, GitPlatformRepositoryService> repositoryServices) {
		this.repositoryServices = repositoryServices != null ? repositoryServices : Map.of();
		log.info("RepositoryServiceLocator initialized with {} services", this.repositoryServices.size());
	}

	public GitPlatformRepositoryService getRepositoryService(String toolType) {
		if (repositoryServices.isEmpty()) {
			log.debug("No repository services available (only GitHub has separate repository service)");
			return null;
		}
		
		String serviceName = mapToServiceName(toolType.toLowerCase());
		GitPlatformRepositoryService service = repositoryServices.get(serviceName);
		
		if (service == null) {
			log.debug("No repository service found for toolType: {} (only GitHub has separate repository service)", toolType);
		}
		
		return service;
	}

	private String mapToServiceName(String toolType) {
		return switch (toolType) {
			case "github" -> "gitHubRepositoryServiceImpl";
			case "gitlab" -> "gitLabRepositoryServiceImpl";
			case "azurerepository" -> "azureDevOpsRepositoryServiceImpl";
			case "bitbucket" -> "bitbucketRepositoryServiceImpl";
			default -> toolType + "RepositoryServiceImpl";
		};
	}
}
