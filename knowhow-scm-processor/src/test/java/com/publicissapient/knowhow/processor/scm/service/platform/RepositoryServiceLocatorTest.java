package com.publicissapient.knowhow.processor.scm.service.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RepositoryServiceLocatorTest {

	@Mock
	private GitPlatformRepositoryService gitHubService;

	@Mock
	private GitPlatformRepositoryService gitLabService;

	@Mock
	private GitPlatformRepositoryService azureService;

	@Mock
	private GitPlatformRepositoryService bitbucketService;

	private RepositoryServiceLocator locator;

	@Test
	void getRepositoryService_github() {
		Map<String, GitPlatformRepositoryService> servicesMap = new HashMap<>();
		servicesMap.put("gitHubRepositoryServiceImpl", gitHubService);
		locator = new RepositoryServiceLocator(servicesMap);

		GitPlatformRepositoryService result = locator.getRepositoryService("github");
		assertNotNull(result);
		assertEquals(gitHubService, result);
	}

	@Test
	void getRepositoryService_gitlab() {
		Map<String, GitPlatformRepositoryService> servicesMap = new HashMap<>();
		servicesMap.put("gitLabRepositoryServiceImpl", gitLabService);
		locator = new RepositoryServiceLocator(servicesMap);

		GitPlatformRepositoryService result = locator.getRepositoryService("gitlab");
		assertNotNull(result);
		assertEquals(gitLabService, result);
	}

	@Test
	void getRepositoryService_azureRepository() {
		Map<String, GitPlatformRepositoryService> servicesMap = new HashMap<>();
		servicesMap.put("azureDevOpsRepositoryServiceImpl", azureService);
		locator = new RepositoryServiceLocator(servicesMap);

		GitPlatformRepositoryService result = locator.getRepositoryService("azurerepository");
		assertNotNull(result);
		assertEquals(azureService, result);
	}

	@Test
	void getRepositoryService_bitbucket() {
		Map<String, GitPlatformRepositoryService> servicesMap = new HashMap<>();
		servicesMap.put("bitbucketRepositoryServiceImpl", bitbucketService);
		locator = new RepositoryServiceLocator(servicesMap);

		GitPlatformRepositoryService result = locator.getRepositoryService("bitbucket");
		assertNotNull(result);
		assertEquals(bitbucketService, result);
	}

	@Test
	void getRepositoryService_caseInsensitive() {
		Map<String, GitPlatformRepositoryService> servicesMap = new HashMap<>();
		servicesMap.put("gitHubRepositoryServiceImpl", gitHubService);
		locator = new RepositoryServiceLocator(servicesMap);

		GitPlatformRepositoryService result = locator.getRepositoryService("GitHub");
		assertNotNull(result);
		assertEquals(gitHubService, result);
	}

	@Test
	void getRepositoryService_emptyMap() {
		locator = new RepositoryServiceLocator(Map.of());

		GitPlatformRepositoryService result = locator.getRepositoryService("github");
		assertNull(result);
	}

	@Test
	void getRepositoryService_nullMap() {
		locator = new RepositoryServiceLocator(null);

		GitPlatformRepositoryService result = locator.getRepositoryService("github");
		assertNull(result);
	}

	@Test
	void getRepositoryService_notFound() {
		Map<String, GitPlatformRepositoryService> servicesMap = new HashMap<>();
		servicesMap.put("gitHubRepositoryServiceImpl", gitHubService);
		locator = new RepositoryServiceLocator(servicesMap);

		GitPlatformRepositoryService result = locator.getRepositoryService("unknown");
		assertNull(result);
	}
}
