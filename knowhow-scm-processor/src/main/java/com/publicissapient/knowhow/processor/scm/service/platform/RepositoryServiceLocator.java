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
