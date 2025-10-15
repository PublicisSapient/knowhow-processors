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
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.platform.gitlab.GitLabService;
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
public class PlatformServiceLocatorTest {

	@Mock
	private GitPlatformService gitHubService;

	@Mock
	private GitLabService gitLabService;

	@Mock
	private GitPlatformService azureDevOpsService;

	@Mock
	private GitPlatformService bitbucketService;

	@Mock
	private GitPlatformService genericService;

	@Mock
	private ScanRequest scanRequest;

	private PlatformServiceLocator platformServiceLocator;
	private Map<String, GitPlatformService> platformServices;

	@BeforeEach
	void setUp() {
		platformServices = new HashMap<>();
		platformServices.put("gitHubService", gitHubService);
		platformServices.put("gitLabService", gitLabService);
		platformServices.put("azureDevOpsService", azureDevOpsService);
		platformServices.put("bitbucketService", bitbucketService);

		platformServiceLocator = new PlatformServiceLocator(platformServices);
	}

	@Test
	void testConstructor_WithMultiplePlatformServices_InitializesSuccessfully() {
		// Arrange & Act
		PlatformServiceLocator locator = new PlatformServiceLocator(platformServices);

		// Assert
		assertNotNull(locator);
		// Verify through behavior - constructor logs are internal implementation
		// details
	}

	@Test
	void testConstructor_WithEmptyServices_InitializesSuccessfully() {
		// Arrange
		Map<String, GitPlatformService> emptyServices = new HashMap<>();

		// Act
		PlatformServiceLocator locator = new PlatformServiceLocator(emptyServices);

		// Assert
		assertNotNull(locator);
	}

	@Test
	void testGetPlatformService_GitHubRequest_ReturnsGitHubService() {
		// Arrange
		when(scanRequest.getToolType()).thenReturn("GitHub");

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformService(scanRequest);

		// Assert
		assertEquals(gitHubService, result);
	}

	@Test
	void testGetPlatformService_GitLabRequest_ReturnsGitLabService() {
		// Arrange
		when(scanRequest.getToolType()).thenReturn("GitLab");

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformService(scanRequest);

		// Assert
		assertEquals(gitLabService, result);
	}

	@Test
	void testGetPlatformService_AzureRequest_ReturnsAzureService() {
		// Arrange
		when(scanRequest.getToolType()).thenReturn("AzureRepository");

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformService(scanRequest);

		// Assert
		assertEquals(azureDevOpsService, result);
	}

	@Test
	void testGetPlatformService_BitbucketRequest_ReturnsBitbucketService() {
		// Arrange
		when(scanRequest.getToolType()).thenReturn("Bitbucket");

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformService(scanRequest);

		// Assert
		assertEquals(bitbucketService, result);
	}

	@Test
	void testGetPlatformService_UnknownPlatform_ReturnsNull() {
		// Arrange
		when(scanRequest.getToolType()).thenReturn("UnknownPlatform");

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformService(scanRequest);

		// Assert
		assertNull(result);
	}

	@Test
	void testGetPlatformServiceByUrl_GitHubUrl_ReturnsGitHubService() {
		// Arrange
		String githubUrl = "https://github.com/user/repo";

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformServiceByUrl(githubUrl);

		// Assert
		assertEquals(gitHubService, result);
	}

	@Test
	void testGetPlatformServiceByUrl_GitLabUrl_ReturnsGitLabService() {
		// Arrange
		String gitlabUrl = "https://gitlab.com/user/repo";

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformServiceByUrl(gitlabUrl);

		// Assert
		assertEquals(gitLabService, result);
	}

	@Test
	void testGetPlatformServiceByUrl_BitbucketUrl_ReturnsBitbucketService() {
		// Arrange
		String bitbucketUrl = "https://bitbucket.org/user/repo";

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformServiceByUrl(bitbucketUrl);

		// Assert
		assertEquals(bitbucketService, result);
	}

	@Test
	void testGetPlatformServiceByUrl_NullUrl_ReturnsNull() {
		// Arrange & Act
		GitPlatformService result = platformServiceLocator.getPlatformServiceByUrl(null);

		// Assert
		assertNull(result);
	}

	@Test
	void testGetPlatformServiceByUrl_UnknownUrl_ReturnsNull() {
		// Arrange
		String unknownUrl = "https://unknown-git-platform.com/user/repo";

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformServiceByUrl(unknownUrl);

		// Assert
		assertNull(result);
	}

	@Test
	void testSetRepositoryContext_GitLabService_SetsContext() {
		// Arrange
		String repositoryUrl = "https://gitlab.com/user/repo";

		// Act
		platformServiceLocator.setRepositoryContext(gitLabService, repositoryUrl);

		// Assert
		verify(gitLabService).setRepositoryUrlContext(repositoryUrl);
	}

	@Test
	void testSetRepositoryContext_NonGitLabService_NoOperation() {
		// Arrange
		String repositoryUrl = "https://github.com/user/repo";

		// Act
		platformServiceLocator.setRepositoryContext(gitHubService, repositoryUrl);

		// Assert
		verifyNoInteractions(gitHubService);
	}

	@Test
	void testClearRepositoryContext_GitLabService_ClearsContext() {
		// Arrange & Act
		platformServiceLocator.clearRepositoryContext(gitLabService);

		// Assert
		verify(gitLabService).clearRepositoryUrlContext();
	}

	@Test
	void testClearRepositoryContext_NonGitLabService_NoOperation() {
		// Arrange & Act
		platformServiceLocator.clearRepositoryContext(gitHubService);

		// Assert
		verifyNoInteractions(gitHubService);
	}

