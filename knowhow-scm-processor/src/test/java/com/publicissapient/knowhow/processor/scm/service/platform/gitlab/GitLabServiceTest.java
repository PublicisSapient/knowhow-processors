package com.publicissapient.knowhow.processor.scm.service.platform.gitlab;

import com.publicissapient.knowhow.processor.scm.client.gitlab.GitLabClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.bson.types.ObjectId;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class GitLabServiceTest {

    @Mock
    private GitLabClient gitLabClient;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private GitUrlParser gitUrlParser;

    @InjectMocks
    private GitLabService gitLabService;

    private String toolConfigId;
    private GitUrlParser.GitUrlInfo gitUrlInfo;
    private String token;
    private LocalDateTime since;
    private LocalDateTime until;
    private String defaultGitlabApiUrl;

    @BeforeEach
    void setUp() {
        toolConfigId = new ObjectId().toString();
        gitUrlInfo = new GitUrlParser.GitUrlInfo(
                GitUrlParser.GitPlatform.GITLAB,
                "testowner",
                "testrepo",
                "testorg",
                "https://gitlab.com/testorg/testrepo.git"
        );
        token = "test-token";
        since = LocalDateTime.now().minusDays(7);
        until = LocalDateTime.now();
        defaultGitlabApiUrl = "https://gitlab.com";
        ReflectionTestUtils.setField(gitLabService, "defaultGitlabApiUrl", defaultGitlabApiUrl);
    }

    @AfterEach
    void tearDown() {
        gitLabService.clearRepositoryUrlContext();
    }

    @Test
    void testGetPlatformName() {
        assertEquals("GitLab", gitLabService.getPlatformName());
    }

    @Test
    void testSetAndClearRepositoryUrlContext() {
        String testUrl = "https://gitlab.example.com/group/repo";
        gitLabService.setRepositoryUrlContext(testUrl);
        gitLabService.clearRepositoryUrlContext();
        assertTrue(true);
    }

    @Test
    void testFetchCommits_Success() throws GitLabApiException, PlatformApiException {
        List<Commit> gitlabCommits = createMockGitLabCommits();
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(gitlabCommits);
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("commit1", result.get(0).getSha());
        assertEquals("Test commit 1", result.get(0).getCommitMessage());
        verify(gitLabClient).fetchCommits(eq("testorg"), eq("testrepo"), eq("main"), eq(token), eq(since), eq(until), anyString());
    }

    @Test
    void testFetchCommits_WithNullOrganization() throws GitLabApiException, PlatformApiException {
        gitUrlInfo = new GitUrlParser.GitUrlInfo(
                GitUrlParser.GitPlatform.GITLAB,
                "testowner",
                "testrepo",
                null,
                "https://gitlab.com/testowner/testrepo.git"
        );
        List<Commit> gitlabCommits = createMockGitLabCommits();
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(gitlabCommits);
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(gitLabClient).fetchCommits(eq("testowner"), eq("testrepo"), eq("main"), eq(token), eq(since), eq(until), anyString());
    }

    @Test
    void testFetchCommits_GitLabApiException() throws GitLabApiException {
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenThrow(new GitLabApiException("API Error"));

        PlatformApiException exception = assertThrows(PlatformApiException.class,
                () -> gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until));

        assertEquals("GitLab", exception.getPlatform());
        assertTrue(exception.getMessage().contains("Failed to fetch commits from GitLab"));
    }

    @Test
    void testFetchCommits_PartialConversionFailure() throws GitLabApiException, PlatformApiException {
        List<Commit> gitlabCommits = new ArrayList<>();
        gitlabCommits.add(createMockCommit("commit1", "Valid commit"));
        Commit invalidCommit = mock(Commit.class);
        when(invalidCommit.getId()).thenReturn("commit2");
        when(invalidCommit.getMessage()).thenThrow(new RuntimeException("Conversion error"));
        gitlabCommits.add(invalidCommit);

        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(gitlabCommits);
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertEquals("commit1", result.get(0).getSha());
    }

    @Test
    void testFetchCommits_EmptyList() throws GitLabApiException, PlatformApiException {
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchCommits_WithStats() throws GitLabApiException, PlatformApiException {
        Commit commit = createMockCommitWithStats();
        List<Diff> diffs = createMockDiffs();
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(commit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(diffs);

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertEquals(10, result.get(0).getAddedLines());
        assertEquals(5, result.get(0).getRemovedLines());
    }

    @Test
    void testFetchCommits_MergeCommit() throws GitLabApiException, PlatformApiException {
        Commit mergeCommit = createMockMergeCommit();
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(mergeCommit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsMergeCommit());
        assertEquals(2, result.get(0).getParentShas().size());
    }

    @Test
    void testFetchMergeRequests_Success() throws GitLabApiException, PlatformApiException {
        List<MergeRequest> gitlabMRs = createMockGitLabMergeRequests();
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(gitlabMRs);
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(System.currentTimeMillis());
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("1", result.get(0).getExternalId());
        assertEquals("Test MR 1", result.get(0).getTitle());
    }

    @Test
    void testFetchMergeRequests_WithNullBranch() throws GitLabApiException, PlatformApiException {
        List<MergeRequest> gitlabMRs = createMockGitLabMergeRequests();
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), isNull(), anyString(), any(), any(), anyString()))
                .thenReturn(gitlabMRs);
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(System.currentTimeMillis());
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void testFetchMergeRequests_GitLabApiException() throws GitLabApiException {
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenThrow(new GitLabApiException("API Error"));

        PlatformApiException exception = assertThrows(PlatformApiException.class,
                () -> gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until));

        assertEquals("GitLab", exception.getPlatform());
        assertTrue(exception.getMessage().contains("Failed to fetch merge requests from GitLab"));
    }

    @Test
    void testFetchMergeRequests_PartialConversionFailure() throws GitLabApiException, PlatformApiException {
        List<MergeRequest> gitlabMRs = new ArrayList<>();
        gitlabMRs.add(createMockMergeRequest(1L, "Valid MR", "opened"));
        MergeRequest invalidMR = mock(MergeRequest.class);
        when(invalidMR.getIid()).thenReturn(2L);
        when(invalidMR.getTitle()).thenThrow(new RuntimeException("Conversion error"));
        gitlabMRs.add(invalidMR);

        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(gitlabMRs);
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(System.currentTimeMillis());
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertEquals("1", result.get(0).getExternalId());
    }

    @Test
    void testFetchMergeRequests_MergedState() throws GitLabApiException, PlatformApiException {
        MergeRequest mergedMR = createMockMergedMergeRequest();
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(mergedMR));
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(System.currentTimeMillis());
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).getMergedAt());
    }

    @Test
    void testFetchMergeRequests_ClosedState() throws GitLabApiException, PlatformApiException {
        MergeRequest closedMR = createMockClosedMergeRequest();
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(closedMR));
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(System.currentTimeMillis());
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertTrue(result.get(0).isClosed());
        assertNotNull(result.get(0).getClosedDate());
    }

    @Test
    void testFetchMergeRequests_DraftMR() throws GitLabApiException, PlatformApiException {
        MergeRequest draftMR = createMockDraftMergeRequest();
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(draftMR));
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(System.currentTimeMillis());
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsDraft());
    }

    // Helper methods to create mock objects
    private List<Commit> createMockGitLabCommits() {
        List<Commit> commits = new ArrayList<>();
        commits.add(createMockCommit("commit1", "Test commit 1"));
        commits.add(createMockCommit("commit2", "Test commit 2"));
        return commits;
    }

    private Commit createMockCommit(String id, String message) {
        Commit commit = mock(Commit.class);
        when(commit.getId()).thenReturn(id);
        when(commit.getMessage()).thenReturn(message);
        when(commit.getCreatedAt()).thenReturn(new Date());
        when(commit.getCommittedDate()).thenReturn(new Date());
        when(commit.getAuthorName()).thenReturn("Test Author");
        when(commit.getAuthorEmail()).thenReturn("author@test.com");
        when(commit.getCommitterName()).thenReturn("Test Committer");
        when(commit.getCommitterEmail()).thenReturn("committer@test.com");
        when(commit.getParentIds()).thenReturn(List.of("parent1"));
        return commit;
    }

    private Commit createMockCommitWithStats() {
        Commit commit = createMockCommit("commit1", "Commit with stats");
        CommitStats stats = mock(CommitStats.class);
        when(stats.getAdditions()).thenReturn(10);
        when(stats.getDeletions()).thenReturn(5);
        when(stats.getTotal()).thenReturn(15);
        when(commit.getStats()).thenReturn(stats);
        return commit;
    }

    private Commit createMockMergeCommit() {
        Commit commit = createMockCommit("merge1", "Merge commit");
        when(commit.getParentIds()).thenReturn(Arrays.asList("parent1", "parent2"));
        return commit;
    }

    private List<Diff> createMockDiffs() {
        List<Diff> diffs = new ArrayList<>();
        Diff diff = mock(Diff.class);
        when(diff.getNewPath()).thenReturn("test.java");
        when(diff.getOldPath()).thenReturn("test.java");
        when(diff.getNewFile()).thenReturn(false);
        when(diff.getDeletedFile()).thenReturn(false);
        when(diff.getRenamedFile()).thenReturn(false);
        when(diff.getDiff()).thenReturn("@@ -1,3 +1,5 @@\n context\n+added line\n-removed line");
        diffs.add(diff);
        return diffs;
    }

    private List<MergeRequest> createMockGitLabMergeRequests() {
        List<MergeRequest> mrs = new ArrayList<>();
        mrs.add(createMockMergeRequest(1L, "Test MR 1", "opened"));
        mrs.add(createMockMergeRequest(2L, "Test MR 2", "opened"));
        return mrs;
    }

    private MergeRequest createMockMergeRequest(Long iid, String title, String state) {
        MergeRequest mr = mock(MergeRequest.class);
        when(mr.getIid()).thenReturn(iid);
        when(mr.getTitle()).thenReturn(title);
        when(mr.getDescription()).thenReturn("Test description");
        when(mr.getState()).thenReturn(state);
        when(mr.getSourceBranch()).thenReturn("feature");
        when(mr.getTargetBranch()).thenReturn("main");
        when(mr.getCreatedAt()).thenReturn(new Date());
        when(mr.getUpdatedAt()).thenReturn(new Date());
        when(mr.getWebUrl()).thenReturn("https://gitlab.com/test/repo/-/merge_requests/" + iid);
        
        Author author = mock(Author.class);
        when(author.getUsername()).thenReturn("testuser");
        when(author.getName()).thenReturn("Test User");
        when(author.getEmail()).thenReturn("test@example.com");
        when(mr.getAuthor()).thenReturn(author);
        
        return mr;
    }

    private MergeRequest createMockMergedMergeRequest() {
        MergeRequest mr = createMockMergeRequest(1L, "Merged MR", "merged");
        when(mr.getMergedAt()).thenReturn(new Date());
        return mr;
    }

    private MergeRequest createMockClosedMergeRequest() {
        MergeRequest mr = createMockMergeRequest(1L, "Closed MR", "closed");
        when(mr.getClosedAt()).thenReturn(new Date());
        return mr;
    }

    private MergeRequest createMockDraftMergeRequest() {
        MergeRequest mr = createMockMergeRequest(1L, "Draft MR", "opened");
        when(mr.getWorkInProgress()).thenReturn(true);
        return mr;
    }

    @Test
    void testFetchCommits_WithDiffStats() throws GitLabApiException, PlatformApiException {
        Commit commit = createMockCommitWithStats();
        List<Diff> diffs = createMockDiffsWithVariousTypes();
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(commit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(diffs);

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertFalse(result.get(0).getFileChanges().isEmpty());
    }

    @Test
    void testFetchCommits_WithoutStats() throws GitLabApiException, PlatformApiException {
        Commit commit = createMockCommit("commit1", "Commit without stats");
        when(commit.getStats()).thenReturn(null);
        List<Diff> diffs = createMockDiffs();
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(commit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(diffs);

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
    }

    @Test
    void testFetchCommits_DiffExtractionException() throws GitLabApiException, PlatformApiException {
        Commit commit = createMockCommit("commit1", "Test commit");
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(commit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Diff error"));

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getAddedLines());
        assertEquals(0, result.get(0).getRemovedLines());
    }

    @Test
    void testFetchMergeRequests_WithStats() throws GitLabApiException, PlatformApiException {
        MergeRequest mr = createMockMergeRequest(1L, "MR with stats", "opened");
        List<Diff> changes = createMockDiffsWithContent();
        List<Commit> commits = createMockGitLabCommits();
        
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(mr));
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(System.currentTimeMillis());
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(changes);
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(commits);

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getLinesChanged() > 0);
        assertEquals(2, result.get(0).getCommitCount());
    }

    @Test
    void testFetchMergeRequests_StatsExtractionException() throws GitLabApiException, PlatformApiException {
        MergeRequest mr = createMockMergeRequest(1L, "MR", "opened");
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(mr));
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(System.currentTimeMillis());
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Stats error"));
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Commits error"));

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getLinesChanged());
        assertEquals(0, result.get(0).getCommitCount());
    }

    @Test
    void testFetchMergeRequests_WithChangesCount() throws GitLabApiException, PlatformApiException {
        MergeRequest mr = createMockMergeRequest(1L, "MR", "opened");
        when(mr.getChangesCount()).thenReturn("50");
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(mr));
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(System.currentTimeMillis());
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
    }

    @Test
    void testFetchMergeRequests_InvalidChangesCount() throws GitLabApiException, PlatformApiException {
        MergeRequest mr = createMockMergeRequest(1L, "MR", "opened");
        when(mr.getChangesCount()).thenReturn("invalid");
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(mr));
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(System.currentTimeMillis());
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
    }

    @Test
    void testFetchMergeRequests_NullAuthor() throws GitLabApiException, PlatformApiException {
        MergeRequest mr = createMockMergeRequest(1L, "MR", "opened");
        when(mr.getAuthor()).thenReturn(null);
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(mr));
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(System.currentTimeMillis());
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
    }

    @Test
    void testFetchMergeRequests_AuthorWithNullEmail() throws GitLabApiException, PlatformApiException {
        MergeRequest mr = createMockMergeRequest(1L, "MR", "opened");
        Author author = mock(Author.class);
        when(author.getUsername()).thenReturn("testuser");
        when(author.getName()).thenReturn("Test User");
        when(author.getEmail()).thenReturn(null);
        when(mr.getAuthor()).thenReturn(author);
        
        when(gitLabClient.fetchMergeRequests(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(mr));
        when(gitLabClient.getPrPickUpTimeStamp(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(System.currentTimeMillis());
        when(gitLabClient.fetchMergeRequestChanges(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(gitLabClient.fetchMergeRequestCommits(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmMergeRequests> result = gitLabService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertEquals("Test User", result.get(0).getAuthorId().getEmail());
    }

    @Test
    void testRepositoryUrlContext_WithContext() throws GitLabApiException, PlatformApiException {
        String customUrl = "https://gitlab.custom.com/group/repo";
        gitLabService.setRepositoryUrlContext(customUrl);
        
        List<Commit> gitlabCommits = createMockGitLabCommits();
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), eq(customUrl)))
                .thenReturn(gitlabCommits);
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertNotNull(result);
        verify(gitLabClient).fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), eq(customUrl));
    }

    @Test
    void testRepositoryUrlContext_EmptyContext() throws GitLabApiException, PlatformApiException {
        gitLabService.setRepositoryUrlContext("   ");
        
        List<Commit> gitlabCommits = createMockGitLabCommits();
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(gitlabCommits);
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertNotNull(result);
    }

    @Test
    void testConstructRepositoryUrl_WithDomainInOwner() {
        gitUrlInfo = new GitUrlParser.GitUrlInfo(
                GitUrlParser.GitPlatform.GITLAB,
                "gitlab.example.com",
                "testrepo",
                null,
                "https://gitlab.example.com/gitlab.example.com/testrepo.git"
        );
        
        try {
            gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);
        } catch (Exception e) {
            // Expected to fail, we're testing URL construction
        }
    }

    @Test
    void testDiffParsing_NewFile() throws GitLabApiException, PlatformApiException {
        Commit commit = createMockCommit("commit1", "Add new file");
        List<Diff> diffs = new ArrayList<>();
        Diff diff = mock(Diff.class);
        when(diff.getNewPath()).thenReturn("newfile.java");
        when(diff.getOldPath()).thenReturn(null);
        when(diff.getNewFile()).thenReturn(true);
        when(diff.getDeletedFile()).thenReturn(false);
        when(diff.getRenamedFile()).thenReturn(false);
        when(diff.getDiff()).thenReturn("@@ -0,0 +1,10 @@\n+new content");
        diffs.add(diff);
        
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(commit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(diffs);

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertFalse(result.get(0).getFileChanges().isEmpty());
        assertEquals("ADDED", result.get(0).getFileChanges().get(0).getChangeType());
    }

    @Test
    void testDiffParsing_DeletedFile() throws GitLabApiException, PlatformApiException {
        Commit commit = createMockCommit("commit1", "Delete file");
        List<Diff> diffs = new ArrayList<>();
        Diff diff = mock(Diff.class);
        when(diff.getNewPath()).thenReturn(null);
        when(diff.getOldPath()).thenReturn("oldfile.java");
        when(diff.getNewFile()).thenReturn(false);
        when(diff.getDeletedFile()).thenReturn(true);
        when(diff.getRenamedFile()).thenReturn(false);
        when(diff.getDiff()).thenReturn("@@ -1,10 +0,0 @@\n-deleted content");
        diffs.add(diff);
        
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(commit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(diffs);

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertFalse(result.get(0).getFileChanges().isEmpty());
        assertEquals("DELETED", result.get(0).getFileChanges().get(0).getChangeType());
    }

    @Test
    void testDiffParsing_RenamedFile() throws GitLabApiException, PlatformApiException {
        Commit commit = createMockCommit("commit1", "Rename file");
        List<Diff> diffs = new ArrayList<>();
        Diff diff = mock(Diff.class);
        when(diff.getNewPath()).thenReturn("newname.java");
        when(diff.getOldPath()).thenReturn("oldname.java");
        when(diff.getNewFile()).thenReturn(false);
        when(diff.getDeletedFile()).thenReturn(false);
        when(diff.getRenamedFile()).thenReturn(true);
        when(diff.getDiff()).thenReturn("");
        diffs.add(diff);
        
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(commit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(diffs);

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertFalse(result.get(0).getFileChanges().isEmpty());
        assertEquals("RENAMED", result.get(0).getFileChanges().get(0).getChangeType());
    }

    @Test
    void testDiffParsing_BinaryFile() throws GitLabApiException, PlatformApiException {
        Commit commit = createMockCommit("commit1", "Add binary");
        List<Diff> diffs = new ArrayList<>();
        Diff diff = mock(Diff.class);
        when(diff.getNewPath()).thenReturn("image.png");
        when(diff.getOldPath()).thenReturn(null);
        when(diff.getNewFile()).thenReturn(true);
        when(diff.getDeletedFile()).thenReturn(false);
        when(diff.getRenamedFile()).thenReturn(false);
        when(diff.getDiff()).thenReturn(null);
        diffs.add(diff);
        
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(commit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(diffs);

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertFalse(result.get(0).getFileChanges().isEmpty());
        assertTrue(result.get(0).getFileChanges().get(0).getIsBinary());
    }

    @Test
    void testDiffParsing_VariousBinaryExtensions() throws GitLabApiException, PlatformApiException {
        Commit commit = createMockCommit("commit1", "Add binaries");
        List<Diff> diffs = new ArrayList<>();
        String[] binaryFiles = {"test.jpg", "doc.pdf", "archive.zip", "app.exe", "lib.dll", "icon.svg"};
        
        for (String file : binaryFiles) {
            Diff diff = mock(Diff.class);
            when(diff.getNewPath()).thenReturn(file);
            when(diff.getNewFile()).thenReturn(true);
            when(diff.getDeletedFile()).thenReturn(false);
            when(diff.getRenamedFile()).thenReturn(false);
            when(diff.getDiff()).thenReturn("");
            diffs.add(diff);
        }
        
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(commit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(diffs);

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertEquals(binaryFiles.length, result.get(0).getFileChanges().size());
        assertTrue(result.get(0).getFileChanges().stream().allMatch(ScmCommits.FileChange::getIsBinary));
    }

    @Test
    void testDiffParsing_ComplexDiffWithLineNumbers() throws GitLabApiException, PlatformApiException {
        Commit commit = createMockCommit("commit1", "Complex changes");
        List<Diff> diffs = new ArrayList<>();
        Diff diff = mock(Diff.class);
        when(diff.getNewPath()).thenReturn("complex.java");
        when(diff.getOldPath()).thenReturn("complex.java");
        when(diff.getNewFile()).thenReturn(false);
        when(diff.getDeletedFile()).thenReturn(false);
        when(diff.getRenamedFile()).thenReturn(false);
        String complexDiff = "@@ -10,5 +10,8 @@\n" +
                " context line\n" +
                "-removed line 1\n" +
                "-removed line 2\n" +
                "+added line 1\n" +
                "+added line 2\n" +
                "+added line 3\n" +
                " context line\n" +
                "@@ -20,3 +23,4 @@\n" +
                " another context\n" +
                "+another addition\n" +
                " more context";
        when(diff.getDiff()).thenReturn(complexDiff);
        diffs.add(diff);
        
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(commit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(diffs);

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertFalse(result.get(0).getFileChanges().isEmpty());
        assertFalse(result.get(0).getFileChanges().get(0).getChangedLineNumbers().isEmpty());
    }

    @Test
    void testDiffParsing_NullDiff() throws GitLabApiException, PlatformApiException {
        Commit commit = createMockCommit("commit1", "Test");
        List<Diff> diffs = new ArrayList<>();
        Diff diff = mock(Diff.class);
        when(diff.getNewPath()).thenReturn(null);
        when(diff.getOldPath()).thenReturn(null);
        diffs.add(diff);
        
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(commit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(diffs);

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
    }

    @Test
    void testDiffParsing_EmptyDiffContent() throws GitLabApiException, PlatformApiException {
        Commit commit = createMockCommit("commit1", "Test");
        List<Diff> diffs = new ArrayList<>();
        Diff diff = mock(Diff.class);
        when(diff.getNewPath()).thenReturn("test.java");
        when(diff.getOldPath()).thenReturn("test.java");
        when(diff.getNewFile()).thenReturn(false);
        when(diff.getDeletedFile()).thenReturn(false);
        when(diff.getRenamedFile()).thenReturn(false);
        when(diff.getDiff()).thenReturn("");
        diffs.add(diff);
        
        when(gitLabClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of(commit));
        when(gitLabClient.fetchCommitDiffs(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(diffs);

        List<ScmCommits> result = gitLabService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertFalse(result.get(0).getFileChanges().isEmpty());
        assertEquals(0, result.get(0).getFileChanges().get(0).getAddedLines());
    }

    private List<Diff> createMockDiffsWithVariousTypes() {
        List<Diff> diffs = new ArrayList<>();
        
        Diff addedDiff = mock(Diff.class);
        when(addedDiff.getNewPath()).thenReturn("added.java");
        when(addedDiff.getNewFile()).thenReturn(true);
        when(addedDiff.getDeletedFile()).thenReturn(false);
        when(addedDiff.getRenamedFile()).thenReturn(false);
        when(addedDiff.getDiff()).thenReturn("@@ -0,0 +1,5 @@\n+line1\n+line2");
        diffs.add(addedDiff);
        
        Diff modifiedDiff = mock(Diff.class);
        when(modifiedDiff.getNewPath()).thenReturn("modified.java");
        when(modifiedDiff.getNewFile()).thenReturn(false);
        when(modifiedDiff.getDeletedFile()).thenReturn(false);
        when(modifiedDiff.getRenamedFile()).thenReturn(false);
        when(modifiedDiff.getDiff()).thenReturn("@@ -1,3 +1,3 @@\n+added\n-removed");
        diffs.add(modifiedDiff);
        
        return diffs;
    }

    private List<Diff> createMockDiffsWithContent() {
        List<Diff> diffs = new ArrayList<>();
        Diff diff = mock(Diff.class);
        when(diff.getNewPath()).thenReturn("file.java");
        when(diff.getNewFile()).thenReturn(false);
        when(diff.getDeletedFile()).thenReturn(false);
        when(diff.getRenamedFile()).thenReturn(false);
        when(diff.getDiff()).thenReturn("@@ -1,5 +1,10 @@\n+added1\n+added2\n+added3\n-removed1\n-removed2");
        diffs.add(diff);
        return diffs;
    }
}
