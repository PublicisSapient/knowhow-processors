package com.publicissapient.knowhow.processor.scm.service.platform.github;

import com.publicissapient.knowhow.processor.scm.client.github.GitHubClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubMergeRequestServiceImplTest {

    @Mock
    private GitHubClient gitHubClient;

    @Mock
    private GitHubCommonHelper commonHelper;

    @InjectMocks
    private GitHubMergeRequestServiceImpl mergeRequestService;

    private String toolConfigId;
    private GitUrlParser.GitUrlInfo gitUrlInfo;
    private LocalDateTime since;
    private LocalDateTime until;

    @BeforeEach
    void setUp() {
        toolConfigId = new ObjectId().toString();
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "owner", "testRepo", "org",
                "https://github.com/owner/repo.git");
        since = LocalDateTime.now().minusDays(7);
        until = LocalDateTime.now();
    }

    @Test
    void testFetchMergeRequests_Success() throws IOException, PlatformApiException {
        List<GHPullRequest> pullRequests = createMockPullRequests();
        when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(pullRequests);

        List<ScmMergeRequests> result = mergeRequestService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, "token", since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(gitHubClient).fetchPullRequests("owner", "testRepo", "token", since, until);
    }

    @Test
    void testFetchMergeRequests_WithBranchFilter() throws IOException, PlatformApiException {
        List<GHPullRequest> pullRequests = createMockPullRequestsWithDifferentBranches();
        when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(pullRequests);

        List<ScmMergeRequests> result = mergeRequestService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", "token", since, until);

        assertEquals(1, result.size());
    }

    @Test
    void testFetchMergeRequests_IOException() throws IOException {
        when(gitHubClient.fetchPullRequests(anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new IOException("API error"));

        PlatformApiException exception = assertThrows(PlatformApiException.class,
                () -> mergeRequestService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", "token", since, until));
        
        assertEquals("GitHub", exception.getPlatform());
        assertTrue(exception.getMessage().contains("Failed to fetch merge requests from GitHub"));
    }

    @Test
    void testGetPlatformName() {
        assertEquals("GitHub", mergeRequestService.getPlatformName());
    }

    private List<GHPullRequest> createMockPullRequests() throws IOException {
        List<GHPullRequest> pullRequests = new ArrayList<>();
        pullRequests.add(createMockPullRequest(1, "Test PR 1", GHIssueState.OPEN));
        pullRequests.add(createMockPullRequest(2, "Test PR 2", GHIssueState.OPEN));
        return pullRequests;
    }

    private List<GHPullRequest> createMockPullRequestsWithDifferentBranches() throws IOException {
        List<GHPullRequest> pullRequests = new ArrayList<>();

        GHPullRequest mainPR = createMockPullRequest(1, "PR to main", GHIssueState.OPEN);
        GHCommitPointer mainBase = mock(GHCommitPointer.class);
        when(mainBase.getRef()).thenReturn("main");
        when(mainPR.getBase()).thenReturn(mainBase);
        pullRequests.add(mainPR);

        GHPullRequest devPR = createMockPullRequest(2, "PR to dev", GHIssueState.OPEN);
        GHCommitPointer devBase = mock(GHCommitPointer.class);
        when(devBase.getRef()).thenReturn("dev");
        when(devPR.getBase()).thenReturn(devBase);
        pullRequests.add(devPR);

        return pullRequests;
    }

    private GHPullRequest createMockPullRequest(int number, String title, GHIssueState state) throws IOException {
        GHPullRequest pr = mock(GHPullRequest.class);
        when(pr.getNumber()).thenReturn(number);
        when(pr.getTitle()).thenReturn(title);
        when(pr.getBody()).thenReturn("PR body");
        when(pr.getState()).thenReturn(state);
        when(pr.getUpdatedAt()).thenReturn(new Date());
        when(pr.getHtmlUrl()).thenReturn(new URL("https://github.com/test/repo/pull/" + number));

        GHRepository repo = mock(GHRepository.class);
        when(repo.getFullName()).thenReturn("test/repo");
        when(pr.getRepository()).thenReturn(repo);

        GHCommitPointer base = mock(GHCommitPointer.class);
        when(base.getRef()).thenReturn("main");
        when(pr.getBase()).thenReturn(base);

        GHCommitPointer head = mock(GHCommitPointer.class);
        when(head.getRef()).thenReturn("feature-branch");
        when(pr.getHead()).thenReturn(head);

        return pr;
    }
}
