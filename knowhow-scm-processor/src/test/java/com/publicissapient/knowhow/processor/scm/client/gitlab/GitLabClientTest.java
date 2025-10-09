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
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitLabClientTest {

    @Mock
    private GitUrlParser gitUrlParser;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private GitLabApi gitLabApi;

    @Mock
    private UserApi userApi;

    @Mock
    private ProjectApi projectApi;

    @Mock
    private CommitsApi commitsApi;

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
    void testGetGitLabClient_WithDefaultUrl_Success() throws GitLabApiException {
        GitLabClient spyClient = spy(gitLabClient);
        doReturn(gitLabApi).when(spyClient).getGitLabClient(TEST_TOKEN, DEFAULT_API_URL);

        GitLabApi result = spyClient.getGitLabClient(TEST_TOKEN);

        assertNotNull(result);
        verify(spyClient).getGitLabClient(TEST_TOKEN, DEFAULT_API_URL);
    }

    @Test
    void testGetGitLabClient_WithCustomUrl_Success() throws GitLabApiException {
        assertThrows(GitLabApiException.class, () -> {
            gitLabClient.getGitLabClient(TEST_TOKEN, TEST_API_URL);
        });
    }

    @Test
    void testGetGitLabClient_WithNullToken_ThrowsException() {
        GitLabApiException exception = assertThrows(GitLabApiException.class, () -> {
            gitLabClient.getGitLabClient(null, TEST_API_URL);
        });
        assertEquals("GitLab token cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGetGitLabClient_WithEmptyToken_ThrowsException() {
        GitLabApiException exception = assertThrows(GitLabApiException.class, () -> {
            gitLabClient.getGitLabClient("  ", TEST_API_URL);
        });
        assertEquals("GitLab token cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGetGitLabClient_WithNullApiUrl_UsesDefault() throws GitLabApiException {
        assertThrows(GitLabApiException.class, () -> {
            gitLabClient.getGitLabClient(TEST_TOKEN, null);
        });
    }

    @Test
    void testGetGitLabClient_WithEmptyApiUrl_UsesDefault() throws GitLabApiException {
        assertThrows(GitLabApiException.class, () -> {
            gitLabClient.getGitLabClient(TEST_TOKEN, "  ");
        });
    }

    @Test
    void testGetGitLabClientFromRepoUrl_Success() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        when(gitUrlParser.getGitLabApiBaseUrl(TEST_REPO_URL)).thenReturn(TEST_API_URL);
        doReturn(gitLabApi).when(spyClient).getGitLabClient(TEST_TOKEN, TEST_API_URL);

        GitLabApi result = spyClient.getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);

        assertNotNull(result);
        verify(gitUrlParser).getGitLabApiBaseUrl(TEST_REPO_URL);
    }

    @Test
    void testGetGitLabClientFromRepoUrl_FallbackToDefault() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        when(gitUrlParser.getGitLabApiBaseUrl(TEST_REPO_URL)).thenThrow(new RuntimeException("Parse error"));
        doReturn(gitLabApi).when(spyClient).getGitLabClient(TEST_TOKEN);

        GitLabApi result = spyClient.getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);

        assertNotNull(result);
        verify(spyClient).getGitLabClient(TEST_TOKEN);
    }

    @Test
    void testFetchCommits_Success() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        Commit commit1 = mock(Commit.class);
        Commit commit2 = mock(Commit.class);
        List<Commit> commits = Arrays.asList(commit1, commit2);
        
        Pager<Commit> mockPager = mock(Pager.class);
        when(mockPager.hasNext()).thenReturn(true, false);
        when(mockPager.next()).thenReturn(commits);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getCommitsApi()).thenReturn(commitsApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(commitsApi.getCommits(anyLong(), eq(TEST_BRANCH), any(), any(), anyInt())).thenReturn(mockPager);
        
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        LocalDateTime until = LocalDateTime.now();
        
        List<Commit> result = spyClient.fetchCommits(TEST_ORG, TEST_REPO, TEST_BRANCH, TEST_TOKEN, since, until, TEST_REPO_URL);
        
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(rateLimitService, atLeastOnce()).checkRateLimit(eq("GitLab"), eq(TEST_TOKEN), anyString(), eq(TEST_API_URL));
    }

    @Test
    void testFetchCommits_WithNullDates_Success() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        Commit commit1 = mock(Commit.class);
        List<Commit> commits = Arrays.asList(commit1);
        
        Pager<Commit> mockPager = mock(Pager.class);
        when(mockPager.hasNext()).thenReturn(true, false);
        when(mockPager.next()).thenReturn(commits);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getCommitsApi()).thenReturn(commitsApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(commitsApi.getCommits(anyLong(), eq(TEST_BRANCH), isNull(), isNull(), anyInt())).thenReturn(mockPager);
        
        List<Commit> result = spyClient.fetchCommits(TEST_ORG, TEST_REPO, TEST_BRANCH, TEST_TOKEN, null, null, TEST_REPO_URL);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchCommits_EmptyResult() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        Pager<Commit> mockPager = mock(Pager.class);
        when(mockPager.hasNext()).thenReturn(true);
        when(mockPager.next()).thenReturn(Collections.emptyList());
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getCommitsApi()).thenReturn(commitsApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(commitsApi.getCommits(anyLong(), eq(TEST_BRANCH), any(), any(), anyInt())).thenReturn(mockPager);
        
        List<Commit> result = spyClient.fetchCommits(TEST_ORG, TEST_REPO, TEST_BRANCH, TEST_TOKEN, 
            LocalDateTime.now().minusDays(7), LocalDateTime.now(), TEST_REPO_URL);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchCommits_MaxLimitReached() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        ReflectionTestUtils.setField(spyClient, "maxCommitsPerScan", 50);
        
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        List<Commit> commits = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            commits.add(mock(Commit.class));
        }
        
        Pager<Commit> mockPager = mock(Pager.class);
        when(mockPager.hasNext()).thenReturn(true, true);
        when(mockPager.next()).thenReturn(commits);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getCommitsApi()).thenReturn(commitsApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(commitsApi.getCommits(anyLong(), eq(TEST_BRANCH), any(), any(), anyInt())).thenReturn(mockPager);
        
        List<Commit> result = spyClient.fetchCommits(TEST_ORG, TEST_REPO, TEST_BRANCH, TEST_TOKEN, 
            LocalDateTime.now().minusDays(7), LocalDateTime.now(), TEST_REPO_URL);
        
        assertNotNull(result);
        assertEquals(50, result.size());
    }

    @Test
    void testFetchCommits_GitLabApiException() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenThrow(new GitLabApiException("API Error", 404));
        
        assertThrows(GitLabApiException.class, () -> {
            spyClient.fetchCommits(TEST_ORG, TEST_REPO, TEST_BRANCH, TEST_TOKEN, 
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), TEST_REPO_URL);
        });
    }

    @Test
    void testFetchCommits_UnexpectedException() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenThrow(new RuntimeException("Unexpected error"));
        
        GitLabApiException exception = assertThrows(GitLabApiException.class, () -> {
            spyClient.fetchCommits(TEST_ORG, TEST_REPO, TEST_BRANCH, TEST_TOKEN, 
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), TEST_REPO_URL);
        });
        
        assertEquals("Unexpected error during commit fetch", exception.getMessage());
        assertEquals(503, exception.getHttpStatus());
    }
}
