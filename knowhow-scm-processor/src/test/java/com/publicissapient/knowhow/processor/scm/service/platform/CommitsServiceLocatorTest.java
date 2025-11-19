package com.publicissapient.knowhow.processor.scm.service.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommitsServiceLocatorTest {

	@Mock
	private GitPlatformCommitsService gitHubService;

	@Mock
	private GitPlatformCommitsService gitLabService;

	@Mock
	private GitPlatformCommitsService azureService;

	@Mock
	private GitPlatformCommitsService bitbucketService;

	private CommitsServiceLocator locator;
	private Map<String, GitPlatformCommitsService> servicesMap;

	@BeforeEach
	void setUp() {
		servicesMap = new HashMap<>();
		servicesMap.put("gitHubCommitsServiceImpl", gitHubService);
		servicesMap.put("gitLabCommitsServiceImpl", gitLabService);
		servicesMap.put("azureDevOpsCommitsServiceImpl", azureService);
		servicesMap.put("bitbucketCommitsServiceImpl", bitbucketService);
		locator = new CommitsServiceLocator(servicesMap);
	}

	@Test
	void getCommitsService_github() {
		GitPlatformCommitsService result = locator.getCommitsService("github");
		assertNotNull(result);
		assertEquals(gitHubService, result);
	}

	@Test
	void getCommitsService_gitlab() {
		GitPlatformCommitsService result = locator.getCommitsService("gitlab");
		assertNotNull(result);
		assertEquals(gitLabService, result);
	}

	@Test
	void getCommitsService_azureRepository() {
		GitPlatformCommitsService result = locator.getCommitsService("azurerepository");
		assertNotNull(result);
		assertEquals(azureService, result);
	}

	@Test
	void getCommitsService_bitbucket() {
		GitPlatformCommitsService result = locator.getCommitsService("bitbucket");
		assertNotNull(result);
		assertEquals(bitbucketService, result);
	}

	@Test
	void getCommitsService_caseInsensitive() {
		GitPlatformCommitsService result = locator.getCommitsService("GitHub");
		assertNotNull(result);
		assertEquals(gitHubService, result);
	}

	@Test
	void getCommitsService_notFound() {
		GitPlatformCommitsService result = locator.getCommitsService("unknown");
		assertNull(result);
	}
}