	@Test
	void testCallWithContext_SuccessfulCall_ReturnsResultAndClearsContext() throws PlatformApiException {
		// Arrange
		String repositoryUrl = "https://gitlab.com/user/repo";
		String expectedResult = "Success";
		PlatformServiceLocator.PlatformServiceCall<String> serviceCall = () -> expectedResult;

		// Act
		String result = platformServiceLocator.callWithContext(gitLabService, repositoryUrl, serviceCall);

		// Assert
		assertEquals(expectedResult, result);
		verify(gitLabService).setRepositoryUrlContext(repositoryUrl);
		verify(gitLabService).clearRepositoryUrlContext();
	}

	@Test
	void testCallWithContext_ExceptionThrown_ClearsContextAndRethrows() {
		// Arrange
		String repositoryUrl = "https://gitlab.com/user/repo";
		PlatformApiException expectedException = new PlatformApiException("Gitlab", "Test error");
		PlatformServiceLocator.PlatformServiceCall<String> serviceCall = () -> {
			throw expectedException;
		};

		// Act & Assert
		PlatformApiException thrownException = assertThrows(PlatformApiException.class,
				() -> platformServiceLocator.callWithContext(gitLabService, repositoryUrl, serviceCall));

		assertEquals(expectedException, thrownException);
		verify(gitLabService).setRepositoryUrlContext(repositoryUrl);
		verify(gitLabService).clearRepositoryUrlContext();
	}

	@Test
	void testMapPlatformToServiceName_AllPlatforms_ReturnsCorrectBeanNames() {
		// This tests the private method indirectly through getPlatformService

		// Test GitHub
		when(scanRequest.getToolType()).thenReturn("github");
		assertNotNull(platformServiceLocator.getPlatformService(scanRequest));

		// Test GitLab
		when(scanRequest.getToolType()).thenReturn("gitlab");
		assertNotNull(platformServiceLocator.getPlatformService(scanRequest));

		// Test Azure
		when(scanRequest.getToolType()).thenReturn("azurerepository");
		assertNotNull(platformServiceLocator.getPlatformService(scanRequest));

		// Test Bitbucket
		when(scanRequest.getToolType()).thenReturn("bitbucket");
		assertNotNull(platformServiceLocator.getPlatformService(scanRequest));

		// Test fallback pattern for new platforms
		platformServices.put("newplatformService", genericService);
		platformServiceLocator = new PlatformServiceLocator(platformServices);
		when(scanRequest.getToolType()).thenReturn("newplatform");
		assertEquals(genericService, platformServiceLocator.getPlatformService(scanRequest));
	}

	@Test
	void testGetPlatformServiceByUrl_SelfHostedGitLab_ReturnsGitLabService() {
		// Arrange - Test self-hosted GitLab instance
		String selfHostedGitLabUrl = "https://gitlab.mycompany.com/user/repo";

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformServiceByUrl(selfHostedGitLabUrl);

		// Assert
		assertEquals(gitLabService, result);
	}

	@Test
	void testGetPlatformServiceByUrl_SelfHostedBitbucket_ReturnsBitbucketService() {
		// Arrange - Test self-hosted Bitbucket instance
		String selfHostedBitbucketUrl = "https://bitbucket.mycompany.com/user/repo";

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformServiceByUrl(selfHostedBitbucketUrl);

		// Assert
		assertEquals(bitbucketService, result);
	}

	@Test
	void testCallWithContext_NonGitLabService_CallsSuccessfully() throws PlatformApiException {
		// Arrange
		String repositoryUrl = "https://github.com/user/repo";
		String expectedResult = "GitHub Result";
		PlatformServiceLocator.PlatformServiceCall<String> serviceCall = () -> expectedResult;

		// Act
		String result = platformServiceLocator.callWithContext(gitHubService, repositoryUrl, serviceCall);

		// Assert
		assertEquals(expectedResult, result);
		verifyNoInteractions(gitHubService); // Should not interact with non-GitLab services
	}

	@Test
	void testGetPlatformService_CaseInsensitive_ReturnsCorrectService() {
		// Arrange - Test with mixed case
		when(scanRequest.getToolType()).thenReturn("GITHUB");

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformService(scanRequest);

		// Assert
		assertEquals(gitHubService, result);
	}

	@Test
	void testDeterminePlatform_EdgeCases_HandlesCorrectly() {
		// Test empty platform services map doesn't affect URL-based lookup
		Map<String, GitPlatformService> emptyServices = new HashMap<>();
		PlatformServiceLocator emptyLocator = new PlatformServiceLocator(emptyServices);

		// Should return null since no services are registered
		assertNull(emptyLocator.getPlatformServiceByUrl("https://github.com/user/repo"));
	}

	@Test
	void testCallWithContext_RuntimeException_StillClearsContext() {
		// Arrange
		String repositoryUrl = "https://gitlab.com/user/repo";
		RuntimeException expectedException = new RuntimeException("Unexpected error");
		PlatformServiceLocator.PlatformServiceCall<String> serviceCall = () -> {
			throw expectedException;
		};

		// Act & Assert
		RuntimeException thrownException = assertThrows(RuntimeException.class,
				() -> platformServiceLocator.callWithContext(gitLabService, repositoryUrl, serviceCall));

		assertEquals(expectedException, thrownException);
		verify(gitLabService).setRepositoryUrlContext(repositoryUrl);
		verify(gitLabService).clearRepositoryUrlContext();
	}

	@Test
	void testGetPlatformServiceByUrl_MissingServiceInMap_ReturnsNull() {
		// Arrange - Remove a service from the map
		platformServices.remove("gitHubService");
		platformServiceLocator = new PlatformServiceLocator(platformServices);

		// Act
		GitPlatformService result = platformServiceLocator.getPlatformServiceByUrl("https://github.com/user/repo");

		// Assert
		assertNull(result);
	}
}
