package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test for GitLabService facade - validates delegation to specialized services
 */
@ExtendWith(MockitoExtension.class)
class GitLabServiceTest {

    @Mock
    private GitLabCommitsServiceImpl commitsService;

    @Mock
    private GitLabMergeRequestServiceImpl mergeRequestService;

    @InjectMocks
    private GitLabService gitLabService;

    private String toolConfigId;
    private GitUrlParser.GitUrlInfo gitUrlInfo;
    private String token;
    private LocalDateTime since;
    private LocalDateTime until;

    @BeforeEach
    void setUp() {
        toolConfigId = new ObjectId().toString();
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner", "testRepo", "org",
                "https://gitlab.com/owner/repo.git");
        token = "test-token";
        since = LocalDateTime.now().minusDays(7);
        until = LocalDateTime.now();
    }

    @Test
    void testFetchCommits_DelegatesToCommitsService() throws PlatformApiException {
        List<ScmCommits> expectedCommits = new ArrayList<>();
        when(commitsService.fetchCommits(anyString(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(expectedCommits);

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertSame(expectedCommits, result);
        verify(commitsService).fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);
        verifyNoInteractions(mergeRequestService);
    }

    @Test
    void testFetchMergeRequests_DelegatesToMergeRequestService() throws PlatformApiException {
        List<ScmMergeRequests> expectedMRs = new ArrayList<>();
        when(mergeRequestService.fetchMergeRequests(anyString(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(expectedMRs);

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertSame(expectedMRs, result);
        verify(mergeRequestService).fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);
        verifyNoInteractions(commitsService);
    }

    @Test
    void testGetPlatformName() {
        String platformName = gitLabService.getPlatformName();
        assertEquals("GitLab", platformName);
    }

    @Test
    void testSetRepositoryUrlContext() {
        gitLabService.setRepositoryUrlContext("https://gitlab.example.com/repo");

        verify(commitsService).setRepositoryUrlContext("https://gitlab.example.com/repo");
        verify(mergeRequestService).setRepositoryUrlContext("https://gitlab.example.com/repo");
    }

    @Test
    void testClearRepositoryUrlContext() {
        gitLabService.clearRepositoryUrlContext();

        verify(commitsService).clearRepositoryUrlContext();
        verify(mergeRequestService).clearRepositoryUrlContext();
    }

    @Test
    void testFetchCommits_PropagatesException() throws PlatformApiException {
        PlatformApiException expectedException = new PlatformApiException("GitLab", "Test error");
        when(commitsService.fetchCommits(anyString(), any(), anyString(), anyString(), any(), any()))
                .thenThrow(expectedException);

        PlatformApiException exception = assertThrows(PlatformApiException.class,
                () -> gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until));

        assertSame(expectedException, exception);
    }

    @Test
    void testFetchMergeRequests_PropagatesException() throws PlatformApiException {
        PlatformApiException expectedException = new PlatformApiException("GitLab", "Test error");
        when(mergeRequestService.fetchMergeRequests(anyString(), any(), anyString(), anyString(), any(), any()))
                .thenThrow(expectedException);

        PlatformApiException exception = assertThrows(PlatformApiException.class,
                () -> gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until));

        assertSame(expectedException, exception);
    }
}
