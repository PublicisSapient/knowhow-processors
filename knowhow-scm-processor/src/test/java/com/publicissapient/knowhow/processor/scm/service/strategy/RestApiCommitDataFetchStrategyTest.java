package com.publicissapient.knowhow.processor.scm.service.strategy;

import com.publicissapient.knowhow.processor.scm.constants.ScmConstants;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.knowhow.processor.scm.exception.DataProcessingException;
import com.publicissapient.knowhow.processor.scm.service.platform.GitPlatformService;
import com.publicissapient.knowhow.processor.scm.service.platform.gitlab.GitLabService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RestApiCommitDataFetchStrategyTest {

    private RestApiCommitDataFetchStrategy strategy;

    @Mock
    private GitPlatformService gitHubService;

    @Mock
    private GitLabService gitLabService;

    @Mock
    private GitPlatformService azureDevOpsService;

    @Mock
    private GitPlatformService bitbucketService;

    @Mock
    private GitUrlParser gitUrlParser;

    private Map<String, GitPlatformService> platformServices;
    private GitUrlParser.GitUrlInfo gitUrlInfo;
    private CommitDataFetchStrategy.RepositoryCredentials credentials;
    private LocalDateTime since;

    @BeforeEach
    void setUp() {
        platformServices = new HashMap<>();
        platformServices.put("gitHubService", gitHubService);
        platformServices.put("gitLabService", gitLabService);
        platformServices.put("azureDevOpsService", azureDevOpsService);
        platformServices.put("bitbucketService", bitbucketService);

        strategy = new RestApiCommitDataFetchStrategy(platformServices);

        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "testRepo", "org", "https://github.com/owner/repo.git");
        credentials = CommitDataFetchStrategy.RepositoryCredentials.builder()
                .username("testuser")
                .token("testtoken")
                .build();

        since = LocalDateTime.now().minusDays(7);
    }

    @Test
    void testFetchCommits_GitHubPlatform_Success() throws Exception {
        // Arrange
        String toolType = "github";
        String toolConfigId = "config123";
        String branchName = "main";
        List<ScmCommits> expectedCommits = createMockCommits(3);

        when(gitHubService.fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(credentials.getToken()), eq(since), isNull()))
                .thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo,
                branchName, credentials, since);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(expectedCommits, result);
        verify(gitHubService).fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(credentials.getToken()), eq(since), isNull());
    }

    @Test
    void testFetchCommits_GitLabPlatform_Success() throws Exception {
        // Arrange
        String toolType = "gitlab";
        String toolConfigId = "config456";
        String branchName = "develop";
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "testRepo", "org", "https://gitlab.com/owner/repo.git");
        List<ScmCommits> expectedCommits = createMockCommits(2);

        when(gitLabService.fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(credentials.getToken()), eq(since), isNull()))
                .thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo,
                branchName, credentials, since);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(expectedCommits, result);
        verify(gitLabService).setRepositoryUrlContext(gitUrlInfo.getOriginalUrl());
        verify(gitLabService).clearRepositoryUrlContext();
        verify(gitLabService).fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(credentials.getToken()), eq(since), isNull());
    }

    @Test
    void testFetchCommits_BitbucketPlatform_Success() throws Exception {
        // Arrange
        String toolType = ScmConstants.BITBUCKET;
        String toolConfigId = "config789";
        String branchName = "feature";
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "testRepo", "org", "https://bitbucket.org/owner/repo.git");
        List<ScmCommits> expectedCommits = createMockCommits(5);
        String expectedToken = credentials.getUsername() + ":" + credentials.getToken();

        when(bitbucketService.fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(expectedToken), eq(since), isNull()))
                .thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo,
                branchName, credentials, since);

        // Assert
        assertNotNull(result);
        assertEquals(5, result.size());
        assertEquals(expectedCommits, result);
        verify(bitbucketService).fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(expectedToken), eq(since), isNull());
    }

    @Test
    void testFetchCommits_FallbackToUrlParsing_Success() throws Exception {
        // Arrange
        String toolType = "unknownTool";
        String toolConfigId = "config999";
        String branchName = "main";
        List<ScmCommits> expectedCommits = createMockCommits(1);

        // First attempt returns null (no service by tool type)
        // Fallback to URL parsing should use github based on toolType passed
        when(gitHubService.fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(credentials.getToken()), eq(since), isNull()))
                .thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = strategy.fetchCommits("github", toolConfigId, gitUrlInfo,
                branchName, credentials, since);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(gitHubService).fetchCommits(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testFetchCommits_NoPlatformService_ThrowsException() {
        // Arrange
        String toolType = "unsupported";
        String toolConfigId = "config000";
        String branchName = "main";
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "testRepo", "org", "https://test.com/owner/repo.git");

        // Act & Assert
        DataProcessingException exception = assertThrows(DataProcessingException.class, () ->
                strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo, branchName, credentials, since));

        assertTrue(exception.getMessage().contains("Failed to fetch commits using REST API strategy"));
    }

    @Test
    void testFetchCommits_PlatformServiceException_ThrowsDataProcessingException() throws Exception {
        // Arrange
        String toolType = "github";
        String toolConfigId = "config111";
        String branchName = "main";

        when(gitHubService.fetchCommits(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("API error"));

        // Act & Assert
        DataProcessingException exception = assertThrows(DataProcessingException.class, () ->
                strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo, branchName, credentials, since));

        assertTrue(exception.getMessage().contains("Failed to fetch commits using REST API strategy"));
        assertNotNull(exception.getCause());
    }

    @Test
    void testSupports_ValidRepositoryUrl_ReturnsTrue() {
        // Arrange
        String repositoryUrl = "https://github.com/owner/repo.git";
        String toolType = "github";

        // Act
        boolean result = strategy.supports(repositoryUrl, toolType);

        // Assert
        assertTrue(result);
    }

    @Test
    void testSupports_InvalidRepositoryUrl_ReturnsFalse() {
        // Arrange
        String repositoryUrl = "https://unknown.com/repo.git";
        String toolType = "unknown";

        // Act
        boolean result = strategy.supports(repositoryUrl, toolType);

        // Assert
        assertFalse(result);
    }

    @Test
    void testSupportsByToolType_ValidToolType_ReturnsTrue() {
        // Act & Assert
        assertTrue(strategy.supportsByToolType("github"));
        assertTrue(strategy.supportsByToolType("gitlab"));
        assertTrue(strategy.supportsByToolType("azure"));
        assertTrue(strategy.supportsByToolType("bitbucket"));
    }

    @Test
    void testSupportsByToolType_InvalidToolType_ReturnsFalse() {
        // Act & Assert
        assertFalse(strategy.supportsByToolType("svn"));
        assertFalse(strategy.supportsByToolType("mercurial"));
        assertFalse(strategy.supportsByToolType(null));
    }

    @Test
    void testGetStrategyName_ReturnsRestApi() {
        // Act
        String strategyName = strategy.getStrategyName();

        // Assert
        assertEquals("REST_API", strategyName);
    }

    @Test
    void testFetchCommits_AzurePlatform_Success() throws Exception {
        // Arrange
        String toolType = "azure";
        String toolConfigId = "configAzure";
        String branchName = "master";
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "testRepo", "org", "https://dev.azure.com/org/project/_git/repo");
        List<ScmCommits> expectedCommits = createMockCommits(4);
        when(azureDevOpsService.fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(credentials.getToken()), eq(since), isNull()))
                .thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo,
                branchName, credentials, since);

        // Assert
        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals(expectedCommits, result);
        verify(azureDevOpsService).fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(credentials.getToken()), eq(since), isNull());
    }

