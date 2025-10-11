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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BitbucketClientFetchPullRequestsTest {

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
	void fetchPullRequests_shouldFetchCloudPullRequestsSuccessfully() throws Exception {
		String prResponse = """
				{
				    "values": [
				        {
				            "id": 1,
				            "title": "Test PR",
				            "description": "Test description",
				            "state": "OPEN",
				            "created_on": "2024-01-01T10:00:00+00:00",
				            "updated_on": "2024-01-02T10:00:00+00:00",
				            "author": {"display_name": "John Doe"},
				            "source": {"branch": {"name": "feature"}},
				            "destination": {"branch": {"name": "main"}},
				            "links": {"self": {"href": "https://api.bitbucket.org/2.0/repositories/owner/repo/pullrequests/1"}}
				        }
				    ],
				    "next": null
				}
				""";

		String activityResponse = """
				{
				    "values": []
				}
				""";

		doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(prResponse))
				.thenReturn(Mono.just(activityResponse));

		List<BitbucketClient.BitbucketPullRequest> prs = bitbucketClient.fetchPullRequests("owner", "repo", null,
				"user", "pass", null, "https://bitbucket.org/owner/repo.git");

		assertEquals(1, prs.size());
		assertEquals(1L, prs.get(0).getId());
		assertEquals("Test PR", prs.get(0).getTitle());
	}

	@Test
	void fetchPullRequests_shouldFetchServerPullRequestsSuccessfully() throws Exception {
		String prResponse = """
				{
				    "values": [
				        {
				            "id": 2,
				            "title": "Server PR",
				            "description": "Server description",
				            "state": "MERGED",
				            "createdDate": 1704103200000,
				            "updatedDate": 1704189600000,
				            "closedDate": 1704276000000,
				            "author": {"user": {"displayName": "Jane Doe"}},
				            "fromRef": {"displayId": "feature"},
				            "toRef": {"displayId": "main"},
				            "links": {"self": [{"href": "https://bitbucket.company.com/rest/api/1.0/projects/PRJ/repos/repo/pull-requests/2"}]}
				        }
				    ],
				    "isLastPage": true
				}
				""";

		String activityResponse = """
				{
				    "values": [
				        {"action": "COMMENTED", "createdDate": 1704103300000},
				        {"action": "APPROVED", "createdDate": 1704103400000}
				    ]
				}
				""";

		doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(prResponse))
				.thenReturn(Mono.just(activityResponse));

		List<BitbucketClient.BitbucketPullRequest> prs = bitbucketClient.fetchPullRequests("PRJ", "repo", null, "user",
				"pass", null, "https://bitbucket.company.com/scm/PRJ/repo.git");

		assertEquals(1, prs.size());
		assertEquals(2L, prs.get(0).getId());
		assertEquals("Server PR", prs.get(0).getTitle());
	}

	@Test
	void fetchPullRequests_shouldFilterByBranch() throws Exception {
		String prResponse = """
				{
				    "values": [
				        {
				            "id": 1,
				            "title": "PR to main",
				            "state": "OPEN",
				            "updated_on": "2024-01-01T10:00:00+00:00",
				            "destination": {"branch": {"name": "main"}},
				            "links": {"self": {"href": "https://api.bitbucket.org/2.0/repositories/owner/repo/pullrequests/1"}}
				        },
				        {
				            "id": 2,
				            "title": "PR to develop",
				            "state": "OPEN",
				            "updated_on": "2024-01-01T10:00:00+00:00",
				            "destination": {"branch": {"name": "develop"}},
				            "links": {"self": {"href": "https://api.bitbucket.org/2.0/repositories/owner/repo/pullrequests/2"}}
				        }
				    ],
				    "next": null
				}
				""";

		String activityResponse = """
				{
				    "values": []
				}
				""";

		doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(prResponse))
				.thenReturn(Mono.just(activityResponse));

		List<BitbucketClient.BitbucketPullRequest> prs = bitbucketClient.fetchPullRequests("owner", "repo", "main",
				"user", "pass", null, "https://bitbucket.org/owner/repo.git");

		assertEquals(1, prs.size());
		assertEquals("PR to main", prs.get(0).getTitle());
	}

	@Test
	void fetchPullRequests_shouldHandlePagination() throws Exception {
		String firstPage = """
				{
				    "values": [
				        {
				            "id": 1,
				            "title": "PR 1",
				            "state": "OPEN",
				            "updated_on": "2024-01-01T10:00:00+00:00",
				            "links": {"self": {"href": "https://api.bitbucket.org/2.0/repositories/owner/repo/pullrequests/1"}}
				        }
				    ],
				    "next": "https://api.bitbucket.org/2.0/repositories/owner/repo/pullrequests?page=2"
				}
				""";

		String secondPage = """
				{
				    "values": [
				        {
				            "id": 2,
				            "title": "PR 2",
				            "state": "OPEN",
				            "updated_on": "2024-01-01T10:00:00+00:00",
				            "links": {"self": {"href": "https://api.bitbucket.org/2.0/repositories/owner/repo/pullrequests/2"}}
				        }
				    ],
				    "next": null
				}
				""";

		String activityResponse = """
				{
				    "values": []
				}
				""";

		doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(firstPage))
				.thenReturn(Mono.just(activityResponse)).thenReturn(Mono.just(secondPage))
				.thenReturn(Mono.just(activityResponse));

		List<BitbucketClient.BitbucketPullRequest> prs = bitbucketClient.fetchPullRequests("owner", "repo", null,
				"user", "pass", null, "https://bitbucket.org/owner/repo.git");

		assertEquals(1, prs.size());
	}

	@Test
	void fetchPullRequests_shouldThrowExceptionOnJsonError() {
		String invalidJson = "{ invalid }";

		doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(invalidJson));

		assertThrows(PlatformApiException.class, () -> bitbucketClient.fetchPullRequests("owner", "repo", null, "user",
				"pass", null, "https://bitbucket.org/owner/repo.git"));
	}

	@Test
	void fetchLatestPullRequests_shouldFetchWithLimit() throws Exception {
		String response = """
				{
				    "values": [
				        {
				            "id": 1,
				            "title": "Latest PR",
				            "state": "OPEN",
				            "updated_on": "2024-01-01T10:00:00+00:00",
				            "destination": {"branch": {"name": "main"}}
				        }
				    ]
				}
				""";

		doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(response));

		List<BitbucketClient.BitbucketPullRequest> prs = bitbucketClient.fetchLatestPullRequests("owner", "repo", null,
				"user", "pass", 10, "https://bitbucket.org/owner/repo.git");

		assertEquals(1, prs.size());
		verify(requestHeadersUriSpec).uri(contains("pagelen=10"));
	}

	@Test
	void fetchLatestPullRequests_shouldRespectMaxLimit() throws Exception {
		String response = """
				{
				    "values": []
				}
				""";

		doNothing().when(rateLimitService).checkRateLimit(anyString(), anyString(), anyString(), anyString());
		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(response));

		bitbucketClient.fetchLatestPullRequests("owner", "repo", null, "user", "pass", 100,
				"https://bitbucket.org/owner/repo.git");

		verify(requestHeadersUriSpec).uri(contains("pagelen=50"));
	}
}
