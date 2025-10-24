package com.publicissapient.knowhow.processor.scm.service.platform.bitbucket;

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

@ExtendWith(MockitoExtension.class)
class BitbucketServiceTest {

    @Mock
    private BitbucketCommitsServiceImpl commitsService;

    @Mock
    private BitbucketMergeRequestServiceImpl mergeRequestService;

    @InjectMocks
    private BitbucketService bitbucketService;

    private String toolConfigId;
    private GitUrlParser.GitUrlInfo gitUrlInfo;
    private String token;
    private LocalDateTime since;
    private LocalDateTime until;

    @BeforeEach
    void setUp() {
        toolConfigId = new ObjectId().toString();
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.BITBUCKET, "owner", "testRepo", null,
                "https://bitbucket.org/owner/repo.git");
        token = "user:pass";
        since = LocalDateTime.now().minusDays(7);
        until = LocalDateTime.now();
    }

    @Test
    void testFetchCommits_DelegatesToCommitsService() throws PlatformApiException {
        List<ScmCommits> expectedCommits = new ArrayList<>();
        when(commitsService.fetchCommits(anyString(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(expectedCommits);

        List<ScmCommits> result = bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertSame(expectedCommits, result);
        verify(commitsService).fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);
        verifyNoInteractions(mergeRequestService);
    }

    @Test
    void testFetchMergeRequests_DelegatesToMergeRequestService() throws PlatformApiException {
        List<ScmMergeRequests> expectedMRs = new ArrayList<>();
        when(mergeRequestService.fetchMergeRequests(anyString(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(expectedMRs);

        List<ScmMergeRequests> result = bitbucketService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertSame(expectedMRs, result);
        verify(mergeRequestService).fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);
        verifyNoInteractions(commitsService);
    }

    @Test
    void testGetPlatformName() {
        assertEquals("Bitbucket", bitbucketService.getPlatformName());
    }

    @Test
    void testSetRepositoryUrlContext() {
        bitbucketService.setRepositoryUrlContext("https://bitbucket.example.com/repo");

        verify(commitsService).setRepositoryUrlContext("https://bitbucket.example.com/repo");
        verify(mergeRequestService).setRepositoryUrlContext("https://bitbucket.example.com/repo");
    }

    @Test
    void testClearRepositoryUrlContext() {
        bitbucketService.clearRepositoryUrlContext();

        verify(commitsService).clearRepositoryUrlContext();
        verify(mergeRequestService).clearRepositoryUrlContext();
    }

    @Test
    void testFetchCommits_PropagatesException() throws PlatformApiException {
        PlatformApiException expectedException = new PlatformApiException("Bitbucket", "Test error");
        when(commitsService.fetchCommits(anyString(), any(), anyString(), anyString(), any(), any()))
                .thenThrow(expectedException);

        PlatformApiException exception = assertThrows(PlatformApiException.class,
                () -> bitbucketService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until));

        assertSame(expectedException, exception);
    }
}