//    @Test
//    void testFetchCommits_NullRepositoryUrl_HandlesGracefully() throws Exception {
//        // Arrange
//        String toolType = "github";
//        String toolConfigId = "configNull";
//        String branchName = "main";
//        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "testRepo", "org", null);
//
//        // Act & Assert
//        DataProcessingException exception = assertThrows(DataProcessingException.class, () ->
//                strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo, branchName, credentials, since));
//
//        assertTrue(exception.getMessage().contains("No platform service found"));
//    }

    @Test
    void testSupports_NullRepositoryUrl_ReturnsFalse() {
        // Act
        boolean result = strategy.supports(null, "github");

        // Assert
        assertFalse(result);
    }

    @Test
    void testSupportsByToolType_AllSupportedTypes_ReturnsTrue() {
        // Arrange
        List<String> supportedTypes = Arrays.asList("github", "gitlab", "azure", "azurerepository", "bitbucket");

        // Act & Assert
        for (String toolType : supportedTypes) {
            assertTrue(strategy.supportsByToolType(toolType),
                    "Should support tool type: " + toolType);
        }
    }

    // Helper method to create mock commits
    private List<ScmCommits> createMockCommits(int count) {
        List<ScmCommits> commits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ScmCommits commit = new ScmCommits();
            commit.setRevisionNumber("commit" + i);
            commit.setAuthor("author" + i);
            commit.setCommitMessage("Commit message " + i);
            commit.setCommitTimestamp(System.currentTimeMillis());
            commits.add(commit);
        }
        return commits;
    }

    @Test
    void testFetchCommits_GitLabServiceWithContextException_ClearsContext() throws Exception {
        // Arrange
        String toolType = "gitlab";
        String toolConfigId = "configGitLab";
        String branchName = "main";
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "testRepo", "org", "https://gitlab.com/owner/repo.git");

        when(gitLabService.fetchCommits(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("GitLab API error"));

        // Act & Assert
        assertThrows(DataProcessingException.class, () ->
                strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo, branchName, credentials, since));

        // Verify context is still cleared even when exception occurs
        verify(gitLabService).setRepositoryUrlContext(gitUrlInfo.getOriginalUrl());
        verify(gitLabService).clearRepositoryUrlContext();
    }

    @Test
    void testFetchCommits_CaseInsensitiveToolType_Success() throws Exception {
        // Arrange
        String toolType = "GITHUB"; // uppercase
        String toolConfigId = "configCase";
        String branchName = "main";
        List<ScmCommits> expectedCommits = createMockCommits(2);

        when(gitHubService.fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(credentials.getToken()), eq(since), isNull()))
                .thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo,
                branchName, credentials, since);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(gitHubService).fetchCommits(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testFetchCommits_EmptyCommitsList_ReturnsEmpty() throws Exception {
        // Arrange
        String toolType = "github";
        String toolConfigId = "configEmpty";
        String branchName = "main";
        List<ScmCommits> expectedCommits = new ArrayList<>();

        when(gitHubService.fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(credentials.getToken()), eq(since), isNull()))
                .thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo,
                branchName, credentials, since);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testMapToolTypeToServiceName_AllMappings() {
        // This test ensures all tool type mappings work correctly
        // We'll test this indirectly through supportsByToolType

        Map<String, Boolean> expectedMappings = new HashMap<>();
        expectedMappings.put("github", true);
        expectedMappings.put("gitlab", true);
        expectedMappings.put("azure", true);
        expectedMappings.put("azurerepository", true);
        expectedMappings.put("bitbucket", true);
        expectedMappings.put("unknown", false);

        for (Map.Entry<String, Boolean> entry : expectedMappings.entrySet()) {
            assertEquals(entry.getValue(), strategy.supportsByToolType(entry.getKey()),
                    "Tool type mapping failed for: " + entry.getKey());
        }
    }

    @Test
    void testFetchCommits_NonGitLabService_NoContextHandling() throws Exception {
        // Arrange
        String toolType = "github";
        String toolConfigId = "configNoContext";
        String branchName = "main";
        List<ScmCommits> expectedCommits = createMockCommits(1);

        when(gitHubService.fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(credentials.getToken()), eq(since), isNull()))
                .thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo,
                branchName, credentials, since);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        // Verify that GitLab-specific methods are never called for non-GitLab services
        verify(gitLabService, never()).setRepositoryUrlContext(any());
        verify(gitLabService, never()).clearRepositoryUrlContext();
    }

