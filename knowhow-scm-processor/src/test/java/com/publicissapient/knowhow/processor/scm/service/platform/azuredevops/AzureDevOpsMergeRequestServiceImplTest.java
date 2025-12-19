package com.publicissapient.knowhow.processor.scm.service.platform.azuredevops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.azd.common.types.Author;
import org.azd.enums.PullRequestStatus;
import org.azd.git.types.GitPullRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.knowhow.processor.scm.client.azuredevops.AzureDevOpsClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;

@ExtendWith(MockitoExtension.class)
class AzureDevOpsMergeRequestServiceImplTest {

    @Mock
    private AzureDevOpsClient azureDevOpsClient;

    @Mock
    private AzureDevOpsCommonHelper commonHelper;

    @InjectMocks
    private AzureDevOpsMergeRequestServiceImpl service;

    private GitUrlParser.GitUrlInfo gitUrlInfo;
    private String toolConfigId;
    private String branchName;
    private String token;
    private LocalDateTime since;
    private LocalDateTime until;

    @BeforeEach
    void setUp() {
        gitUrlInfo = mock(GitUrlParser.GitUrlInfo.class);
        when(gitUrlInfo.getOrganization()).thenReturn("testOrg");
        when(gitUrlInfo.getProject()).thenReturn("testProject");
        when(gitUrlInfo.getRepositoryName()).thenReturn("testRepo");

        toolConfigId = "507f1f77bcf86cd799439011";
        branchName = "main";
        token = "testToken";
        since = LocalDateTime.now().minusDays(7);
        until = LocalDateTime.now();
    }

    @Test
    void fetchMergeRequests_Success() throws Exception {
        GitPullRequest azurePR = createMockGitPullRequest();
        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Arrays.asList(azurePR));

        List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("123", result.get(0).getExternalId());
        assertEquals("Test PR", result.get(0).getTitle());
        verify(azureDevOpsClient).fetchPullRequests("testOrg", "testProject", "testRepo", token, since, branchName);
    }

    @Test
    void fetchMergeRequests_WithNullBranch() throws Exception {
        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), isNull()))
                .thenReturn(Arrays.asList());

        List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since, until);

        assertNotNull(result);
        verify(azureDevOpsClient).fetchPullRequests("testOrg", "testProject", "testRepo", token, since, null);
    }

    @Test
    void fetchMergeRequests_ThrowsPlatformApiException() throws Exception {
        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("API error"));

        assertThrows(PlatformApiException.class, () ->
                service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until));
    }

    @Test
    void fetchMergeRequests_WithAuthorInfo() throws Exception {
        GitPullRequest azurePR = createMockGitPullRequest();
        User mockUser = User.builder().username("john@example.com").displayName("John Doe").build();
        
        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Arrays.asList(azurePR));
        when(commonHelper.createUser(anyString(), anyString())).thenReturn(mockUser);

        List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(commonHelper).createUser("john@example.com", "John Doe");
    }

    @Test
    void fetchMergeRequests_WithNullAuthor() throws Exception {
        GitPullRequest azurePR = mock(GitPullRequest.class);
        when(azurePR.getPullRequestId()).thenReturn(123);
        when(azurePR.getTitle()).thenReturn("Test PR");
        when(azurePR.getStatus()).thenReturn(PullRequestStatus.ACTIVE);
        when(azurePR.getSourceRefName()).thenReturn("refs/heads/feature");
        when(azurePR.getTargetRefName()).thenReturn("refs/heads/main");
        when(azurePR.getCreatedBy()).thenReturn(null);

        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Arrays.asList(azurePR));

        List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(commonHelper, never()).createUser(anyString(), anyString());
    }

    @Test
    void fetchMergeRequests_WithTimestamps() throws Exception {
        GitPullRequest azurePR = createMockGitPullRequest();
        when(azurePR.getCreationDate()).thenReturn("2024-01-15T10:30:00Z");
        when(azurePR.getClosedDate()).thenReturn("2024-01-20T15:45:00Z");

        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Arrays.asList(azurePR));

        List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNotNull(result.get(0).getCreatedDate());
        assertNotNull(result.get(0).getUpdatedDate());
    }

    @Test
    void fetchMergeRequests_WithNullClosedDate() throws Exception {
        GitPullRequest azurePR = createMockGitPullRequest();
        when(azurePR.getCreationDate()).thenReturn("2024-01-15T10:30:00Z");
        when(azurePR.getClosedDate()).thenReturn(null);

        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Arrays.asList(azurePR));

        List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNotNull(result.get(0).getCreatedDate());
    }

    @Test
    void fetchMergeRequests_EmptyList() throws Exception {
        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Arrays.asList());

        List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void fetchMergeRequests_SkipsInvalidPR() throws Exception {
        GitPullRequest validPR = createMockGitPullRequest();
        GitPullRequest invalidPR = mock(GitPullRequest.class);
        when(invalidPR.getPullRequestId()).thenThrow(new RuntimeException("Invalid PR"));

        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Arrays.asList(validPR, invalidPR));

        List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void fetchMergeRequests_WithUrl() throws Exception {
        GitPullRequest azurePR = createMockGitPullRequest();
        when(azurePR.getUrl()).thenReturn("https://dev.azure.com/testOrg/testProject/_git/testRepo/pullrequest/123");

        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Arrays.asList(azurePR));

        List<ScmMergeRequests> result = service.fetchMergeRequests(toolConfigId, gitUrlInfo, branchName, token, since, until);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("https://dev.azure.com/testOrg/testProject/_git/testRepo/pullrequest/123", result.get(0).getMergeRequestUrl());
    }

    private GitPullRequest createMockGitPullRequest() {
        GitPullRequest pr = mock(GitPullRequest.class);
        when(pr.getPullRequestId()).thenReturn(123);
        when(pr.getTitle()).thenReturn("Test PR");
        when(pr.getDescription()).thenReturn("Test description");
        when(pr.getStatus()).thenReturn(PullRequestStatus.ACTIVE);
        when(pr.getSourceRefName()).thenReturn("refs/heads/feature");
        when(pr.getTargetRefName()).thenReturn("refs/heads/main");
        when(pr.getCreationDate()).thenReturn("2024-01-15T10:30:00Z");

        Author author = mock(Author.class);
        when(author.getUniqueName()).thenReturn("john@example.com");
        when(author.getDisplayName()).thenReturn("John Doe");
        when(pr.getCreatedBy()).thenReturn(author);

        return pr;
    }
}
