package com.publicissapient.knowhow.processor.scm.client.azuredevops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import org.azd.connection.Connection;
import org.azd.git.GitApi;
import org.azd.git.types.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AzureDevOpsClientTest {

    private static final String TEST_TOKEN = "test-token";
    private static final String TEST_ORG = "test-org";
    private static final String TEST_PROJECT = "test-project";
    private static final String TEST_REPO = "test-repo";
    private static final String TEST_BRANCH = "main";

    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;
    @Mock
    private ObjectMapper objectMapper;

    private AzureDevOpsClient azureDevOpsClient;

    @BeforeEach
    void setUp() {
        azureDevOpsClient = new AzureDevOpsClient(webClientBuilder, objectMapper);
        ReflectionTestUtils.setField(azureDevOpsClient, "azureDevOpsApiUrl", "https://dev.azure.com");
//        ReflectionTestUtils.setField(azureDevOpsClient, "maxMergeRequestsPerScan", 5000);
    }

    @Test
    void testGetAzureDevOpsConnection_Success() throws Exception {
        try (MockedConstruction<Connection> mockedConnection = mockConstruction(Connection.class)) {
            Connection connection = azureDevOpsClient.getAzureDevOpsConnection(TEST_TOKEN, TEST_ORG, TEST_PROJECT);
            assertNotNull(connection);
            assertEquals(1, mockedConnection.constructed().size());
        }
    }

    @Test
    void testGetAzureDevOpsConnection_NullToken_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> azureDevOpsClient.getAzureDevOpsConnection(null, TEST_ORG, TEST_PROJECT));
        assertEquals("Azure DevOps token cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGetAzureDevOpsConnection_EmptyToken_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> azureDevOpsClient.getAzureDevOpsConnection("  ", TEST_ORG, TEST_PROJECT));
        assertEquals("Azure DevOps token cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGetAzureDevOpsConnection_NullOrganization_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> azureDevOpsClient.getAzureDevOpsConnection(TEST_TOKEN, null, TEST_PROJECT));
        assertEquals("Azure DevOps organization cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGetAzureDevOpsConnection_EmptyOrganization_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> azureDevOpsClient.getAzureDevOpsConnection(TEST_TOKEN, "  ", TEST_PROJECT));
        assertEquals("Azure DevOps organization cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGetAzureDevOpsConnection_ConnectionFailure_ThrowsException() {
        try (MockedConstruction<Connection> mockedConnection = mockConstruction(Connection.class,
                (mock, context) -> { throw new RuntimeException("Connection failed"); })) {
            Exception exception = assertThrows(Exception.class,
                    () -> azureDevOpsClient.getAzureDevOpsConnection(TEST_TOKEN, TEST_ORG, TEST_PROJECT));
            assertTrue(exception.getMessage().contains("Azure DevOps authentication failed"));
        }
    }

    @Test
    void testGetRepository_Success() throws Exception {
        GitRepository mockRepo = mock(GitRepository.class);
        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getRepository(TEST_REPO)).thenReturn(mockRepo))) {
            GitRepository result = azureDevOpsClient.getRepository(TEST_ORG, TEST_PROJECT, TEST_REPO, TEST_TOKEN);
            assertNotNull(result);
            assertEquals(mockRepo, result);
        }
    }

    @Test
    void testGetRepository_Failure_ThrowsException() {
        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getRepository(TEST_REPO))
                             .thenThrow(new RuntimeException("Repository not found")))) {
            Exception exception = assertThrows(Exception.class,
                    () -> azureDevOpsClient.getRepository(TEST_ORG, TEST_PROJECT, TEST_REPO, TEST_TOKEN));
            assertTrue(exception.getMessage().contains("Failed to access repository"));
        }
    }

    @Test
    void testFetchCommits_Success_WithDateFiltering() throws Exception {
        LocalDateTime since = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime until = LocalDateTime.of(2024, 12, 31, 23, 59);

        GitCommitRef commit1 = createMockCommit("commit1", "2024-06-15T10:00:00Z");
        GitCommitRef commit2 = createMockCommit("commit2", "2024-06-16T10:00:00Z");

        GitCommitRefs firstBatch = mock(GitCommitRefs.class);
        lenient().when(firstBatch.getGitCommitRefs()).thenReturn(List.of(commit1, commit2));
        
        GitCommitRefs emptyBatch = mock(GitCommitRefs.class);
        lenient().when(emptyBatch.getGitCommitRefs()).thenReturn(new ArrayList<>());

        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getCommitsBatch(eq(TEST_REPO), any(GitCommitsBatch.class)))
                             .thenReturn(firstBatch, emptyBatch))) {
            List<GitCommitRef> result = azureDevOpsClient.fetchCommits(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    TEST_BRANCH, TEST_TOKEN, since, until);
            assertNotNull(result);
            assertEquals(2, result.size());
        }
    }

    @Test
    void testFetchCommits_WithNullDates() throws Exception {
        LocalDateTime since = LocalDateTime.of(2024, 1, 1, 0, 0);
        GitCommitRef commit1 = createMockCommit("commit1", "2024-06-15T10:00:00Z");

        GitCommitRefs firstBatch = mock(GitCommitRefs.class);
        lenient().when(firstBatch.getGitCommitRefs()).thenReturn(List.of(commit1));
        
        GitCommitRefs emptyBatch = mock(GitCommitRefs.class);
        lenient().when(emptyBatch.getGitCommitRefs()).thenReturn(new ArrayList<>());

        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getCommitsBatch(eq(TEST_REPO), any(GitCommitsBatch.class)))
                             .thenReturn(firstBatch, emptyBatch))) {
            List<GitCommitRef> result = azureDevOpsClient.fetchCommits(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    TEST_BRANCH, TEST_TOKEN, since, null);
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void testFetchCommits_WithCommitWithoutDate() throws Exception {
        LocalDateTime since = LocalDateTime.of(2024, 1, 1, 0, 0);
        GitCommitRef commitWithoutDate = mock(GitCommitRef.class);
        lenient().when(commitWithoutDate.getCommitter()).thenReturn(null);

        GitCommitRefs firstBatch = mock(GitCommitRefs.class);
        lenient().when(firstBatch.getGitCommitRefs()).thenReturn(List.of(commitWithoutDate));
        
        GitCommitRefs emptyBatch = mock(GitCommitRefs.class);
        lenient().when(emptyBatch.getGitCommitRefs()).thenReturn(new ArrayList<>());

        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getCommitsBatch(eq(TEST_REPO), any(GitCommitsBatch.class)))
                             .thenReturn(firstBatch, emptyBatch))) {
            List<GitCommitRef> result = azureDevOpsClient.fetchCommits(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    TEST_BRANCH, TEST_TOKEN, since, null);
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void testFetchCommits_WithInvalidDateFormat() throws Exception {
        LocalDateTime since = LocalDateTime.of(2024, 1, 1, 0, 0);
        GitCommitRef commitWithInvalidDate = createMockCommit("commit1", "invalid-date");

        GitCommitRefs firstBatch = mock(GitCommitRefs.class);
        lenient().when(firstBatch.getGitCommitRefs()).thenReturn(List.of(commitWithInvalidDate));
        
        GitCommitRefs emptyBatch = mock(GitCommitRefs.class);
        lenient().when(emptyBatch.getGitCommitRefs()).thenReturn(new ArrayList<>());

        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getCommitsBatch(eq(TEST_REPO), any(GitCommitsBatch.class)))
                             .thenReturn(firstBatch, emptyBatch))) {
            List<GitCommitRef> result = azureDevOpsClient.fetchCommits(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    TEST_BRANCH, TEST_TOKEN, since, null);
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void testFetchCommits_EmptyResult() throws Exception {
        LocalDateTime since = LocalDateTime.of(2024, 1, 1, 0, 0);
        GitCommitRefs emptyBatch = mock(GitCommitRefs.class);
        when(emptyBatch.getGitCommitRefs()).thenReturn(new ArrayList<>());

        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getCommitsBatch(eq(TEST_REPO), any(GitCommitsBatch.class)))
                             .thenReturn(emptyBatch))) {
            List<GitCommitRef> result = azureDevOpsClient.fetchCommits(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    TEST_BRANCH, TEST_TOKEN, since, null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void testFetchCommits_BatchException() throws Exception {
        LocalDateTime since = LocalDateTime.of(2024, 1, 1, 0, 0);
        GitCommitRef commit1 = createMockCommit("commit1", "2024-06-15T10:00:00Z");

        GitCommitRefs firstBatch = mock(GitCommitRefs.class);
        lenient().when(firstBatch.getGitCommitRefs()).thenReturn(List.of(commit1));

        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getCommitsBatch(eq(TEST_REPO), any(GitCommitsBatch.class)))
                             .thenReturn(firstBatch).thenThrow(new RuntimeException("Batch failed")))) {
            List<GitCommitRef> result = azureDevOpsClient.fetchCommits(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    TEST_BRANCH, TEST_TOKEN, since, null);
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void testFetchCommits_GeneralException_ThrowsException() {
        try (MockedConstruction<Connection> mockedConnection = mockConstruction(Connection.class,
                (mock, context) -> { throw new RuntimeException("Connection failed"); })) {
            LocalDateTime since = LocalDateTime.of(2024, 1, 1, 0, 0);
            Exception exception = assertThrows(Exception.class,
                    () -> azureDevOpsClient.fetchCommits(TEST_ORG, TEST_PROJECT, TEST_REPO,
                            TEST_BRANCH, TEST_TOKEN, since, null));
            assertTrue(exception.getMessage().contains("Azure DevOps authentication failed") || 
                      exception.getMessage().contains("Failed to fetch commits from Azure DevOps"));
        }
    }

    @Test
    void testFetchPullRequests_Success() throws Exception {
        LocalDateTime since = LocalDateTime.of(2024, 1, 1, 0, 0);
        GitPullRequest pr1 = createMockPullRequest(1, "2024-06-15T10:00:00Z");
        GitPullRequest pr2 = createMockPullRequest(2, "2024-06-16T10:00:00Z");

        PullRequests firstBatch = mock(PullRequests.class);
        lenient().when(firstBatch.getPullRequests()).thenReturn(List.of(pr1, pr2));
        
        PullRequests emptyBatch = mock(PullRequests.class);
        lenient().when(emptyBatch.getPullRequests()).thenReturn(new ArrayList<>());

        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getPullRequests(eq(TEST_REPO), any(GitPullRequestQueryParameters.class)))
                             .thenReturn(firstBatch, emptyBatch))) {
            List<GitPullRequest> result = azureDevOpsClient.fetchPullRequests(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    TEST_TOKEN, since, TEST_BRANCH);
            assertNotNull(result);
            assertEquals(2, result.size());
        }
    }

    @Test
    void testFetchPullRequests_WithNullSince() throws Exception {
        GitPullRequest pr1 = createMockPullRequest(1, "2024-06-15T10:00:00Z");

        PullRequests firstBatch = mock(PullRequests.class);
        lenient().when(firstBatch.getPullRequests()).thenReturn(List.of(pr1));
        
        PullRequests emptyBatch = mock(PullRequests.class);
        lenient().when(emptyBatch.getPullRequests()).thenReturn(new ArrayList<>());

        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getPullRequests(eq(TEST_REPO), any(GitPullRequestQueryParameters.class)))
                             .thenReturn(firstBatch, emptyBatch))) {
            List<GitPullRequest> result = azureDevOpsClient.fetchPullRequests(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    TEST_TOKEN, null, TEST_BRANCH);
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void testFetchPullRequests_WithNullCreationDate() throws Exception {
        LocalDateTime since = LocalDateTime.of(2024, 1, 1, 0, 0);
        GitPullRequest prWithoutDate = mock(GitPullRequest.class);
        lenient().when(prWithoutDate.getCreationDate()).thenReturn(null);

        PullRequests firstBatch = mock(PullRequests.class);
        lenient().when(firstBatch.getPullRequests()).thenReturn(List.of(prWithoutDate));
        
        PullRequests emptyBatch = mock(PullRequests.class);
        lenient().when(emptyBatch.getPullRequests()).thenReturn(new ArrayList<>());

        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getPullRequests(eq(TEST_REPO), any(GitPullRequestQueryParameters.class)))
                             .thenReturn(firstBatch, emptyBatch))) {
            List<GitPullRequest> result = azureDevOpsClient.fetchPullRequests(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    TEST_TOKEN, since, TEST_BRANCH);
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void testFetchPullRequests_WithInvalidDateFormat() throws Exception {
        LocalDateTime since = LocalDateTime.of(2024, 1, 1, 0, 0);
        GitPullRequest prWithInvalidDate = createMockPullRequest(1, "invalid-date");

        PullRequests firstBatch = mock(PullRequests.class);
        lenient().when(firstBatch.getPullRequests()).thenReturn(List.of(prWithInvalidDate));
        
        PullRequests emptyBatch = mock(PullRequests.class);
        lenient().when(emptyBatch.getPullRequests()).thenReturn(new ArrayList<>());

        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getPullRequests(eq(TEST_REPO), any(GitPullRequestQueryParameters.class)))
                             .thenReturn(firstBatch, emptyBatch))) {
            List<GitPullRequest> result = azureDevOpsClient.fetchPullRequests(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    TEST_TOKEN, since, TEST_BRANCH);
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void testFetchPullRequests_EmptyResult() throws Exception {
        PullRequests emptyBatch = mock(PullRequests.class);
        when(emptyBatch.getPullRequests()).thenReturn(new ArrayList<>());

        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getPullRequests(eq(TEST_REPO), any(GitPullRequestQueryParameters.class)))
                             .thenReturn(emptyBatch))) {
            List<GitPullRequest> result = azureDevOpsClient.fetchPullRequests(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    TEST_TOKEN, null, TEST_BRANCH);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void testFetchPullRequests_BatchException() throws Exception {
        GitPullRequest pr1 = createMockPullRequest(1, "2024-06-15T10:00:00Z");

        PullRequests firstBatch = mock(PullRequests.class);
        lenient().when(firstBatch.getPullRequests()).thenReturn(List.of(pr1));

        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> when(mock.getPullRequests(eq(TEST_REPO), any(GitPullRequestQueryParameters.class)))
                             .thenReturn(firstBatch).thenThrow(new RuntimeException("Batch failed")))) {
            List<GitPullRequest> result = azureDevOpsClient.fetchPullRequests(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    TEST_TOKEN, null, TEST_BRANCH);
            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    void testFetchPullRequests_GeneralException_ThrowsException() {
        try (MockedConstruction<Connection> mockedConnection = mockConstruction(Connection.class,
                (mock, context) -> { throw new RuntimeException("Connection failed"); })) {
            Exception exception = assertThrows(Exception.class,
                    () -> azureDevOpsClient.fetchPullRequests(TEST_ORG, TEST_PROJECT, TEST_REPO,
                            TEST_TOKEN, null, TEST_BRANCH));
            assertTrue(exception.getMessage().contains("Azure DevOps authentication failed") || 
                      exception.getMessage().contains("Failed to fetch pull requests from Azure DevOps"));
        }
    }

    @Test
    void testGetPullRequestPickupTime_Success() throws Exception {
        GitPullRequest pr = createMockPullRequest(123, "2024-06-15T10:00:00Z");
        String threadsJson = "{\"value\":[{\"comments\":[{\"publishedDate\":\"2024-06-15T12:00:00Z\"}]}]}";
        
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(threadsJson));

        JsonNode rootNode = new ObjectMapper().readTree(threadsJson);
        when(objectMapper.readTree(threadsJson)).thenReturn(rootNode);

        long result = azureDevOpsClient.getPullRequestPickupTime(TEST_ORG, TEST_PROJECT, TEST_REPO, TEST_TOKEN, pr);
        assertTrue(result > 0);
    }

    @Test
    void testGetPullRequestPickupTime_NullCreationDate() {
        GitPullRequest pr = mock(GitPullRequest.class);
        lenient().when(pr.getCreationDate()).thenReturn(null);
        long result = azureDevOpsClient.getPullRequestPickupTime(TEST_ORG, TEST_PROJECT, TEST_REPO, TEST_TOKEN, pr);
        assertEquals(0L, result);
    }

    @Test
    void testGetPullRequestPickupTime_EmptyCreationDate() {
        GitPullRequest pr = mock(GitPullRequest.class);
        lenient().when(pr.getCreationDate()).thenReturn("");
        long result = azureDevOpsClient.getPullRequestPickupTime(TEST_ORG, TEST_PROJECT, TEST_REPO, TEST_TOKEN, pr);
        assertEquals(0L, result);
    }

    @Test
    void testGetPullRequestPickupTime_NoThreads() throws Exception {
        GitPullRequest pr = createMockPullRequest(123, "2024-06-15T10:00:00Z");
        String emptyThreadsJson = "{\"value\":[]}";
        
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(emptyThreadsJson));

        JsonNode rootNode = new ObjectMapper().readTree(emptyThreadsJson);
        when(objectMapper.readTree(emptyThreadsJson)).thenReturn(rootNode);

        long result = azureDevOpsClient.getPullRequestPickupTime(TEST_ORG, TEST_PROJECT, TEST_REPO, TEST_TOKEN, pr);
        assertEquals(0L, result);
    }

    @Test
    void testGetPullRequestPickupTime_Exception() {
        GitPullRequest pr = createMockPullRequest(123, "2024-06-15T10:00:00Z");
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenThrow(new RuntimeException("WebClient error"));

        long result = azureDevOpsClient.getPullRequestPickupTime(TEST_ORG, TEST_PROJECT, TEST_REPO, TEST_TOKEN, pr);
        assertEquals(0L, result);
    }

    @Test
    void testGetCommitDiffStats_Success() throws Exception {
        GitCommitChanges commitChanges = mock(GitCommitChanges.class);
        GitCommit commit = mock(GitCommit.class);
        when(commitChanges.getChangeCounts()).thenReturn(null);
        try (MockedConstruction<Connection> ignored = mockConstruction(Connection.class);
             MockedConstruction<GitApi> mockedGitApi = mockConstruction(GitApi.class,
                     (mock, context) -> {
                         when(mock.getChanges(TEST_REPO, "commit123")).thenReturn(commitChanges);
                         when(mock.getCommit(TEST_REPO, "commit123")).thenReturn(commit);
                     })) {
            ScmCommits.FileChange result = azureDevOpsClient.getCommitDiffStats(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    "commit123", TEST_TOKEN);
            assertNotNull(result);
        }
    }

    @Test
    void testGetCommitDiffStats_Exception() {
        try (MockedConstruction<Connection> mockedConnection = mockConstruction(Connection.class,
                (mock, context) -> { throw new RuntimeException("Connection failed"); })) {
            ScmCommits.FileChange result = azureDevOpsClient.getCommitDiffStats(TEST_ORG, TEST_PROJECT, TEST_REPO,
                    "commit123", TEST_TOKEN);
            assertNotNull(result);
        }
    }

    @Test
    void testGetApiUrl() {
        String apiUrl = azureDevOpsClient.getApiUrl();
        assertEquals("https://dev.azure.com", apiUrl);
    }

    private GitCommitRef createMockCommit(String commitId, String date) {
        GitCommitRef commit = mock(GitCommitRef.class);
        GitUserDate committer = mock(GitUserDate.class);
        lenient().when(committer.getDate()).thenReturn(date);
        lenient().when(commit.getCommitter()).thenReturn(committer);
        lenient().when(commit.getCommitId()).thenReturn(commitId);
        return commit;
    }

    private GitPullRequest createMockPullRequest(int id, String creationDate) {
        GitPullRequest pr = mock(GitPullRequest.class);
        lenient().when(pr.getPullRequestId()).thenReturn(id);
        lenient().when(pr.getCreationDate()).thenReturn(creationDate);
        return pr;
    }
}