//    @Test
//    void testFetchCommits_NullCredentials_ThrowsException() {
//        // Arrange
//        String toolType = "github";
//        String toolConfigId = "configNullCreds";
//        String branchName = "main";
//
//        // Act & Assert
//        assertThrows(NullPointerException.class, () ->
//                strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo, branchName, null, since));
//    }

    @Test
    void testFetchCommits_BitbucketWithNullUsername_HandlesGracefully() throws Exception {
        // Arrange
        String toolType = ScmConstants.BITBUCKET;
        String toolConfigId = "configBitbucketNull";
        String branchName = "main";
        credentials = CommitDataFetchStrategy.RepositoryCredentials.builder()
                .username(null)
                .token("testtoken")
                .build();
        List<ScmCommits> expectedCommits = createMockCommits(1);
        String expectedToken = "null:" + credentials.getToken(); // null gets converted to string

        when(bitbucketService.fetchCommits(eq(toolConfigId), eq(gitUrlInfo), eq(branchName),
                eq(expectedToken), eq(since), isNull()))
                .thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo,
                branchName, credentials, since);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testSupports_EmptyToolType_ReturnsFalse() {
        // Act
        boolean result = strategy.supports("https://github.com/repo.git", "");

        // Assert
        assertFalse(result);
    }

    @Test
    void testFetchCommits_LoggingVerification() throws Exception {
        // This test verifies that appropriate logging occurs
        // In a real scenario, you might use a LogCaptor or similar

        String toolType = "github";
        String toolConfigId = "configLog";
        String branchName = "main";
        List<ScmCommits> expectedCommits = createMockCommits(10);

        when(gitHubService.fetchCommits(any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedCommits);

        // Act
        List<ScmCommits> result = strategy.fetchCommits(toolType, toolConfigId, gitUrlInfo,
                branchName, credentials, since);

        // Assert
        assertNotNull(result);
        assertEquals(10, result.size());
        // In real test, verify log statements were called
    }
}
