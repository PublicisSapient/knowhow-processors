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
public class MergeRequestServiceLocator {

	private final Map<String, GitPlatformMergeRequestService> mergeRequestServices;

	@Autowired
	public MergeRequestServiceLocator(Map<String, GitPlatformMergeRequestService> mergeRequestServices) {
		this.mergeRequestServices = mergeRequestServices;
		log.info("MergeRequestServiceLocator initialized with {} services", mergeRequestServices.size());
	}

	public GitPlatformMergeRequestService getMergeRequestService(String toolType) {
		String serviceName = mapToServiceName(toolType.toLowerCase());
		GitPlatformMergeRequestService service = mergeRequestServices.get(serviceName);
		
		if (service == null) {
			log.warn("No merge request service found for toolType: {}", toolType);
		}
		
		return service;
	}

	private String mapToServiceName(String toolType) {
		return switch (toolType) {
			case "github" -> "gitHubMergeRequestServiceImpl";
			case "gitlab" -> "gitLabMergeRequestServiceImpl";
			case "azurerepository" -> "azureDevOpsMergeRequestServiceImpl";
			case "bitbucket" -> "bitbucketMergeRequestServiceImpl";
			default -> toolType + "MergeRequestServiceImpl";
		};
	}
}
