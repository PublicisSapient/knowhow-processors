package com.publicissapient.knowhow.processor.scm.client.gitlab;

import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import org.gitlab4j.api.*;
import org.gitlab4j.api.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitLabClientMergeRequestTest {

    @Mock
    private GitUrlParser gitUrlParser;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private GitLabApi gitLabApi;

    @Mock
    private ProjectApi projectApi;

    @Mock
    private MergeRequestApi mergeRequestApi;

    @Mock
    private NotesApi notesApi;

    @InjectMocks
    private GitLabClient gitLabClient;

    private static final String TEST_TOKEN = "test-token";
    private static final String TEST_API_URL = "https://gitlab.example.com";
    private static final String DEFAULT_API_URL = "https://gitlab.com";
    private static final String TEST_ORG = "test-org";
    private static final String TEST_REPO = "test-repo";
    private static final String TEST_BRANCH = "main";
    private static final String TEST_REPO_URL = "https://gitlab.example.com/test-org/test-repo";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gitLabClient, "defaultGitlabApiUrl", DEFAULT_API_URL);
        ReflectionTestUtils.setField(gitLabClient, "maxCommitsPerScan", 1000);
        ReflectionTestUtils.setField(gitLabClient, "maxMergeRequestsPerScan", 500);
    }

    @Test
    void testFetchMergeRequests_Success() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        MergeRequest mr1 = mock(MergeRequest.class);
        when(mr1.getSourceBranch()).thenReturn(TEST_BRANCH);
        when(mr1.getIid()).thenReturn(1L);
        
        List<MergeRequest> mergeRequests = Arrays.asList(mr1);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getMergeRequestApi()).thenReturn(mergeRequestApi);
        when(gitLabApi.getNotesApi()).thenReturn(notesApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(mergeRequestApi.getMergeRequests(any(MergeRequestFilter.class), anyInt(), anyInt())).thenReturn(mergeRequests, Collections.emptyList());
        when(notesApi.getMergeRequestNotes(anyLong(), anyLong())).thenReturn(Collections.emptyList());
        
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        LocalDateTime until = LocalDateTime.now();
        
        List<MergeRequest> result = spyClient.fetchMergeRequests(TEST_ORG, TEST_REPO, TEST_BRANCH, TEST_TOKEN, since, until, TEST_REPO_URL);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(rateLimitService, atLeastOnce()).checkRateLimit(eq("GitLab"), eq(TEST_TOKEN), anyString(), eq(TEST_API_URL));
    }

    @Test
    void testFetchMergeRequests_WithBranchFiltering() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        MergeRequest mr1 = mock(MergeRequest.class);
        when(mr1.getSourceBranch()).thenReturn(TEST_BRANCH);
        when(mr1.getIid()).thenReturn(1L);

        MergeRequest mr2 = mock(MergeRequest.class);
        when(mr2.getSourceBranch()).thenReturn("feature");
        when(mr2.getTargetBranch()).thenReturn("develop");

        List<MergeRequest> mergeRequests = Arrays.asList(mr1, mr2);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getMergeRequestApi()).thenReturn(mergeRequestApi);
        when(gitLabApi.getNotesApi()).thenReturn(notesApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(mergeRequestApi.getMergeRequests(any(MergeRequestFilter.class), anyInt(), anyInt())).thenReturn(mergeRequests, Collections.emptyList());
        when(notesApi.getMergeRequestNotes(anyLong(), anyLong())).thenReturn(Collections.emptyList());
        
        List<MergeRequest> result = spyClient.fetchMergeRequests(TEST_ORG, TEST_REPO, TEST_BRANCH, TEST_TOKEN, 
            LocalDateTime.now().minusDays(7), LocalDateTime.now(), TEST_REPO_URL);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(TEST_BRANCH, result.get(0).getSourceBranch());
    }

    @Test
    void testFetchMergeRequests_WithNullBranch() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        MergeRequest mr1 = mock(MergeRequest.class);
        when(mr1.getIid()).thenReturn(1L);
        
        List<MergeRequest> mergeRequests = Arrays.asList(mr1);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getMergeRequestApi()).thenReturn(mergeRequestApi);
        when(gitLabApi.getNotesApi()).thenReturn(notesApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(mergeRequestApi.getMergeRequests(any(MergeRequestFilter.class), anyInt(), anyInt())).thenReturn(mergeRequests, Collections.emptyList());
        when(notesApi.getMergeRequestNotes(anyLong(), anyLong())).thenReturn(Collections.emptyList());
        
        List<MergeRequest> result = spyClient.fetchMergeRequests(TEST_ORG, TEST_REPO, null, TEST_TOKEN, 
            LocalDateTime.now().minusDays(7), LocalDateTime.now(), TEST_REPO_URL);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchMergeRequests_RateLimitRetry() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        MergeRequest mr1 = mock(MergeRequest.class);
        when(mr1.getIid()).thenReturn(1L);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getMergeRequestApi()).thenReturn(mergeRequestApi);
        when(gitLabApi.getNotesApi()).thenReturn(notesApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(mergeRequestApi.getMergeRequests(any(MergeRequestFilter.class), anyInt(), anyInt()))
            .thenThrow(new GitLabApiException("Rate limit", 429))
            .thenReturn(Arrays.asList(mr1), Collections.emptyList());
        when(notesApi.getMergeRequestNotes(anyLong(), anyLong())).thenReturn(Collections.emptyList());
        
        List<MergeRequest> result = spyClient.fetchMergeRequests(TEST_ORG, TEST_REPO, TEST_BRANCH, TEST_TOKEN, 
            LocalDateTime.now().minusDays(7), LocalDateTime.now(), TEST_REPO_URL);
        
        assertNotNull(result);
    }

    @Test
    void testFetchMergeRequests_ApiException() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getMergeRequestApi()).thenReturn(mergeRequestApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(mergeRequestApi.getMergeRequests(any(MergeRequestFilter.class), anyInt(), anyInt()))
            .thenThrow(new GitLabApiException("API Error", 500));
        
        assertThrows(GitLabApiException.class, () -> {
            spyClient.fetchMergeRequests(TEST_ORG, TEST_REPO, TEST_BRANCH, TEST_TOKEN, 
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), TEST_REPO_URL);
        });
    }

    @Test
    void testFetchMergeRequests_ProjectNotFound() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenThrow(new GitLabApiException("Not found", 404));
        
        assertThrows(GitLabApiException.class, () -> {
            spyClient.fetchMergeRequests(TEST_ORG, TEST_REPO, TEST_BRANCH, TEST_TOKEN, 
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), TEST_REPO_URL);
        });
    }
}
