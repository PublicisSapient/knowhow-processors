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
