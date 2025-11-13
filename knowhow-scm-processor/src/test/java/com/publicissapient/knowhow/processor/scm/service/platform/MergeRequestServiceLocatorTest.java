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
class MergeRequestServiceLocatorTest {

	@Mock
	private GitPlatformMergeRequestService gitHubService;

	@Mock
	private GitPlatformMergeRequestService gitLabService;

	@Mock
	private GitPlatformMergeRequestService azureService;

	@Mock
	private GitPlatformMergeRequestService bitbucketService;

	private MergeRequestServiceLocator locator;
	private Map<String, GitPlatformMergeRequestService> servicesMap;

	@BeforeEach
	void setUp() {
		servicesMap = new HashMap<>();
		servicesMap.put("gitHubMergeRequestServiceImpl", gitHubService);
		servicesMap.put("gitLabMergeRequestServiceImpl", gitLabService);
		servicesMap.put("azureDevOpsMergeRequestServiceImpl", azureService);
		servicesMap.put("bitbucketMergeRequestServiceImpl", bitbucketService);
		locator = new MergeRequestServiceLocator(servicesMap);
	}

	@Test
	void getMergeRequestService_github() {
		GitPlatformMergeRequestService result = locator.getMergeRequestService("github");
		assertNotNull(result);
		assertEquals(gitHubService, result);
	}

	@Test
	void getMergeRequestService_gitlab() {
		GitPlatformMergeRequestService result = locator.getMergeRequestService("gitlab");
		assertNotNull(result);
		assertEquals(gitLabService, result);
	}

	@Test
	void getMergeRequestService_azureRepository() {
		GitPlatformMergeRequestService result = locator.getMergeRequestService("azurerepository");
		assertNotNull(result);
		assertEquals(azureService, result);
	}

	@Test
	void getMergeRequestService_bitbucket() {
		GitPlatformMergeRequestService result = locator.getMergeRequestService("bitbucket");
		assertNotNull(result);
		assertEquals(bitbucketService, result);
	}

	@Test
	void getMergeRequestService_caseInsensitive() {
		GitPlatformMergeRequestService result = locator.getMergeRequestService("GitHub");
		assertNotNull(result);
		assertEquals(gitHubService, result);
	}

	@Test
	void getMergeRequestService_notFound() {
		GitPlatformMergeRequestService result = locator.getMergeRequestService("unknown");
		assertNull(result);
	}
}
