package com.publicissapient.knowhow.processor.scm.client.bitbucket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BitbucketClientDiffsAndErrorHandlingTest {

    @Mock
    private GitUrlParser gitUrlParser;

    @Mock
    private RateLimitService rateLimitService;

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

    private ObjectMapper objectMapper;
    private BitbucketClient bitbucketClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        bitbucketClient = new BitbucketClient(rateLimitService, objectMapper, webClientBuilder);
        ReflectionTestUtils.setField(bitbucketClient, "defaultBitbucketApiUrl", "https://api.bitbucket.org/2.0");
        
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
    }

    @Test
    void fetchCommitDiffs_shouldFetchCloudDiffsSuccessfully() throws Exception {
        String diffContent = "diff --git a/file.txt b/file.txt\n+added line";
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(diffContent.getBytes(StandardCharsets.UTF_8));

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.exchangeToMono(any())).thenAnswer(invocation -> {
            ClientResponse response = mock(ClientResponse.class);
            when(response.statusCode()).thenReturn(HttpStatus.OK);
            when(response.bodyToFlux(DataBuffer.class)).thenReturn(Flux.just(dataBuffer));
            return Mono.just(diffContent);
        });

        String result = bitbucketClient.fetchCommitDiffs("owner", "repo", "abc123", "user", "pass", 
            "https://bitbucket.org/owner/repo.git");

        assertNotNull(result);
        assertTrue(result.contains("diff --git"));
    }

    @Test
    void fetchCommitDiffs_shouldFetchServerDiffsSuccessfully() throws Exception {
        String diffContent = "diff --git a/file.txt b/file.txt\n+added line";
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(diffContent.getBytes(StandardCharsets.UTF_8));

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.exchangeToMono(any())).thenAnswer(invocation -> {
            ClientResponse response = mock(ClientResponse.class);
            when(response.statusCode()).thenReturn(HttpStatus.OK);
            when(response.bodyToFlux(DataBuffer.class)).thenReturn(Flux.just(dataBuffer));
            return Mono.just(diffContent);
        });

        String result = bitbucketClient.fetchCommitDiffs("PROJECT", "repo", "def456", "user", "pass", 
            "https://bitbucket.company.com/scm/PROJECT/repo.git");

        assertNotNull(result);
    }

    @Test
    void fetchCommitDiffs_shouldThrowExceptionOnError() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        
        WebClientResponseException exception = WebClientResponseException.create(
            HttpStatus.NOT_FOUND.value(), "Not found", null, null, null);
        when(requestHeadersSpec.exchangeToMono(any())).thenThrow(exception);

        assertThrows(PlatformApiException.class, () -> 
            bitbucketClient.fetchCommitDiffs("owner", "repo", "abc123", "user", "pass", 
                "https://bitbucket.org/owner/repo.git"));
    }

    @Test
    void fetchPullRequestDiffs_shouldFetchCloudDiffsSuccessfully() throws Exception {
        String diffContent = "diff --git a/file.txt b/file.txt\n+added line";
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(diffContent.getBytes(StandardCharsets.UTF_8));

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.exchangeToMono(any())).thenAnswer(invocation -> {
            ClientResponse response = mock(ClientResponse.class);
            when(response.statusCode()).thenReturn(HttpStatus.OK);
            when(response.bodyToFlux(DataBuffer.class)).thenReturn(Flux.just(dataBuffer));
            return Mono.just(diffContent);
        });

        String result = bitbucketClient.fetchPullRequestDiffs("owner", "repo", 1L, "user", "pass", 
            "https://bitbucket.org/owner/repo.git");

        assertNotNull(result);
    }

    @Test
    void fetchPullRequestDiffs_shouldFetchServerDiffsSuccessfully() throws Exception {
        String diffContent = "diff --git a/file.txt b/file.txt\n+added line";
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(diffContent.getBytes(StandardCharsets.UTF_8));

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.exchangeToMono(any())).thenAnswer(invocation -> {
            ClientResponse response = mock(ClientResponse.class);
            when(response.statusCode()).thenReturn(HttpStatus.OK);
            when(response.bodyToFlux(DataBuffer.class)).thenReturn(Flux.just(dataBuffer));
            return Mono.just(diffContent);
        });

        String result = bitbucketClient.fetchPullRequestDiffs("PROJECT", "repo", 2L, "user", "pass", 
            "https://bitbucket.company.com/scm/PROJECT/repo.git");

        assertNotNull(result);
    }

    @Test
    void fetchPullRequestActivity_shouldHandleCloudActivity() throws Exception {
        String activityResponse = """
            {
                "values": []
            }
            """;

        BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
        pr.setSelfLink("https://api.bitbucket.org/2.0/repositories/owner/repo/pullrequests/1");

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(activityResponse));

        assertDoesNotThrow(() -> bitbucketClient.fetchPullRequestActivity(webClient, pr));
    }

    @Test
    void fetchPullRequestActivity_shouldHandleServerActivity() throws Exception {
        String activityResponse = """
            {
                "values": [
                    {"action": "COMMENTED", "createdDate": 1704103200000},
                    {"action": "APPROVED", "createdDate": 1704103300000},
                    {"action": "merge", "createdDate": 1704103400000}
                ]
            }
            """;

        BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
        pr.setSelfLink("https://bitbucket.company.com/rest/api/1.0/projects/PRJ/repos/repo/pull-requests/1");

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(activityResponse));

        bitbucketClient.fetchPullRequestActivity(webClient, pr);

        assertNotNull(pr.getPickedUpOn());
    }

    @Test
    void fetchPullRequestActivity_shouldThrowExceptionOnError() {
        BitbucketClient.BitbucketPullRequest pr = new BitbucketClient.BitbucketPullRequest();
        pr.setSelfLink("https://api.bitbucket/2.0/repositories/owner/repo/pullrequests/1");

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        
        WebClientResponseException exception = WebClientResponseException.create(
            HttpStatus.NOT_FOUND.value(), "Not found", null, null, null);
        when(responseSpec.bodyToMono(String.class)).thenThrow(exception);

        assertThrows(PlatformApiException.class, () -> 
            bitbucketClient.fetchPullRequestActivity(webClient, pr));
    }

    @Test
    void fetchLatestPullRequests_shouldStopAtLimit() throws Exception {
        String response = """
            {
                "values": [
                    {"id": 1, "title": "PR 1", "state": "OPEN", "updated_on": "2024-01-01T10:00:00+00:00", "destination": {"branch": {"name": "main"}}},
                    {"id": 2, "title": "PR 2", "state": "OPEN", "updated_on": "2024-01-01T10:00:00+00:00", "destination": {"branch": {"name": "main"}}},
                    {"id": 3, "title": "PR 3", "state": "OPEN", "updated_on": "2024-01-01T10:00:00+00:00", "destination": {"branch": {"name": "main"}}}
                ]
            }
            """;

        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(response));

        List<BitbucketClient.BitbucketPullRequest> prs = bitbucketClient.fetchLatestPullRequests(
            "owner", "repo", null, "user", "pass", 2, "https://bitbucket.org/owner/repo.git");

        assertEquals(2, prs.size());
    }
}
