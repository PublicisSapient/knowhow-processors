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
class GitLabClientAdditionalTest {

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

    @Mock
    private CommitsApi commitsApi;

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
    void testGetPrPickUpTimeStamp_Success() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        MergeRequest mockMr = mock(MergeRequest.class);
        Author mockAuthor = mock(Author.class);
        when(mockAuthor.getUsername()).thenReturn("author");
        when(mockMr.getAuthor()).thenReturn(mockAuthor);
        when(mockMr.getCreatedAt()).thenReturn(new Date(1000000L));
        
        Note note1 = mock(Note.class);
        Author reviewer = mock(Author.class);
        when(reviewer.getUsername()).thenReturn("reviewer");
        when(note1.getAuthor()).thenReturn(reviewer);
        when(note1.getCreatedAt()).thenReturn(new Date(2000000L));
        when(note1.getBody()).thenReturn("This looks good");
        when(note1.getSystem()).thenReturn(false);
        
        List<Note> notes = Arrays.asList(note1);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getMergeRequestApi()).thenReturn(mergeRequestApi);
        when(gitLabApi.getNotesApi()).thenReturn(notesApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(mergeRequestApi.getMergeRequest(1L, 1L)).thenReturn(mockMr);
        when(notesApi.getMergeRequestNotes(1L, 1L)).thenReturn(notes);
        
        long result = spyClient.getPrPickUpTimeStamp(TEST_ORG, TEST_REPO, TEST_TOKEN, TEST_REPO_URL, 1L);
        
        assertEquals(2000000L, result);
    }

    @Test
    void testGetPrPickUpTimeStamp_NoNotes() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        MergeRequest mockMr = mock(MergeRequest.class);
        when(mockMr.getCreatedAt()).thenReturn(new Date(1000000L));
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getMergeRequestApi()).thenReturn(mergeRequestApi);
        when(gitLabApi.getNotesApi()).thenReturn(notesApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(mergeRequestApi.getMergeRequest(1L, 1L)).thenReturn(mockMr);
        when(notesApi.getMergeRequestNotes(1L, 1L)).thenReturn(Collections.emptyList());
        
        long result = spyClient.getPrPickUpTimeStamp(TEST_ORG, TEST_REPO, TEST_TOKEN, TEST_REPO_URL, 1L);
        
        assertEquals(0L, result);
    }

    @Test
    void testGetPrPickUpTimeStamp_SystemNote() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        MergeRequest mockMr = mock(MergeRequest.class);
        Author mockAuthor = mock(Author.class);
        when(mockAuthor.getUsername()).thenReturn("author");
        when(mockMr.getAuthor()).thenReturn(mockAuthor);
        when(mockMr.getCreatedAt()).thenReturn(new Date(1000000L));
        
        Note note1 = mock(Note.class);
        Author reviewer = mock(Author.class);
        when(reviewer.getUsername()).thenReturn("reviewer");
        when(note1.getAuthor()).thenReturn(reviewer);
        when(note1.getCreatedAt()).thenReturn(new Date(2000000L));
        when(note1.getBody()).thenReturn("approved this merge request");
        when(note1.getSystem()).thenReturn(true);
        
        List<Note> notes = Arrays.asList(note1);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getMergeRequestApi()).thenReturn(mergeRequestApi);
        when(gitLabApi.getNotesApi()).thenReturn(notesApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(mergeRequestApi.getMergeRequest(1L, 1L)).thenReturn(mockMr);
        when(notesApi.getMergeRequestNotes(1L, 1L)).thenReturn(notes);
        
        long result = spyClient.getPrPickUpTimeStamp(TEST_ORG, TEST_REPO, TEST_TOKEN, TEST_REPO_URL, 1L);
        
        assertEquals(2000000L, result);
    }

    @Test
    void testGetPrPickUpTimeStamp_SkipAuthorNotes() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        MergeRequest mockMr = mock(MergeRequest.class);
        Author mockAuthor = mock(Author.class);
        when(mockAuthor.getUsername()).thenReturn("author");
        when(mockMr.getAuthor()).thenReturn(mockAuthor);
        when(mockMr.getCreatedAt()).thenReturn(new Date(1000000L));
        
        Note note1 = mock(Note.class);
        when(note1.getAuthor()).thenReturn(mockAuthor);
        when(note1.getCreatedAt()).thenReturn(new Date(2000000L));
        
        List<Note> notes = Arrays.asList(note1);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getMergeRequestApi()).thenReturn(mergeRequestApi);
        when(gitLabApi.getNotesApi()).thenReturn(notesApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(mergeRequestApi.getMergeRequest(1L, 1L)).thenReturn(mockMr);
        when(notesApi.getMergeRequestNotes(1L, 1L)).thenReturn(notes);
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        
        long result = spyClient.getPrPickUpTimeStamp(TEST_ORG, TEST_REPO, TEST_TOKEN, TEST_REPO_URL, 1L);
        
        assertEquals(0L, result);
    }

    @Test
    void testFetchCommitDiffs_Success() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        Diff diff1 = mock(Diff.class);
        List<Diff> diffs = Arrays.asList(diff1);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getCommitsApi()).thenReturn(commitsApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(commitsApi.getDiff(1L, "abc123")).thenReturn(diffs);
        
        List<Diff> result = spyClient.fetchCommitDiffs(TEST_ORG, TEST_REPO, "abc123", TEST_TOKEN, TEST_REPO_URL);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchCommitDiffs_Exception() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getCommitsApi()).thenReturn(commitsApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(commitsApi.getDiff(1L, "abc123")).thenThrow(new GitLabApiException("Not found", 404));
        
        List<Diff> result = spyClient.fetchCommitDiffs(TEST_ORG, TEST_REPO, "abc123", TEST_TOKEN, TEST_REPO_URL);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchMergeRequestChanges_Success() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        MergeRequest mrWithChanges = mock(MergeRequest.class);
        Diff diff1 = mock(Diff.class);
        when(mrWithChanges.getChanges()).thenReturn(Arrays.asList(diff1));
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getMergeRequestApi()).thenReturn(mergeRequestApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(mergeRequestApi.getMergeRequestChanges(1L, 1L)).thenReturn(mrWithChanges);
        
        List<Diff> result = spyClient.fetchMergeRequestChanges(TEST_ORG, TEST_REPO, 1L, TEST_TOKEN, TEST_REPO_URL);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchMergeRequestCommits_Success() throws Exception {
        GitLabClient spyClient = spy(gitLabClient);
        Project mockProject = mock(Project.class);
        when(mockProject.getId()).thenReturn(1L);
        
        Commit commit1 = mock(Commit.class);
        List<Commit> commits = Arrays.asList(commit1);
        
        doReturn(gitLabApi).when(spyClient).getGitLabClientFromRepoUrl(TEST_TOKEN, TEST_REPO_URL);
        when(gitLabApi.getGitLabServerUrl()).thenReturn(TEST_API_URL);
        when(gitLabApi.getProjectApi()).thenReturn(projectApi);
        when(gitLabApi.getMergeRequestApi()).thenReturn(mergeRequestApi);
        when(projectApi.getProject(TEST_ORG + "/" + TEST_REPO)).thenReturn(mockProject);
        when(mergeRequestApi.getCommits(1L, 1L)).thenReturn(commits);
        
        List<Commit> result = spyClient.fetchMergeRequestCommits(TEST_ORG, TEST_REPO, 1L, TEST_TOKEN, TEST_REPO_URL);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }

}
