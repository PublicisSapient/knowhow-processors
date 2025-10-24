package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.knowhow.processor.scm.client.gitlab.GitLabClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.bson.types.ObjectId;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Author;
import org.gitlab4j.api.models.MergeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitLabMergeRequestServiceImplTest {

    @Mock
    private GitLabClient gitLabClient;

    @Mock
    private GitLabCommonHelper commonHelper;

    @InjectMocks
    private GitLabMergeRequestServiceImpl mergeRequestService;

    private String toolConfigId;
    private GitUrlParser.GitUrlInfo gitUrlInfo;
    private LocalDateTime since;
    private LocalDateTime until;

    @BeforeEach
    void setUp() {
        toolConfigId = new ObjectId().toString();
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.GITLAB, "owner", "testRepo", "org",
                "https://gitlab.com/owner/repo.git");
        since = LocalDateTime.now().minusDays(7);
        until = LocalDateTime.now();
    }

    @Test
    void testFetchMergeRequests_Success() throws GitLabApiException, PlatformApiException {
        List<MergeRequest> gitlabMRs = createMockMergeRequests();
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(gitlabMRs);
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(0L);
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmMergeRequests> result = mergeRequestService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, "token", since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(gitLabClient).fetchMergeRequests(eq("org"), eq("testRepo"), isNull(), eq("token"), any(), any(), anyString());
    }

    @Test
    void testFetchMergeRequests_GitLabApiException() throws GitLabApiException {
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenThrow(new GitLabApiException("API error"));

        PlatformApiException exception = assertThrows(PlatformApiException.class,
                () -> mergeRequestService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", "token", since, until));

        assertEquals("GitLab", exception.getPlatform());
        assertTrue(exception.getMessage().contains("Failed to fetch merge requests from GitLab"));
    }

    @Test
    void testGetPlatformName() {
        assertEquals("GitLab", mergeRequestService.getPlatformName());
    }

    @Test
    void testSetAndClearRepositoryUrlContext() {
        mergeRequestService.setRepositoryUrlContext("https://gitlab.example.com/repo");
        mergeRequestService.clearRepositoryUrlContext();
        assertTrue(true);
    }

    private List<MergeRequest> createMockMergeRequests() {
        List<MergeRequest> mrs = new ArrayList<>();
        mrs.add(createMockMergeRequest(1L, "Test MR 1"));
        mrs.add(createMockMergeRequest(2L, "Test MR 2"));
        return mrs;
    }

    private MergeRequest createMockMergeRequest(Long iid, String title) {
        MergeRequest mr = mock(MergeRequest.class);
        when(mr.getIid()).thenReturn(iid);
        when(mr.getTitle()).thenReturn(title);
        when(mr.getDescription()).thenReturn("Description");
        when(mr.getState()).thenReturn("opened");
        when(mr.getSourceBranch()).thenReturn("feature");
        when(mr.getTargetBranch()).thenReturn("main");
        when(mr.getCreatedAt()).thenReturn(new Date());
        when(mr.getUpdatedAt()).thenReturn(new Date());
        
        Author author = mock(Author.class);
        when(author.getUsername()).thenReturn("testuser");
        when(mr.getAuthor()).thenReturn(author);
        
        return mr;
    }
}
