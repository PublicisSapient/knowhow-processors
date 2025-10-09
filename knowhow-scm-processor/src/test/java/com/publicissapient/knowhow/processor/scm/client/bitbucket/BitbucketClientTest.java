package com.publicissapient.knowhow.processor.scm.client.bitbucket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BitbucketClientTest {

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
    }

    @Test
    void getBitbucketClient_shouldCreateWebClientWithBasicAuth() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        WebClient result = bitbucketClient.getBitbucketClient("user", "pass", "https://api.bitbucket.org/2.0");

        assertNotNull(result);
        verify(webClientBuilder).baseUrl("https://api.bitbucket.org/2.0");
        verify(webClientBuilder, atLeastOnce()).defaultHeader(eq(HttpHeaders.AUTHORIZATION), anyString());
        verify(webClientBuilder).defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        verify(webClientBuilder).defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void getBitbucketClientFromRepoUrl_shouldCreateWebClientForCloudUrl() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        WebClient result = bitbucketClient.getBitbucketClientFromRepoUrl("user", "pass", 
            "https://bitbucket.org/owner/repo.git");

        assertNotNull(result);
        verify(webClientBuilder).baseUrl("https://api.bitbucket.org/2.0");
    }

    @Test
    void getApiUrlFromRepoUrl_shouldReturnCloudApiUrlForBitbucketOrg() {
        String result = bitbucketClient.getApiUrlFromRepoUrl("https://bitbucket.org/owner/repo.git");
        assertEquals("https://api.bitbucket.org/2.0", result);
    }

    @Test
    void getApiUrlFromRepoUrl_shouldReturnServerApiUrlForOnPremise() {
        String result = bitbucketClient.getApiUrlFromRepoUrl("https://bitbucket.company.com/scm/project/repo.git");
        assertEquals("https://bitbucket.company.com/rest/api/1.0", result);
    }

    @Test
    void getApiUrlFromRepoUrl_shouldHandleProjectsFormat() {
        String result = bitbucketClient.getApiUrlFromRepoUrl("https://bitbucket.company.com/projects/project/repos/repo");
        assertEquals("https://bitbucket.company.com/rest/api/1.0", result);
    }

    @Test
    void getApiUrlFromRepoUrl_shouldReturnDefaultForNullUrl() {
        String result = bitbucketClient.getApiUrlFromRepoUrl(null);
        assertEquals("https://api.bitbucket.org/2.0", result);
    }

    @Test
    void getApiUrlFromRepoUrl_shouldReturnDefaultForEmptyUrl() {
        String result = bitbucketClient.getApiUrlFromRepoUrl("");
        assertEquals("https://api.bitbucket.org/2.0", result);
    }

    @Test
    void getApiUrl_shouldReturnDefaultApiUrl() {
        String result = bitbucketClient.getApiUrl();
        assertEquals("https://api.bitbucket.org/2.0", result);
    }

    @Test
    void testConnection_shouldReturnTrueForSuccessfulConnection() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{\"username\":\"test\"}"));

        boolean result = bitbucketClient.testConnection("user", "pass");

        assertTrue(result);
    }

    @Test
    void testConnection_shouldReturnFalseForFailedConnection() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenThrow(new RuntimeException("Connection failed"));

        boolean result = bitbucketClient.testConnection("user", "pass");

        assertFalse(result);
    }

    @Test
    void testConnection_shouldReturnFalseForEmptyResponse() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(""));

        boolean result = bitbucketClient.testConnection("user", "pass");

        assertFalse(result);
    }

    @Test
    void getBitbucketParser_shouldReturnCloudParserForBitbucketOrg() {
        var parser = bitbucketClient.getBitbucketParser(true);
        assertNotNull(parser);
        assertEquals("CloudBitBucketParser", parser.getClass().getSimpleName());
    }

    @Test
    void getBitbucketParser_shouldReturnServerParserForOnPremise() {
        var parser = bitbucketClient.getBitbucketParser(false);
        assertNotNull(parser);
        assertEquals("ServerBitbucketParser", parser.getClass().getSimpleName());
    }
}
