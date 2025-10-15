package com.publicissapient.knowhow.processor.scm.service.platform.azuredevops;

import com.publicissapient.knowhow.processor.scm.client.azuredevops.AzureDevOpsClient;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.azd.common.types.Author;
import org.azd.enums.PullRequestStatus;
import org.azd.git.types.GitCommitRef;
import org.azd.git.types.GitPullRequest;
import org.azd.git.types.GitUserDate;
import org.bson.types.ObjectId;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class AzureDevOpsServiceTest {

    @Mock
    private AzureDevOpsClient azureDevOpsClient;

    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private AzureDevOpsService azureDevOpsService;

    private String toolConfigId;
    private GitUrlParser.GitUrlInfo gitUrlInfo;
    private String token;
    private LocalDateTime since;
    private LocalDateTime until;

    @BeforeEach
    void setUp() {
        toolConfigId = new ObjectId().toString();
        gitUrlInfo = new GitUrlParser.GitUrlInfo(
            GitUrlParser.GitPlatform.AZURE_DEVOPS, 
            "testOrg", 
            "testRepo", 
            "testOrg",
            "testProject",
            "https://dev.azure.com/testOrg/testProject/_git/testRepo"
        );
        token = "test-token";
        since = LocalDateTime.now().minusDays(7);
        until = LocalDateTime.now();
        
        ReflectionTestUtils.setField(azureDevOpsService, "azureDevOpsApiUrl", "https://dev.azure.com");
    }

    @Test
    void testFetchCommits_Success() throws Exception {
        List<GitCommitRef> azureCommits = createMockCommits();
        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(azureCommits);

        List<ScmCommits> result = azureDevOpsService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("commit1", result.get(0).getSha());
        assertEquals("Test commit 1", result.get(0).getCommitLog());
        assertEquals("testOrg/testProject/testRepo", result.get(0).getRepositoryName());
        verify(azureDevOpsClient).fetchCommits("testOrg", "testProject", "testRepo", "main", token, since, until);
    }

    @Test
    void testFetchCommits_WithNullProject() throws Exception {
        gitUrlInfo = new GitUrlParser.GitUrlInfo(GitUrlParser.GitPlatform.AZURE_DEVOPS, "testOrg", "testRepo", "testOrg", null, "https://dev.azure.com/testOrg/_git/testRepo");
        List<GitCommitRef> azureCommits = createMockCommits();
        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(azureCommits);

        List<ScmCommits> result = azureDevOpsService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(azureDevOpsClient).fetchCommits("testOrg", "testRepo", "testRepo", "main", token, since, until);
    }

    @Test
    void testFetchCommits_EmptyList() throws Exception {
        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(new ArrayList<>());

        List<ScmCommits> result = azureDevOpsService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchCommits_PartialConversionFailure() throws Exception {
        List<GitCommitRef> azureCommits = new ArrayList<>();
        GitCommitRef validCommit = createMockCommit("commit1", "Valid commit");
        GitCommitRef invalidCommit = mock(GitCommitRef.class);
        when(invalidCommit.getCommitId()).thenReturn("commit2");
        when(invalidCommit.getComment()).thenThrow(new RuntimeException("Conversion error"));
        azureCommits.add(validCommit);
        azureCommits.add(invalidCommit);
        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(azureCommits);

        List<ScmCommits> result = azureDevOpsService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertEquals("commit1", result.get(0).getSha());
    }

    @Test
    void testFetchCommits_Exception() throws Exception {
        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenThrow(new RuntimeException("API error"));

        PlatformApiException exception = assertThrows(PlatformApiException.class,
            () -> azureDevOpsService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until));
        assertEquals("Azure DevOps", exception.getPlatform());
        assertTrue(exception.getMessage().contains("Failed to fetch commits from Azure DevOps"));
    }

    @Test
    void testFetchMergeRequests_Success() throws Exception {
        List<GitPullRequest> azurePRs = createMockPullRequests();
        when(azureDevOpsClient.fetchPullRequests(eq("testOrg"), eq("testProject"), eq("testRepo"), eq(token), any(LocalDateTime.class), isNull()))
            .thenReturn(azurePRs);
        when(azureDevOpsClient.getPullRequestPickupTime(eq("testOrg"), eq("testProject"), eq("testRepo"), eq(token), any(GitPullRequest.class)))
            .thenReturn(1234567890L);

        List<ScmMergeRequests> result = azureDevOpsService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since, until);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("1", result.get(0).getExternalId());
        assertEquals("Test PR 1", result.get(0).getTitle());
    }

    @Test
    void testFetchMergeRequests_WithBranchFilter() throws Exception {
        List<GitPullRequest> azurePRs = createMockPullRequestsWithDifferentBranches();
        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
            .thenReturn(azurePRs);
        when(azureDevOpsClient.getPullRequestPickupTime(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(1234567890L);

        List<ScmMergeRequests> result = azureDevOpsService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertEquals("main", result.get(0).getToBranch());
    }

    @Test
    void testFetchMergeRequests_WithEmptyBranchFilter() throws Exception {
        List<GitPullRequest> azurePRs = createMockPullRequests();
        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
            .thenReturn(azurePRs);
        when(azureDevOpsClient.getPullRequestPickupTime(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(1234567890L);

        List<ScmMergeRequests> result = azureDevOpsService.fetchMergeRequests(toolConfigId, gitUrlInfo, "", token, since, until);

        assertEquals(2, result.size());
    }

    @Test
    void testFetchMergeRequests_Exception() throws Exception {
        when(azureDevOpsClient.fetchPullRequests(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
            .thenThrow(new RuntimeException("API error"));

        PlatformApiException exception = assertThrows(PlatformApiException.class,
            () -> azureDevOpsService.fetchMergeRequests(toolConfigId, gitUrlInfo, "main", token, since, until));
        assertEquals("Azure DevOps", exception.getPlatform());
        assertTrue(exception.getMessage().contains("Failed to fetch pull requests from Azure DevOps"));
    }

    @Test
    void testGetPlatformName() {
        String platformName = azureDevOpsService.getPlatformName();
        assertEquals("Azure DevOps", platformName);
    }

    @Test
    void testConvertToCommit_WithAuthor() throws Exception {
        GitCommitRef commit = createMockCommitWithAuthor();
        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(List.of(commit));

        List<ScmCommits> result = azureDevOpsService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        ScmCommits scmCommit = result.get(0);
        assertEquals("Test Author", scmCommit.getAuthor());
        assertEquals("test@example.com", scmCommit.getAuthorEmail());
        assertNotNull(scmCommit.getCommitTimestamp());
    }

    @Test
    void testConvertToCommit_WithoutAuthor() throws Exception {
        GitCommitRef commit = createMockCommit("commit1", "Test commit");
        when(azureDevOpsClient.fetchCommits(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(List.of(commit));

        List<ScmCommits> result = azureDevOpsService.fetchCommits(toolConfigId, gitUrlInfo, "main", token, since, until);

        assertEquals(1, result.size());
        assertNull(result.get(0).getCommitAuthor());
    }

    @Test
    void testConvertToMergeRequest_ActiveStatus() throws Exception {
        GitPullRequest pr = createMockPullRequest(1, "Active PR", PullRequestStatus.ACTIVE);
        when(azureDevOpsClient.fetchPullRequests(eq("testOrg"), eq("testProject"), eq("testRepo"), eq(token), any(LocalDateTime.class), isNull()))
            .thenReturn(List.of(pr));
        when(azureDevOpsClient.getPullRequestPickupTime(eq("testOrg"), eq("testProject"), eq("testRepo"), eq(token), any(GitPullRequest.class)))
            .thenReturn(0L);

        List<ScmMergeRequests> result = azureDevOpsService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since, until);

        assertEquals(1, result.size());
        assertEquals("open", result.get(0).getState());
        assertTrue(result.get(0).isOpen());
    }

    @Test
    void testConvertToMergeRequest_CompletedStatus() throws Exception {
        GitPullRequest pr = createMockPullRequestWithClosedDate(1, "Completed PR", PullRequestStatus.COMPLETED);
        when(azureDevOpsClient.fetchPullRequests(eq("testOrg"), eq("testProject"), eq("testRepo"), eq(token), any(LocalDateTime.class), isNull()))
            .thenReturn(List.of(pr));
        when(azureDevOpsClient.getPullRequestPickupTime(eq("testOrg"), eq("testProject"), eq("testRepo"), eq(token), any(GitPullRequest.class)))
            .thenReturn(1234567890L);

        List<ScmMergeRequests> result = azureDevOpsService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since, until);

        assertEquals(1, result.size());
        assertEquals("merged", result.get(0).getState());
        assertTrue(result.get(0).isClosed());
        assertNotNull(result.get(0).getMergedAt());
    }

    @Test
    void testConvertToMergeRequest_AbandonedStatus() throws Exception {
        GitPullRequest pr = createMockPullRequestWithClosedDate(1, "Abandoned PR", PullRequestStatus.ABANDONED);
        when(azureDevOpsClient.fetchPullRequests(eq("testOrg"), eq("testProject"), eq("testRepo"), eq(token), any(LocalDateTime.class), isNull()))
            .thenReturn(List.of(pr));
        when(azureDevOpsClient.getPullRequestPickupTime(eq("testOrg"), eq("testProject"), eq("testRepo"), eq(token), any(GitPullRequest.class)))
            .thenReturn(1234567890L);

        List<ScmMergeRequests> result = azureDevOpsService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since, until);

        assertEquals(1, result.size());
        assertEquals("closed", result.get(0).getState());
        assertTrue(result.get(0).isClosed());
    }

    @Test
    void testConvertToMergeRequest_NullStatus() throws Exception {
        GitPullRequest pr = createMockPullRequest(1, "PR with null status", null);
        when(azureDevOpsClient.fetchPullRequests(eq("testOrg"), eq("testProject"), eq("testRepo"), eq(token), any(LocalDateTime.class), isNull()))
            .thenReturn(List.of(pr));
        when(azureDevOpsClient.getPullRequestPickupTime(eq("testOrg"), eq("testProject"), eq("testRepo"), eq(token), any(GitPullRequest.class)))
            .thenReturn(0L);

        List<ScmMergeRequests> result = azureDevOpsService.fetchMergeRequests(toolConfigId, gitUrlInfo, null, token, since, until);

        assertEquals(1, result.size());
        assertNull(result.get(0).getState());
    }

    private List<GitCommitRef> createMockCommits() {
        List<GitCommitRef> commits = new ArrayList<>();
        commits.add(createMockCommit("commit1", "Test commit 1"));
        commits.add(createMockCommit("commit2", "Test commit 2"));
        return commits;
    }

    private GitCommitRef createMockCommit(String commitId, String comment) {
        GitCommitRef commit = mock(GitCommitRef.class);
        when(commit.getCommitId()).thenReturn(commitId);
        when(commit.getComment()).thenReturn(comment);
        when(commit.getUrl()).thenReturn("https://dev.azure.com/test/commit/" + commitId);
        return commit;
    }

    private GitCommitRef createMockCommitWithAuthor() {
        GitCommitRef commit = createMockCommit("commit1", "Test commit with author");
        GitUserDate author = mock(GitUserDate.class);
        when(author.getName()).thenReturn("Test Author");
        when(author.getEmail()).thenReturn("test@example.com");
        when(author.getDate()).thenReturn("2023-01-01T10:00:00Z");
        when(commit.getAuthor()).thenReturn(author);
        return commit;
    }

    private List<GitPullRequest> createMockPullRequests() {
        List<GitPullRequest> prs = new ArrayList<>();
        prs.add(createMockPullRequest(1, "Test PR 1", PullRequestStatus.ACTIVE));
        prs.add(createMockPullRequest(2, "Test PR 2", PullRequestStatus.ACTIVE));
        return prs;
    }

    private List<GitPullRequest> createMockPullRequestsWithDifferentBranches() {
        List<GitPullRequest> prs = new ArrayList<>();
        GitPullRequest mainPR = createMockPullRequest(1, "PR to main", PullRequestStatus.ACTIVE);
        when(mainPR.getTargetRefName()).thenReturn("refs/heads/main");
        prs.add(mainPR);
        GitPullRequest devPR = createMockPullRequest(2, "PR to dev", PullRequestStatus.ACTIVE);
        when(devPR.getTargetRefName()).thenReturn("refs/heads/dev");
        prs.add(devPR);
        return prs;
    }

    private GitPullRequest createMockPullRequest(int id, String title, PullRequestStatus status) {
        GitPullRequest pr = mock(GitPullRequest.class, withSettings().lenient());
        when(pr.getPullRequestId()).thenReturn(id);
        when(pr.getTitle()).thenReturn(title);
        when(pr.getDescription()).thenReturn("Test description");
        when(pr.getStatus()).thenReturn(status);
        when(pr.getCreationDate()).thenReturn("2023-01-01T10:00:00Z");
        when(pr.getUrl()).thenReturn("https://dev.azure.com/test/pr/" + id);
        when(pr.getSourceRefName()).thenReturn("refs/heads/feature");
        when(pr.getTargetRefName()).thenReturn("refs/heads/main");
        when(pr.getClosedDate()).thenReturn(null);
        
        Author createdBy = mock(Author.class, withSettings().lenient());
        when(createdBy.getDisplayName()).thenReturn("Test User");
        when(createdBy.getUniqueName()).thenReturn("testuser@example.com");
        when(pr.getCreatedBy()).thenReturn(createdBy);
        return pr;
    }

    private GitPullRequest createMockPullRequestWithClosedDate(int id, String title, PullRequestStatus status) {
        GitPullRequest pr = createMockPullRequest(id, title, status);
        when(pr.getClosedDate()).thenReturn("2023-01-02T10:00:00Z");
        return pr;
    }

    private GitPullRequest createMockPullRequestWithBranches() {
        GitPullRequest pr = createMockPullRequest(1, "PR with branches", PullRequestStatus.ACTIVE);
        when(pr.getSourceRefName()).thenReturn("refs/heads/feature-branch");
        when(pr.getTargetRefName()).thenReturn("refs/heads/main");
        return pr;
    }
}