package com.publicissapient.knowhow.processor.scm.client.bitbucket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.wrapper.BitbucketParser;
import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BitbucketClientRepositoryTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private WebClient.Builder webClientBuilder;

    @InjectMocks
    private BitbucketClient bitbucketClient;

    private ObjectMapper realObjectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bitbucketClient, "defaultBitbucketApiUrl", "https://api.bitbucket.org/2.0");
        ReflectionTestUtils.setField(bitbucketClient, "objectMapper", realObjectMapper);
    }

    @Test
    void testGetNextBranchPageUrl_WithNullRootNode_ReturnsNull() throws Exception {
        String result = ReflectionTestUtils.invokeMethod(bitbucketClient, "getNextBranchPageUrl", (JsonNode) null);
        assertNull(result);
    }

    @Test
    void testGetNextBranchPageUrl_WithMissingNextNode_ReturnsNull() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree("{}");
        
        String result = ReflectionTestUtils.invokeMethod(bitbucketClient, "getNextBranchPageUrl", rootNode);
        assertNull(result);
    }

    @Test
    void testGetNextBranchPageUrl_WithNullNextNode_ReturnsNull() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree("{\"next\": null}");
        
        String result = ReflectionTestUtils.invokeMethod(bitbucketClient, "getNextBranchPageUrl", rootNode);
        assertNull(result);
    }

    @Test
    void testGetNextBranchPageUrl_WithEmptyNextUrl_ReturnsNull() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree("{\"next\": \"\"}");
        
        String result = ReflectionTestUtils.invokeMethod(bitbucketClient, "getNextBranchPageUrl", rootNode);
        assertNull(result);
    }

    @Test
    void testGetNextBranchPageUrl_WithValidHttpUrl_ReturnsNormalizedUrl() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree("{\"next\": \"https://api.bitbucket.org/2.0/repositories/owner/repo/refs/branches?page=2\"}");
        
        String result = ReflectionTestUtils.invokeMethod(bitbucketClient, "getNextBranchPageUrl", rootNode);
        assertEquals("/repositories/owner/repo/refs/branches?page=2", result);
    }

    @Test
    void testGetNextBranchPageUrl_WithRelativeUrl_ReturnsAsIs() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree("{\"next\": \"/repositories/owner/repo/refs/branches?page=2\"}");
        
        String result = ReflectionTestUtils.invokeMethod(bitbucketClient, "getNextBranchPageUrl", rootNode);
        assertEquals("/repositories/owner/repo/refs/branches?page=2", result);
    }

    @Test
    void testUpdateRepositoryLastUpdated_WithEmptyBranchList_NoUpdate() throws Exception {
        ScmRepos repo = ScmRepos.builder().build();
        repo.setBranchList(new ArrayList<>());
        repo.setLastUpdated(1000000L);
        
        ReflectionTestUtils.invokeMethod(bitbucketClient, "updateRepositoryLastUpdated", repo);
        
        assertEquals(1000000L, repo.getLastUpdated());
    }

    @Test
    void testUpdateRepositoryLastUpdated_WithSingleBranch_UpdatesToMostRecent() throws Exception {
        ScmRepos repo = ScmRepos.builder().build();
        repo.setLastUpdated(1000000L);
        
        ScmBranch branch = ScmBranch.builder().build();
        branch.setLastUpdatedAt(2000000L);
        
        List<ScmBranch> branches = new ArrayList<>();
        branches.add(branch);
        repo.setBranchList(branches);
        
        ReflectionTestUtils.invokeMethod(bitbucketClient, "updateRepositoryLastUpdated", repo);
        
        assertEquals(2000000L, repo.getLastUpdated());
    }

    @Test
    void testUpdateRepositoryLastUpdated_WithMultipleBranches_UpdatesToMostRecent() throws Exception {
        ScmRepos repo = ScmRepos.builder().build();
        repo.setLastUpdated(1000000L);
        
        ScmBranch branch1 = ScmBranch.builder().build();
        branch1.setLastUpdatedAt(2000000L);
        
        ScmBranch branch2 = ScmBranch.builder().build();
        branch2.setLastUpdatedAt(3000000L);
        
        ScmBranch branch3 = ScmBranch.builder().build();
        branch3.setLastUpdatedAt(2500000L);
        
        List<ScmBranch> branches = new ArrayList<>();
        branches.add(branch1);
        branches.add(branch2);
        branches.add(branch3);
        repo.setBranchList(branches);
        
        ReflectionTestUtils.invokeMethod(bitbucketClient, "updateRepositoryLastUpdated", repo);
        
        assertEquals(3000000L, repo.getLastUpdated());
    }

    @Test
    void testUpdateRepositoryLastUpdated_WithOlderBranches_UsesMaxBranchTime() throws Exception {
        ScmRepos repo = ScmRepos.builder().build();
        repo.setLastUpdated(1000000L);
        
        ScmBranch branch1 = ScmBranch.builder().build();
        branch1.setLastUpdatedAt(2000000L);
        
        ScmBranch branch2 = ScmBranch.builder().build();
        branch2.setLastUpdatedAt(3000000L);
        
        List<ScmBranch> branches = new ArrayList<>();
        branches.add(branch1);
        branches.add(branch2);
        repo.setBranchList(branches);
        
        ReflectionTestUtils.invokeMethod(bitbucketClient, "updateRepositoryLastUpdated", repo);
        
        assertEquals(3000000L, repo.getLastUpdated());
    }

    @Test
    void testFetchServerBranches_WithException_LogsError() throws Exception {
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class)).thenThrow(new RuntimeException("Connection error"));
        
        ScmRepos repo = ScmRepos.builder().build();
        repo.setRepositoryName("test-repo");
        repo.setBranchList(new ArrayList<>());
        
        ReflectionTestUtils.invokeMethod(bitbucketClient, "fetchServerBranches", 
            mockClient, repo, "TEST", LocalDateTime.now(), bitbucketClient.getBitbucketParser(false));
        
        assertTrue(repo.getBranchList().isEmpty());
    }

    @Test
    void testFetchServerBranches_Success() throws Exception {
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String branchResponse = "{\"values\":[{\"displayId\":\"main\",\"latestCommit\":\"abc123\"}],\"isLastPage\":true}";
        long recentTimestamp = System.currentTimeMillis();
        String commitResponse = "{\"authorTimestamp\":" + recentTimestamp + "}";
        
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class))
            .thenReturn(Mono.just(branchResponse))
            .thenReturn(Mono.just(commitResponse));
        
        ScmRepos repo = ScmRepos.builder().build();
        repo.setRepositoryName("test-repo");
        repo.setBranchList(new ArrayList<>());
        repo.setLastUpdated(1000000L);
        
        BitbucketParser parser = bitbucketClient.getBitbucketParser(false);
        ReflectionTestUtils.invokeMethod(bitbucketClient, "fetchServerBranches", 
            mockClient, repo, "TEST", LocalDateTime.now().minusDays(365), parser);
        
        assertFalse(repo.getBranchList().isEmpty());
    }

    @Test
    void testFetchRepositories_BitbucketCloud() throws Exception {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String repoResponse = "{\"values\":[{\"name\":\"test-repo\",\"updated_on\":\"2024-01-01T00:00:00Z\",\"links\":{\"html\":{\"href\":\"https://bitbucket.org/owner/test-repo\"}},\"owner\":{\"username\":\"owner\"}}],\"next\":null}";
        String branchResponse = "{\"values\":[{\"name\":\"main\",\"target\":{\"date\":\"2024-01-01T00:00:00Z\"}}],\"next\":null}";
        
        doReturn(mockClient).when(spyClient).getBitbucketClient(anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class))
            .thenReturn(Mono.just(repoResponse))
            .thenReturn(Mono.just(branchResponse));
        
        List<ScmRepos> result = spyClient.fetchRepositories(
            "https://bitbucket.org", "user", "pass", LocalDateTime.now().minusDays(30), new ObjectId());
        
        assertNotNull(result);
    }

    @Test
    void testFetchRepositories_BitbucketServer() throws Exception {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String projectResponse = "{\"values\":[{\"key\":\"TEST\",\"name\":\"Test Project\"}],\"isLastPage\":true}";
        String repoResponse = "{\"values\":[{\"name\":\"test-repo\",\"links\":{\"self\":[{\"href\":\"http://server/rest/api/1.0/projects/TEST/repos/test-repo\"}]}}],\"isLastPage\":true}";
        String branchResponse = "{\"values\":[],\"isLastPage\":true}";
        
        doReturn(mockClient).when(spyClient).getBitbucketClient(anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class))
            .thenReturn(Mono.just(projectResponse))
            .thenReturn(Mono.just(repoResponse))
            .thenReturn(Mono.just(branchResponse));
        
        List<ScmRepos> result = spyClient.fetchRepositories(
            "http://server", "user", "pass", LocalDateTime.now().minusDays(30), new ObjectId());
        
        assertNotNull(result);
    }

    @Test
    void testFetchRepositories_Exception() {
        BitbucketClient spyClient = spy(bitbucketClient);
        doThrow(new RuntimeException("Connection error")).when(spyClient).getBitbucketClient(anyString(), anyString(), anyString());
        
        assertThrows(PlatformApiException.class, () -> {
            spyClient.fetchRepositories("https://bitbucket.org", "user", "pass", LocalDateTime.now(), new ObjectId());
        });
    }

    @Test
    void testGetBitbucketClient() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(mock(WebClient.class));
        
        WebClient result = bitbucketClient.getBitbucketClient("user", "pass", "https://api.bitbucket.org/2.0");
        
        assertNotNull(result);
        verify(webClientBuilder).baseUrl("https://api.bitbucket.org/2.0");
    }

    @Test
    void testGetBitbucketClientFromRepoUrl_Cloud() {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        doReturn(mockClient).when(spyClient).getBitbucketClient(anyString(), anyString(), anyString());
        
        WebClient result = spyClient.getBitbucketClientFromRepoUrl("user", "pass", "https://bitbucket.org/owner/repo");
        
        assertNotNull(result);
    }

    @Test
    void testGetBitbucketClientFromRepoUrl_Server() {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        doReturn(mockClient).when(spyClient).getBitbucketClient(anyString(), anyString(), anyString());
        
        WebClient result = spyClient.getBitbucketClientFromRepoUrl("user", "pass", "http://server/scm/project/repo.git");
        
        assertNotNull(result);
    }

    @Test
    void testGetApiUrlFromRepoUrl_Cloud() {
        String result = bitbucketClient.getApiUrlFromRepoUrl("https://bitbucket.org/owner/repo");
        assertEquals("https://api.bitbucket.org/2.0", result);
    }

    @Test
    void testGetApiUrlFromRepoUrl_Server() {
        String result = bitbucketClient.getApiUrlFromRepoUrl("http://server/scm/project/repo.git");
        assertEquals("http://server/rest/api/1.0", result);
    }

    @Test
    void testGetApiUrlFromRepoUrl_Null() {
        String result = bitbucketClient.getApiUrlFromRepoUrl(null);
        assertEquals("https://api.bitbucket.org/2.0", result);
    }

    @Test
    void testGetApiUrlFromRepoUrl_Empty() {
        String result = bitbucketClient.getApiUrlFromRepoUrl("");
        assertEquals("https://api.bitbucket.org/2.0", result);
    }

    @Test
    void testGetApiUrl() {
        String result = bitbucketClient.getApiUrl();
        assertEquals("https://api.bitbucket.org/2.0", result);
    }

    @Test
    void testTestConnection_Success() {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        doReturn(mockClient).when(spyClient).getBitbucketClient(anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri("/user")).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{\"username\":\"test\"}"));
        
        boolean result = spyClient.testConnection("user", "pass");
        
        assertTrue(result);
    }

    @Test
    void testTestConnection_Failure() {
        BitbucketClient spyClient = spy(bitbucketClient);
        doThrow(new RuntimeException("Connection failed")).when(spyClient).getBitbucketClient(anyString(), anyString(), anyString());
        
        boolean result = spyClient.testConnection("user", "pass");
        
        assertFalse(result);
    }

    @Test
    void testGetBitbucketParser_Cloud() {
        BitbucketParser parser = bitbucketClient.getBitbucketParser(true);
        assertNotNull(parser);
    }

    @Test
    void testGetBitbucketParser_Server() {
        BitbucketParser parser = bitbucketClient.getBitbucketParser(false);
        assertNotNull(parser);
    }

    @Test
    void testNormalizeCloudApiUrl_WithHttp() throws Exception {
        String result = ReflectionTestUtils.invokeMethod(bitbucketClient, "normalizeCloudApiUrl", 
            "https://api.bitbucket.org/2.0/repositories/owner/repo");
        assertEquals("/repositories/owner/repo", result);
    }

    @Test
    void testNormalizeCloudApiUrl_WithoutHttp() throws Exception {
        String result = ReflectionTestUtils.invokeMethod(bitbucketClient, "normalizeCloudApiUrl", 
            "/repositories/owner/repo");
        assertEquals("/repositories/owner/repo", result);
    }

    @Test
    void testNormalizeCloudApiUrl_Null() throws Exception {
        String result = ReflectionTestUtils.invokeMethod(bitbucketClient, "normalizeCloudApiUrl", (String) null);
        assertNull(result);
    }

    @Test
    void testExtractServerApiUrl_WithScm() throws Exception {
        String result = ReflectionTestUtils.invokeMethod(bitbucketClient, "extractServerApiUrl", 
            "http://server/scm/project/repo.git");
        assertEquals("http://server/rest/api/1.0", result);
    }

    @Test
    void testExtractServerApiUrl_WithProjects() throws Exception {
        String result = ReflectionTestUtils.invokeMethod(bitbucketClient, "extractServerApiUrl", 
            "http://server/projects/TEST/repos/repo");
        assertEquals("http://server/rest/api/1.0", result);
    }

    @Test
    void testExtractServerApiUrl_WithTrailingSlash() throws Exception {
        String result = ReflectionTestUtils.invokeMethod(bitbucketClient, "extractServerApiUrl", 
            "http://server/scm/project/repo/");
        assertEquals("http://server/rest/api/1.0", result);
    }

    @Test
    void testFetchCommits_Cloud_Success() throws Exception {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String commitResponse = "{\"values\":[{\"hash\":\"abc123\",\"date\":\"2024-01-01T00:00:00Z\",\"message\":\"test commit\",\"author\":{\"raw\":\"Test User <test@example.com>\"}}],\"next\":null}";
        
        doReturn(mockClient).when(spyClient).getBitbucketClientFromRepoUrl(anyString(), anyString(), anyString());
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just(commitResponse));
        
        List<BitbucketClient.BitbucketCommit> result = spyClient.fetchCommits(
            "owner", "repo", "main", "user", "pass", LocalDateTime.of(2023, 1, 1, 0, 0), "https://bitbucket.org/owner/repo");
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("abc123", result.get(0).getHash());
    }

    @Test
    void testFetchCommits_Server_Success() throws Exception {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String commitResponse = "{\"values\":[{\"id\":\"abc123\",\"authorTimestamp\":1609459200000,\"message\":\"test commit\",\"author\":{\"name\":\"Test User\",\"emailAddress\":\"test@example.com\"}}],\"isLastPage\":true}";
        
        doReturn(mockClient).when(spyClient).getBitbucketClientFromRepoUrl(anyString(), anyString(), anyString());
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just(commitResponse));
        
        List<BitbucketClient.BitbucketCommit> result = spyClient.fetchCommits(
            "PROJECT", "repo", "main", "user", "pass", null, "http://server/scm/PROJECT/repo");
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchCommits_WithPagination() throws Exception {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String page1 = "{\"values\":[{\"hash\":\"abc123\",\"date\":\"2024-01-01T00:00:00Z\",\"message\":\"commit1\",\"author\":{\"raw\":\"User <user@example.com>\"}}],\"next\":\"https://api.bitbucket.org/2.0/repositories/owner/repo/commits?page=2\"}";
        String page2 = "{\"values\":[{\"hash\":\"def456\",\"date\":\"2024-01-02T00:00:00Z\",\"message\":\"commit2\",\"author\":{\"raw\":\"User <user@example.com>\"}}],\"next\":null}";
        
        doReturn(mockClient).when(spyClient).getBitbucketClientFromRepoUrl(anyString(), anyString(), anyString());
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class))
            .thenReturn(Mono.just(page1))
            .thenReturn(Mono.just(page2));
        
        List<BitbucketClient.BitbucketCommit> result = spyClient.fetchCommits(
            "owner", "repo", null, "user", "pass", null, "https://bitbucket.org/owner/repo");
        
        assertEquals(2, result.size());
    }

    @Test
    void testFetchCommits_WithDateFilter() throws Exception {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String commitResponse = "{\"values\":[{\"hash\":\"abc123\",\"date\":\"2024-01-15T00:00:00Z\",\"message\":\"recent\",\"author\":{\"raw\":\"User <user@example.com>\"}},{\"hash\":\"old123\",\"date\":\"2023-12-01T00:00:00Z\",\"message\":\"old\",\"author\":{\"raw\":\"User <user@example.com>\"}}],\"next\":null}";
        
        doReturn(mockClient).when(spyClient).getBitbucketClientFromRepoUrl(anyString(), anyString(), anyString());
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just(commitResponse));
        
        List<BitbucketClient.BitbucketCommit> result = spyClient.fetchCommits(
            "owner", "repo", null, "user", "pass", LocalDateTime.of(2024, 1, 1, 0, 0), "https://bitbucket.org/owner/repo");
        
        assertEquals(1, result.size());
        assertEquals("abc123", result.get(0).getHash());
    }

    @Test
    void testFetchCommits_JsonException() {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        doReturn(mockClient).when(spyClient).getBitbucketClientFromRepoUrl(anyString(), anyString(), anyString());
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just("invalid json"));
        
        assertThrows(PlatformApiException.class, () -> {
            spyClient.fetchCommits("owner", "repo", null, "user", "pass", null, "https://bitbucket.org/owner/repo");
        });
    }

    @Test
    void testFetchPullRequests_Cloud_Success() throws Exception {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String prResponse = "{\"values\":[{\"id\":1,\"title\":\"Test PR\",\"state\":\"OPEN\",\"created_on\":\"2024-01-01T00:00:00Z\",\"updated_on\":\"2024-01-02T00:00:00Z\",\"author\":{\"display_name\":\"Test User\"},\"source\":{\"branch\":{\"name\":\"feature\"}},\"destination\":{\"branch\":{\"name\":\"main\"}},\"links\":{\"self\":{\"href\":\"https://bitbucket.org/owner/repo/pull-requests/1\"}}}],\"next\":null}";
        
        doReturn(mockClient).when(spyClient).getBitbucketClientFromRepoUrl(anyString(), anyString(), anyString());
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just(prResponse));
        
        List<BitbucketClient.BitbucketPullRequest> result = spyClient.fetchPullRequests(
            "owner", "repo", "main", "user", "pass", LocalDateTime.of(2023, 1, 1, 0, 0), "https://bitbucket.org/owner/repo");
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test PR", result.get(0).getTitle());
    }

    @Test
    void testFetchPullRequests_Server_Success() throws Exception {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String prResponse = "{\"values\":[{\"id\":1,\"title\":\"Test PR\",\"state\":\"OPEN\",\"createdDate\":1609459200000,\"updatedDate\":1609545600000,\"author\":{\"user\":{\"name\":\"testuser\"}},\"fromRef\":{\"displayId\":\"feature\"},\"toRef\":{\"displayId\":\"main\"},\"links\":{\"self\":[{\"href\":\"http://server/projects/TEST/repos/repo/pull-requests/1\"}]}}],\"isLastPage\":true}";
        String activityResponse = "{\"values\":[{\"action\":\"MERGED\",\"createdDate\":1609545600000}],\"isLastPage\":true}";
        
        doReturn(mockClient).when(spyClient).getBitbucketClientFromRepoUrl(anyString(), anyString(), anyString());
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class))
            .thenReturn(Mono.just(prResponse))
            .thenReturn(Mono.just(activityResponse));
        
        List<BitbucketClient.BitbucketPullRequest> result = spyClient.fetchPullRequests(
            "TEST", "repo", "main", "user", "pass", null, "http://server/scm/TEST/repo");
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchPullRequests_WithBranchFilter() throws Exception {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String prResponse = "{\"values\":[{\"id\":1,\"title\":\"PR to main\",\"state\":\"OPEN\",\"created_on\":\"2024-01-01T00:00:00Z\",\"updated_on\":\"2024-01-02T00:00:00Z\",\"author\":{\"display_name\":\"User\"},\"source\":{\"branch\":{\"name\":\"feature\"}},\"destination\":{\"branch\":{\"name\":\"main\"}},\"links\":{\"self\":{\"href\":\"https://bitbucket.org/owner/repo/pull-requests/1\"}}},{\"id\":2,\"title\":\"PR to dev\",\"state\":\"OPEN\",\"created_on\":\"2024-01-01T00:00:00Z\",\"updated_on\":\"2024-01-02T00:00:00Z\",\"author\":{\"display_name\":\"User\"},\"source\":{\"branch\":{\"name\":\"feature2\"}},\"destination\":{\"branch\":{\"name\":\"dev\"}},\"links\":{\"self\":{\"href\":\"https://bitbucket.org/owner/repo/pull-requests/2\"}}}],\"next\":null}";
        
        doReturn(mockClient).when(spyClient).getBitbucketClientFromRepoUrl(anyString(), anyString(), anyString());
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just(prResponse));
        
        List<BitbucketClient.BitbucketPullRequest> result = spyClient.fetchPullRequests(
            "owner", "repo", "main", "user", "pass", null, "https://bitbucket.org/owner/repo");
        
        assertEquals(1, result.size());
        assertEquals("PR to main", result.get(0).getTitle());
    }

    @Test
    void testFetchPullRequests_WithPagination() throws Exception {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String page1 = "{\"values\":[{\"id\":1,\"title\":\"PR1\",\"state\":\"OPEN\",\"created_on\":\"2024-01-01T00:00:00Z\",\"updated_on\":\"2024-01-02T00:00:00Z\",\"author\":{\"display_name\":\"User\"},\"source\":{\"branch\":{\"name\":\"f1\"}},\"destination\":{\"branch\":{\"name\":\"main\"}},\"links\":{\"self\":{\"href\":\"https://bitbucket.org/owner/repo/pull-requests/1\"}}}],\"next\":\"https://api.bitbucket.org/2.0/repositories/owner/repo/pullrequests?page=2\"}";
        String page2 = "{\"values\":[{\"id\":2,\"title\":\"PR2\",\"state\":\"OPEN\",\"created_on\":\"2024-01-01T00:00:00Z\",\"updated_on\":\"2024-01-02T00:00:00Z\",\"author\":{\"display_name\":\"User\"},\"source\":{\"branch\":{\"name\":\"f2\"}},\"destination\":{\"branch\":{\"name\":\"main\"}},\"links\":{\"self\":{\"href\":\"https://bitbucket.org/owner/repo/pull-requests/2\"}}}],\"next\":null}";
        
        doReturn(mockClient).when(spyClient).getBitbucketClientFromRepoUrl(anyString(), anyString(), anyString());
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class))
            .thenReturn(Mono.just(page1))
            .thenReturn(Mono.just(page2));
        
        List<BitbucketClient.BitbucketPullRequest> result = spyClient.fetchPullRequests(
            "owner", "repo", null, "user", "pass", null, "https://bitbucket.org/owner/repo");
        
        assertEquals(2, result.size());
    }

    @Test
    void testFetchLatestPullRequests_Success() throws Exception {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        String prResponse = "{\"values\":[{\"id\":1,\"title\":\"Latest PR\",\"state\":\"OPEN\",\"created_on\":\"2024-01-01T00:00:00Z\",\"updated_on\":\"2024-01-02T00:00:00Z\",\"author\":{\"display_name\":\"User\"},\"source\":{\"branch\":{\"name\":\"feature\"}},\"destination\":{\"branch\":{\"name\":\"main\"}},\"links\":{\"self\":{\"href\":\"https://bitbucket.org/owner/repo/pull-requests/1\"}}}]}";
        
        doReturn(mockClient).when(spyClient).getBitbucketClientFromRepoUrl(anyString(), anyString(), anyString());
        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(mockClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just(prResponse));
        
        List<BitbucketClient.BitbucketPullRequest> result = spyClient.fetchLatestPullRequests(
            "owner", "repo", null, "user", "pass", 10, "https://bitbucket.org/owner/repo");
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testFetchCommitDiffs_Exception() {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        
        doReturn(mockClient).when(spyClient).getBitbucketClientFromRepoUrl(anyString(), anyString(), anyString());
        doThrow(new RuntimeException("Diff error")).when(mockClient).get();
        
        assertThrows(PlatformApiException.class, () -> {
            spyClient.fetchCommitDiffs("owner", "repo", "abc123", "user", "pass", "https://bitbucket.org/owner/repo");
        });
    }

    @Test
    void testFetchPullRequestDiffs_Exception() {
        BitbucketClient spyClient = spy(bitbucketClient);
        WebClient mockClient = mock(WebClient.class);
        
        doReturn(mockClient).when(spyClient).getBitbucketClientFromRepoUrl(anyString(), anyString(), anyString());
        doThrow(new RuntimeException("Diff error")).when(mockClient).get();
        
        assertThrows(PlatformApiException.class, () -> {
            spyClient.fetchPullRequestDiffs("owner", "repo", 1L, "user", "pass", "https://bitbucket.org/owner/repo");
        });
    }
}
