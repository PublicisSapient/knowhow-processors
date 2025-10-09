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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BitbucketClientFetchCommitsTest {

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
    void fetchCommits_shouldFetchCloudCommitsSuccessfully() throws Exception {
        String cloudResponse = """
            {
                "values": [
                    {
                        "hash": "abc123",
                        "date": "2024-01-01T10:00:00+00:00",
                        "message": "Test commit",
                        "author": {
                            "type": "author",
                            "raw": "John Doe <john@example.com>",
                            "user": {
                                "display_name": "John Doe",
                                "nickname": "johndoe",
                                "uuid": "{uuid-123}",
                                "account_id": "acc123"
                            }
                        }
                    }
                ],
                "next": null
            }
            """;

        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(cloudResponse));

        List<BitbucketClient.BitbucketCommit> commits = bitbucketClient.fetchCommits(
            "owner", "repo", "main", "user", "pass", null, "https://bitbucket.org/owner/repo.git");

        assertEquals(1, commits.size());
        assertEquals("abc123", commits.get(0).getHash());
        assertEquals("Test commit", commits.get(0).getMessage());
        verify(rateLimitService).checkRateLimit("Bitbucket", "user:pass", "repo", "https://api.bitbucket.org/2.0");
    }

    @Test
    void fetchCommits_shouldFetchServerCommitsSuccessfully() throws Exception {
        String serverResponse = """
            {
                "values": [
                    {
                        "id": "def456",
                        "message": "Server commit",
                        "authorTimestamp": 1704103200000,
                        "author": {
                            "name": "Jane Doe",
                            "emailAddress": "jane@example.com",
                            "displayName": "Jane Doe",
                            "id": 123,
                            "slug": "janedoe",
                            "active": true
                        },
                        "parents": [{"id": "parent123"}]
                    }
                ],
                "isLastPage": true
            }
            """;

        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(serverResponse));

        List<BitbucketClient.BitbucketCommit> commits = bitbucketClient.fetchCommits(
            "PROJECT", "repo", "main", "user", "pass", null, "https://bitbucket.company.com/scm/PROJECT/repo.git");

        assertEquals(1, commits.size());
        assertEquals("def456", commits.get(0).getHash());
        assertEquals("Server commit", commits.get(0).getMessage());
    }

    @Test
    void fetchCommits_shouldHandlePaginationForCloud() throws Exception {
        String firstPage = """
            {
                "values": [{"hash": "commit1", "date": "2024-01-01T10:00:00+00:00", "message": "First"}],
                "next": "https://api.bitbucket.org/2.0/repositories/owner/repo/commits?page=2"
            }
            """;
        String secondPage = """
            {
                "values": [{"hash": "commit2", "date": "2024-01-02T10:00:00+00:00", "message": "Second"}],
                "next": null
            }
            """;

        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
            .thenReturn(Mono.just(firstPage))
            .thenReturn(Mono.just(secondPage));

        List<BitbucketClient.BitbucketCommit> commits = bitbucketClient.fetchCommits(
            "owner", "repo", null, "user", "pass", null, "https://bitbucket.org/owner/repo.git");

        assertEquals(2, commits.size());
        assertEquals("commit1", commits.get(0).getHash());
        assertEquals("commit2", commits.get(1).getHash());
    }

    @Test
    void fetchCommits_shouldHandlePaginationForServer() throws Exception {
        String firstPage = """
            {
                "values": [{"id": "commit1", "message": "First", "authorTimestamp": 1704103200000}],
                "isLastPage": false,
                "nextPageStart": 25
            }
            """;
        String secondPage = """
            {
                "values": [{"id": "commit2", "message": "Second", "authorTimestamp": 1704189600000}],
                "isLastPage": true
            }
            """;

        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
            .thenReturn(Mono.just(firstPage))
            .thenReturn(Mono.just(secondPage));

        List<BitbucketClient.BitbucketCommit> commits = bitbucketClient.fetchCommits(
            "PROJECT", "repo", "main", "user", "pass", null, "https://bitbucket.company.com/scm/PROJECT/repo.git");

        assertEquals(2, commits.size());
    }

    @Test
    void fetchCommits_shouldFilterByDateRange() throws Exception {
        String response = """
            {
                "values": [
                    {"hash": "commit1", "date": "2024-01-01T10:00:00+00:00", "message": "Old"},
                    {"hash": "commit2", "date": "2024-01-15T10:00:00+00:00", "message": "In range"},
                    {"hash": "commit3", "date": "2024-02-01T10:00:00+00:00", "message": "Too new"}
                ],
                "next": null
            }
            """;

        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(response));

        LocalDateTime since = LocalDateTime.of(2024, 1, 10, 0, 0);
        LocalDateTime until = LocalDateTime.of(2024, 1, 20, 0, 0);

        List<BitbucketClient.BitbucketCommit> commits = bitbucketClient.fetchCommits(
            "owner", "repo", null, "user", "pass", since, "https://bitbucket.org/owner/repo.git");

        assertEquals(2, commits.size());
        assertEquals("commit2", commits.get(0).getHash());
    }

    @Test
    void fetchCommits_shouldThrowPlatformApiExceptionOnJsonError() {
        String invalidJson = "{ invalid json }";

        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(invalidJson));

        assertThrows(PlatformApiException.class, () -> 
            bitbucketClient.fetchCommits("owner", "repo", null, "user", "pass", null,
                "https://bitbucket.org/owner/repo.git"));
    }

    @Test
    void fetchCommits_shouldHandleEmptyValues() throws Exception {
        String response = """
            {
                "values": [],
                "next": null
            }
            """;

        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(response));

        List<BitbucketClient.BitbucketCommit> commits = bitbucketClient.fetchCommits(
            "owner", "repo", null, "user", "pass", null, "https://bitbucket.org/owner/repo.git");

        assertEquals(0, commits.size());
    }

    @Test
    void fetchCommits_shouldIncludeBranchInUrl() throws Exception {
        String response = """
            {
                "values": [{"hash": "commit1", "date": "2024-01-01T10:00:00+00:00", "message": "Test"}],
                "next": null
            }
            """;

        doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(response));

        bitbucketClient.fetchCommits("owner", "repo", "develop", "user", "pass", null,
            "https://bitbucket.org/owner/repo.git");

        verify(requestHeadersUriSpec).uri(contains("include=develop"));
    }
}
