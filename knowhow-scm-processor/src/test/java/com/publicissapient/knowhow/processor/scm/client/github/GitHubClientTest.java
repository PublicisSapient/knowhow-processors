package com.publicissapient.knowhow.processor.scm.client.github;

import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubClientTest {

    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private GitHubClient gitHubClient;

    private static final String TEST_TOKEN = "test-token";
    private static final String TEST_OWNER = "test-owner";
    private static final String TEST_REPO = "test-repo";
    private static final String TEST_BRANCH = "main";
    private static final String GITHUB_API_URL = "https://api.github.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gitHubClient, "githubApiUrl", GITHUB_API_URL);
    }

    @Test
    void testGetGitHubClient_WithNullToken_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            gitHubClient.getGitHubClient(null);
        });
        assertEquals("GitHub token cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGetGitHubClient_WithEmptyToken_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            gitHubClient.getGitHubClient("");
        });
        assertEquals("GitHub token cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGetGitHubClient_WithWhitespaceToken_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            gitHubClient.getGitHubClient("   ");
        });
        assertEquals("GitHub token cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGetRepository_Success() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GitHub mockGitHub = mock(GitHub.class);
        GHRepository mockRepo = mock(GHRepository.class);

        doReturn(mockGitHub).when(spyClient).getGitHubClient(TEST_TOKEN);
        when(mockGitHub.getRepository(TEST_OWNER + "/" + TEST_REPO)).thenReturn(mockRepo);

        GHRepository result = spyClient.getRepository(TEST_OWNER, TEST_REPO, TEST_TOKEN);

        assertNotNull(result);
        verify(mockGitHub).getRepository(TEST_OWNER + "/" + TEST_REPO);
    }

    @Test
    void testGetRepository_Failure() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GitHub mockGitHub = mock(GitHub.class);

        doReturn(mockGitHub).when(spyClient).getGitHubClient(TEST_TOKEN);
        when(mockGitHub.getRepository(TEST_OWNER + "/" + TEST_REPO))
                .thenThrow(new IOException("Repository not found"));

        IOException exception = assertThrows(IOException.class, () -> {
            spyClient.getRepository(TEST_OWNER, TEST_REPO, TEST_TOKEN);
        });

        assertTrue(exception.getMessage().contains("Failed to access repository"));
    }

    @Test
    void testGetApiUrl() {
        String apiUrl = gitHubClient.getApiUrl();
        
        assertNotNull(apiUrl);
        assertEquals(GITHUB_API_URL, apiUrl);
    }

    @Test
    void testTestConnection_Success() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GitHub mockGitHub = mock(GitHub.class);

        doReturn(mockGitHub).when(spyClient).getGitHubClient(TEST_TOKEN);
        doNothing().when(mockGitHub).checkApiUrlValidity();

        boolean result = spyClient.testConnection(TEST_TOKEN);

        assertTrue(result);
        verify(mockGitHub).checkApiUrlValidity();
    }

    @Test
    void testTestConnection_Failure() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GitHub mockGitHub = mock(GitHub.class);

        doReturn(mockGitHub).when(spyClient).getGitHubClient(TEST_TOKEN);
        doThrow(new IOException("Connection failed")).when(mockGitHub).checkApiUrlValidity();

        assertThrows(IOException.class, () -> {
            spyClient.testConnection(TEST_TOKEN);
        });
    }

    private GHCommit createMockCommit(LocalDateTime dateTime) throws IOException {
        GHCommit mockCommit = mock(GHCommit.class);
        Date date = Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        when(mockCommit.getCommitDate()).thenReturn(date);
        return mockCommit;
    }

    private GHPullRequest createMockPullRequest(LocalDateTime dateTime) throws IOException {
        GHPullRequest mockPR = mock(GHPullRequest.class);
        Date date = Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        when(mockPR.getUpdatedAt()).thenReturn(date);
        return mockPR;
    }

    @SuppressWarnings("unchecked")
    private <T> PagedIterable<T> createMockPagedIterable(List<T> items) {
        PagedIterable<T> pagedIterable = mock(PagedIterable.class);
        PagedIterator<T> pagedIterator = mock(PagedIterator.class);
        java.util.Iterator<T> iterator = items.iterator();
        doAnswer(inv -> iterator.hasNext()).when(pagedIterator).hasNext();
        doAnswer(inv -> iterator.next()).when(pagedIterator).next();
        when(pagedIterable.iterator()).thenReturn(pagedIterator);
        return pagedIterable;
    }

    @Test
    void testFetchCommits_WithBranch() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GHRepository mockRepo = mock(GHRepository.class);
        GHCommitQueryBuilder mockQueryBuilder = mock(GHCommitQueryBuilder.class);
        GHCommit commit = createMockCommit(LocalDateTime.now().minusDays(1));
        PagedIterable<GHCommit> pagedIterable = createMockPagedIterable(List.of(commit));
        doReturn(mockRepo).when(spyClient).getRepository(TEST_OWNER, TEST_REPO, TEST_TOKEN);
        when(mockRepo.queryCommits()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.from(TEST_BRANCH)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(pagedIterable);
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), isNull());
        List<GHCommit> result = spyClient.fetchCommits(TEST_OWNER, TEST_REPO, TEST_BRANCH, TEST_TOKEN, null, null);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchCommits_WithoutBranch() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GHRepository mockRepo = mock(GHRepository.class);
        GHCommitQueryBuilder mockQueryBuilder = mock(GHCommitQueryBuilder.class);
        GHCommit commit = createMockCommit(LocalDateTime.now().minusDays(1));
        PagedIterable<GHCommit> pagedIterable = createMockPagedIterable(List.of(commit));
        doReturn(mockRepo).when(spyClient).getRepository(TEST_OWNER, TEST_REPO, TEST_TOKEN);
        when(mockRepo.queryCommits()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(pagedIterable);
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), isNull());
        List<GHCommit> result = spyClient.fetchCommits(TEST_OWNER, TEST_REPO, null, TEST_TOKEN, null, null);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchCommits_WithDateFilter() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GHRepository mockRepo = mock(GHRepository.class);
        GHCommitQueryBuilder mockQueryBuilder = mock(GHCommitQueryBuilder.class);
        LocalDateTime now = LocalDateTime.now();
        GHCommit validCommit = createMockCommit(now.minusDays(3));
        GHCommit oldCommit = createMockCommit(now.minusDays(10));
        PagedIterable<GHCommit> pagedIterable = createMockPagedIterable(List.of(validCommit, oldCommit));
        doReturn(mockRepo).when(spyClient).getRepository(TEST_OWNER, TEST_REPO, TEST_TOKEN);
        when(mockRepo.queryCommits()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(pagedIterable);
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), isNull());
        List<GHCommit> result = spyClient.fetchCommits(TEST_OWNER, TEST_REPO, null, TEST_TOKEN, now.minusDays(7), now);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchCommits_NullDate() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GHRepository mockRepo = mock(GHRepository.class);
        GHCommitQueryBuilder mockQueryBuilder = mock(GHCommitQueryBuilder.class);
        GHCommit commit = mock(GHCommit.class);
        when(commit.getCommitDate()).thenReturn(null);
        PagedIterable<GHCommit> pagedIterable = createMockPagedIterable(List.of(commit));
        doReturn(mockRepo).when(spyClient).getRepository(TEST_OWNER, TEST_REPO, TEST_TOKEN);
        when(mockRepo.queryCommits()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(pagedIterable);
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), isNull());
        List<GHCommit> result = spyClient.fetchCommits(TEST_OWNER, TEST_REPO, null, TEST_TOKEN, null, null);
        assertEquals(0, result.size());
    }

    @Test
    void testFetchPullRequests() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GitHub mockGitHub = mock(GitHub.class);
        GHRepository mockRepo = mock(GHRepository.class);
        GHPullRequestQueryBuilder mockQueryBuilder = mock(GHPullRequestQueryBuilder.class);
        GHPullRequest pr = createMockPullRequest(LocalDateTime.now().minusDays(1));
        PagedIterable<GHPullRequest> pagedIterable = createMockPagedIterable(List.of(pr));
        doReturn(mockRepo).when(spyClient).getRepository(TEST_OWNER, TEST_REPO, TEST_TOKEN);
        when(mockRepo.queryPullRequests()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.state(GHIssueState.ALL)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.sort(GHPullRequestQueryBuilder.Sort.UPDATED)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.direction(GHDirection.DESC)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(pagedIterable);
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), isNull());
        List<GHPullRequest> result = spyClient.fetchPullRequests(TEST_OWNER, TEST_REPO, TEST_TOKEN, null, null);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchPullRequestsByState_Open() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GitHub mockGitHub = mock(GitHub.class);
        GHRepository mockRepo = mock(GHRepository.class);
        GHPullRequestQueryBuilder mockQueryBuilder = mock(GHPullRequestQueryBuilder.class);
        GHPullRequest pr = createMockPullRequest(LocalDateTime.now().minusDays(1));
        PagedIterable<GHPullRequest> pagedIterable = createMockPagedIterable(List.of(pr));
        doReturn(mockRepo).when(spyClient).getRepository(TEST_OWNER, TEST_REPO, TEST_TOKEN);
        when(mockRepo.queryPullRequests()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.state(GHIssueState.OPEN)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.sort(GHPullRequestQueryBuilder.Sort.UPDATED)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.direction(GHDirection.DESC)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(pagedIterable);
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), isNull());
        List<GHPullRequest> result = spyClient.fetchPullRequestsByState(TEST_OWNER, TEST_REPO, "open", TEST_TOKEN, null, null);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchPullRequestsByState_Closed() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GHRepository mockRepo = mock(GHRepository.class);
        GHPullRequestQueryBuilder mockQueryBuilder = mock(GHPullRequestQueryBuilder.class);
        GHPullRequest pr = createMockPullRequest(LocalDateTime.now().minusDays(1));
        PagedIterable<GHPullRequest> pagedIterable = createMockPagedIterable(List.of(pr));
        doReturn(mockRepo).when(spyClient).getRepository(TEST_OWNER, TEST_REPO, TEST_TOKEN);
        when(mockRepo.queryPullRequests()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.state(GHIssueState.CLOSED)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.sort(GHPullRequestQueryBuilder.Sort.UPDATED)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.direction(GHDirection.DESC)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(pagedIterable);
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), isNull());
        List<GHPullRequest> result = spyClient.fetchPullRequestsByState(TEST_OWNER, TEST_REPO, "closed", TEST_TOKEN, null, null);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchPullRequestsByState_NullState() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GitHub mockGitHub = mock(GitHub.class);
        GHRepository mockRepo = mock(GHRepository.class);
        GHPullRequestQueryBuilder mockQueryBuilder = mock(GHPullRequestQueryBuilder.class);
        GHPullRequest pr = createMockPullRequest(LocalDateTime.now().minusDays(1));
        PagedIterable<GHPullRequest> pagedIterable = createMockPagedIterable(List.of(pr));
        doReturn(mockRepo).when(spyClient).getRepository(TEST_OWNER, TEST_REPO, TEST_TOKEN);
        when(mockRepo.queryPullRequests()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.state(GHIssueState.ALL)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.sort(GHPullRequestQueryBuilder.Sort.UPDATED)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.direction(GHDirection.DESC)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(pagedIterable);
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), isNull());
        List<GHPullRequest> result = spyClient.fetchPullRequestsByState(TEST_OWNER, TEST_REPO, null, TEST_TOKEN, null, null);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchLatestPullRequests() throws IOException {
        GitHubClient spyClient = spy(gitHubClient);
        GHRepository mockRepo = mock(GHRepository.class);
        GHPullRequestQueryBuilder mockQueryBuilder = mock(GHPullRequestQueryBuilder.class);
        GHPullRequest pr1 = mock(GHPullRequest.class, withSettings().lenient());
        GHPullRequest pr2 = mock(GHPullRequest.class, withSettings().lenient());
        GHPullRequest pr3 = mock(GHPullRequest.class, withSettings().lenient());
        PagedIterable<GHPullRequest> pagedIterable = createMockPagedIterable(List.of(pr1, pr2, pr3));
        doReturn(mockRepo).when(spyClient).getRepository(TEST_OWNER, TEST_REPO, TEST_TOKEN);
        when(mockRepo.queryPullRequests()).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.state(GHIssueState.ALL)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.sort(GHPullRequestQueryBuilder.Sort.UPDATED)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.direction(GHDirection.DESC)).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.list()).thenReturn(pagedIterable);
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), isNull());
        List<GHPullRequest> result = spyClient.fetchLatestPullRequests(TEST_OWNER, TEST_REPO, TEST_TOKEN, 3);
        assertEquals(3, result.size());
    }
}
