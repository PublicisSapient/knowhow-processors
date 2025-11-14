package com.publicissapient.knowhow.processor.scm.service.platform.azuredevops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.azd.git.types.GitCommitRef;
import org.azd.git.types.GitUserDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.knowhow.processor.scm.client.azuredevops.AzureDevOpsClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;

@ExtendWith(MockitoExtension.class)
class AzureDevOpsCommitsServiceImplTest {

    @Mock
    private AzureDevOpsClient azureDevOpsClient;

    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private AzureDevOpsCommitsServiceImpl service;

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
        when(gitUrlInfo.getOriginalUrl()).thenReturn("https://dev.azure.com/testOrg/testProject/_git/testRepo");

        toolConfigId = "507f1f77bcf86cd799439011";
        branchName = "main";
        token = "testToken";
        since = LocalDateTime.now().minusDays(7);
        until = LocalDateTime.now();
    }

    @Test
    void fetchCommits_Success() throws Exception {
        GitCommitRef azureCommit = createMockGitCommitRef();
        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Arrays.asList(azureCommit));

        List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("abc123", result.get(0).getSha());
        assertEquals("Test commit", result.get(0).getCommitLog());
        verify(rateLimitService).checkRateLimit(eq("AzureDevOps"), eq(token), eq("testRepo"), anyString());
    }

    @Test
    void fetchCommits_RateLimitCheck() throws Exception {
        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Arrays.asList());

        service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

        verify(rateLimitService).checkRateLimit("AzureDevOps", token, "testRepo", gitUrlInfo.getOriginalUrl());
    }

    @Test
    void fetchCommits_ThrowsPlatformApiException() throws Exception {
        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new RuntimeException("API error"));

        assertThrows(PlatformApiException.class, () ->
                service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until));
    }

    @Test
    void fetchCommits_WithNullProject() throws Exception {
        when(gitUrlInfo.getProject()).thenReturn(null);
        GitCommitRef azureCommit = createMockGitCommitRef();
        when(azureDevOpsClient.fetchCommits(eq("testOrg"), eq("testRepo"), eq("testRepo"), anyString(), anyString(), any(), any()))
                .thenReturn(Arrays.asList(azureCommit));

        List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

        assertNotNull(result);
        verify(azureDevOpsClient).fetchCommits(eq("testOrg"), eq("testRepo"), eq("testRepo"), anyString(), anyString(), any(), any());
    }

    @Test
    void fetchCommits_WithAuthorInfo() throws Exception {
        GitCommitRef azureCommit = createMockGitCommitRef();
        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Arrays.asList(azureCommit));

        List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

        ScmCommits commit = result.get(0);
        assertEquals("John Doe", commit.getAuthor());
        assertEquals("john@example.com", commit.getAuthorEmail());
        assertNotNull(commit.getCommitTimestamp());
    }

    @Test
    void fetchCommits_WithNullAuthor() throws Exception {
        GitCommitRef azureCommit = mock(GitCommitRef.class);
        when(azureCommit.getCommitId()).thenReturn("abc123");
        when(azureCommit.getComment()).thenReturn("Test commit");
        when(azureCommit.getAuthor()).thenReturn(null);

        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Arrays.asList(azureCommit));

        List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNull(result.get(0).getAuthor());
    }

    @Test
    void fetchCommits_EmptyCommitList() throws Exception {
        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Arrays.asList());

        List<ScmCommits> result = service.fetchCommits(toolConfigId, gitUrlInfo, branchName, token, since, until);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    private GitCommitRef createMockGitCommitRef() {
        GitCommitRef commit = mock(GitCommitRef.class);
        when(commit.getCommitId()).thenReturn("abc123");
        when(commit.getComment()).thenReturn("Test commit");
        when(commit.getUrl()).thenReturn("https://dev.azure.com/testOrg/testProject/_git/testRepo/commit/abc123");

        GitUserDate author = mock(GitUserDate.class);
        when(author.getName()).thenReturn("John Doe");
        when(author.getEmail()).thenReturn("john@example.com");
        when(author.getDate()).thenReturn("2024-01-15T10:30:00Z");
        when(commit.getAuthor()).thenReturn(author);

        return commit;
    }
}
