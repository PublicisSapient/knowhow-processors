package com.publicissapient.knowhow.processor.scm.service.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class CommitsServiceLocator {

	private final Map<String, GitPlatformCommitsService> commitsServices;

	@Autowired
	public CommitsServiceLocator(Map<String, GitPlatformCommitsService> commitsServices) {
		this.commitsServices = commitsServices;
		log.info("CommitsServiceLocator initialized with {} services", commitsServices.size());
	}

	public GitPlatformCommitsService getCommitsService(String toolType) {
		String serviceName = mapToServiceName(toolType.toLowerCase());
		GitPlatformCommitsService service = commitsServices.get(serviceName);
		
		if (service == null) {
			log.warn("No commits service found for toolType: {}", toolType);
		}
		
		return service;
	}

	private String mapToServiceName(String toolType) {
		return switch (toolType) {
			case "github" -> "gitHubCommitsServiceImpl";
			case "gitlab" -> "gitLabCommitsServiceImpl";
			case "azurerepository" -> "azureDevOpsCommitsServiceImpl";
			case "bitbucket" -> "bitbucketCommitsServiceImpl";
			default -> toolType + "CommitsServiceImpl";
		};
	}
}
