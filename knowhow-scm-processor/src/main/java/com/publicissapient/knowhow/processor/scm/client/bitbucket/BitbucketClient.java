/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.knowhow.processor.scm.client.bitbucket;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.knowhow.processor.scm.util.wrapper.BitbucketParser;
import com.publicissapient.knowhow.processor.scm.util.wrapper.impl.CloudBitBucketParser;
import com.publicissapient.knowhow.processor.scm.util.wrapper.impl.ServerBitbucketParser;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;

import reactor.core.publisher.Mono;

/**
 * Bitbucket API client for interacting with Bitbucket repositories. Supports
 * both Bitbucket Cloud (bitbucket.org) and Bitbucket Server (on-premise).
 */
@Component
@Slf4j
public class BitbucketClient {

	private static final String BITBUCKET_CLOUD_HOST = "bitbucket.org";
	private static final String BITBUCKET_CLOUD_API_URL = "https://api.bitbucket.org/2.0";
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 1024;
	private static final int RATE_LIMIT_WAIT_TIME_MS = 3600000;
	private static final long RATE_LIMIT_BUFFER_MS = 30000L;
	private static final int HTTP_TOO_MANY_REQUESTS = 429;
	private static final int DEFAULT_PAGE_LIMIT = 100;
	private static final int MAX_PULL_REQUESTS_LIMIT = 50;

	// CHANGE: Extract string literals as constants
	private static final String PLATFORM_NAME = "Bitbucket";
	private static final String HEADER_LOCATION = "Location";
	private static final String PARAM_NEXT_PAGE_START = "nextPageStart=";
	private static final String JSON_FIELD_VALUES = "values";
	private static final String JSON_FIELD_NEXT = "next";
	private static final String JSON_FIELD_IS_LAST_PAGE = "isLastPage";
	private static final String JSON_FIELD_NEXT_PAGE_START = "nextPageStart";

	// CHANGE: Extract activity event types
	private static final String EVENT_RESCOPED = "RESCOPED";
	private static final String EVENT_COMMENTED = "COMMENTED";
	private static final String EVENT_APPROVED = "APPROVED";
	private static final List<String> EVENT_TYPES_API_V1 = Arrays.asList(EVENT_RESCOPED, EVENT_COMMENTED,
			EVENT_APPROVED);

	@Value("${git-scanner.platforms.bitbucket.api-url:https://api.bitbucket.org/2.0}")
	private String defaultBitbucketApiUrl;

	private final RateLimitService rateLimitService;
	private final ObjectMapper objectMapper;
	private final WebClient.Builder webClientBuilder;

	public BitbucketClient(RateLimitService rateLimitService, ObjectMapper objectMapper,
			WebClient.Builder webClientBuilder) {
		this.rateLimitService = rateLimitService;
		this.objectMapper = objectMapper;
		this.webClientBuilder = webClientBuilder;
	}

	/**
	 * Creates a WebClient for Bitbucket API calls.
	 */
	public WebClient getBitbucketClient(String username, String appPassword, String apiBaseUrl) {
		String credentials = Base64.getEncoder().encodeToString((username + ":" + appPassword).getBytes());
		return webClientBuilder.baseUrl(apiBaseUrl).defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(DEFAULT_BUFFER_SIZE)).build();
	}

	/**
	 * Creates a WebClient from repository URL.
	 */
	public WebClient getBitbucketClientFromRepoUrl(String username, String appPassword, String repositoryUrl) {
		String apiBaseUrl = getApiUrlFromRepoUrl(repositoryUrl);
		return getBitbucketClient(username, appPassword, apiBaseUrl);
	}

	/**
	 * Fetches commits from a Bitbucket repository.
	 */
	public List<BitbucketCommit> fetchCommits(String owner, String repository, String branchName, String username,
			String appPassword, LocalDateTime since, String repositoryUrl) throws PlatformApiException {

		// CHANGE: Extract method for rate limit check
		checkRateLimit(username, appPassword, repository, repositoryUrl);

		WebClient client = getBitbucketClientFromRepoUrl(username, appPassword, repositoryUrl);
		List<BitbucketCommit> allCommits = new ArrayList<>();
		boolean isBitbucketCloud = isBitbucketCloud(repositoryUrl);

		// CHANGE: Extract URL building logic
		String initialUrl = buildCommitsUrl(owner, repository, branchName, since, isBitbucketCloud);

		// CHANGE: Extract pagination logic to reduce complexity
		fetchCommitsPaginated(client, initialUrl, allCommits, since, isBitbucketCloud, repository);

		log.info("Fetched {} commits from Bitbucket repository {}/{}", allCommits.size(), owner, repository);
		return allCommits;
	}

	// CHANGE: Extract method to reduce complexity
	private void checkRateLimit(String username, String appPassword, String repository, String repositoryUrl) {
		rateLimitService.checkRateLimit(PLATFORM_NAME, username + ":" + appPassword, repository,
				getApiUrlFromRepoUrl(repositoryUrl));
	}

	// CHANGE: Extract method to check if Bitbucket Cloud
	private boolean isBitbucketCloud(String repositoryUrl) {
		return repositoryUrl != null && repositoryUrl.contains(BITBUCKET_CLOUD_HOST);
	}

	// CHANGE: Extract URL building logic
	private String buildCommitsUrl(String owner, String repository, String branchName, LocalDateTime since,
			boolean isBitbucketCloud) {
		StringBuilder urlBuilder = new StringBuilder();

		if (isBitbucketCloud) {
			urlBuilder.append(String.format("/repositories/%s/%s/commits", owner, repository));
			appendCloudCommitParams(urlBuilder, branchName, since);
		} else {
			urlBuilder.append(String.format("/projects/%s/repos/%s/commits", owner, repository));
			appendServerCommitParams(urlBuilder, branchName);
		}

		return urlBuilder.toString();
	}

	// CHANGE: Extract parameter appending logic
	private void appendCloudCommitParams(StringBuilder urlBuilder, String branchName, LocalDateTime since) {
		boolean hasParams = false;

		if (branchName != null && !branchName.trim().isEmpty()) {
			urlBuilder.append("?include=").append(branchName);
			hasParams = true;
		}

		if (since != null) {
			urlBuilder.append(hasParams ? "&" : "?");
			urlBuilder.append("q=").append(URLEncoder
					.encode("date >= " + since.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), StandardCharsets.UTF_8));
		}
	}

	// CHANGE: Extract parameter appending logic
	private void appendServerCommitParams(StringBuilder urlBuilder, String branchName) {
		if (branchName != null && !branchName.trim().isEmpty()) {
			urlBuilder.append("?until=refs/heads/").append(branchName);
			urlBuilder.append("&limit=").append(DEFAULT_PAGE_LIMIT);
		}
	}

	// CHANGE: Extract pagination logic to separate method
	private void fetchCommitsPaginated(WebClient client, String initialUrl, List<BitbucketCommit> allCommits,
			LocalDateTime since, boolean isBitbucketCloud, String repository) throws PlatformApiException {
		String nextUrl = initialUrl;

		while (nextUrl != null) {
			try {
				log.debug("Fetching commits from: {}", nextUrl);
				String response = fetchFromUrl(client, nextUrl);

				BitbucketCommitsResponse commitsResponse = parseCommitsResponse(response, isBitbucketCloud);

				// CHANGE: Extract commit processing
				processCommits(commitsResponse, allCommits, since);

				// CHANGE: Extract next URL calculation
				nextUrl = calculateNextUrl(commitsResponse, nextUrl, isBitbucketCloud);

			} catch (WebClientResponseException e) {
				handleWebClientException(e, repository);
			} catch (JsonProcessingException e) {
				throw new PlatformApiException(PLATFORM_NAME, "Failed to parse Bitbucket response: " + e.getMessage(),
						e);
			} catch (Exception e) {
				throw new PlatformApiException(PLATFORM_NAME, "Unexpected error: " + e.getMessage(), e);
			}
		}
	}

	// CHANGE: Extract URL fetching logic
	private String fetchFromUrl(WebClient client, String url) {
		String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);
		return client.get().uri(decodedUrl).retrieve().bodyToMono(String.class).block();
	}

	// CHANGE: Extract commit processing logic
	private void processCommits(BitbucketCommitsResponse commitsResponse, List<BitbucketCommit> allCommits,
			LocalDateTime since) {
		if (commitsResponse.getValues() != null) {
			for (BitbucketCommit commit : commitsResponse.getValues()) {
				if (isCommitInDateRange(commit, since, LocalDateTime.now())) {
					allCommits.add(commit);
				}
			}
		}
	}

	// CHANGE: Extract next URL calculation logic
	private String calculateNextUrl(BitbucketCommitsResponse response, String currentUrl, boolean isBitbucketCloud) {
		String nextUrl = response.getNext();

		if (nextUrl == null) {
			return null;
		}

		if (isBitbucketCloud && nextUrl.startsWith("http")) {
			return nextUrl.substring(nextUrl.indexOf("/repositories"));
		} else if (!isBitbucketCloud && nextUrl.startsWith(PARAM_NEXT_PAGE_START)) {
			String pageStart = nextUrl.substring(PARAM_NEXT_PAGE_START.length());
			String baseUrl = currentUrl.contains("?") ? currentUrl.substring(0, currentUrl.indexOf("?")) : currentUrl;
			return baseUrl + "?start=" + pageStart;
		}

		return nextUrl;
	}

	// CHANGE: Consolidate exception handling
	private void handleWebClientException(WebClientResponseException e, String repository) throws PlatformApiException {
		log.error("Error fetching data from Bitbucket: {}", e.getMessage());

		if (e.getStatusCode().equals(HttpStatusCode.valueOf(HTTP_TOO_MANY_REQUESTS))) {
			handleRateLimitExceeded(repository);
		} else {
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch data from Bitbucket: " + e.getMessage(), e);
		}
	}

	// CHANGE: Extract rate limit handling
	private void handleRateLimitExceeded(String repository) {
		log.warn("=== RATE LIMIT THRESHOLD EXCEEDED ===");
		log.warn("Platform: {}", PLATFORM_NAME);
		log.warn("Repository: {}", Optional.ofNullable(repository).orElse("N/A"));

		long waitTimeSeconds = RATE_LIMIT_WAIT_TIME_MS / 1000;
		long waitTimeMinutes = waitTimeSeconds / 60;
		long waitTimeHours = waitTimeMinutes / 60;

		log.warn("Platform cooldown time: {} seconds ({} minutes, {} hours)", waitTimeSeconds, waitTimeMinutes,
				waitTimeHours);

		try {
			long totalWaitTime = RATE_LIMIT_WAIT_TIME_MS + RATE_LIMIT_BUFFER_MS;
			log.info("Sleeping for {} milliseconds (platform cooldown + 30s buffer)", totalWaitTime);
			Thread.sleep(totalWaitTime);
			log.info("RATE LIMIT SLEEP COMPLETED - Platform: {}, Repository: {}", PLATFORM_NAME, repository);
		} catch (InterruptedException e) {
			log.error("Thread interrupted while waiting for platform rate limit cooldown: {}", e.getMessage());
			log.error("RATE LIMIT SLEEP INTERRUPTED - Platform: {}, Repository: {}", PLATFORM_NAME, repository);
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Parses commits response from both Bitbucket Cloud and Server APIs
	 */
	private BitbucketCommitsResponse parseCommitsResponse(String response, boolean isBitbucketCloud)
			throws JsonProcessingException {
		JsonNode rootNode = objectMapper.readTree(response);
		BitbucketCommitsResponse commitsResponse = new BitbucketCommitsResponse();
		List<BitbucketCommit> commits = new ArrayList<>();

		JsonNode valuesNode = rootNode.get(JSON_FIELD_VALUES);
		if (valuesNode != null && valuesNode.isArray()) {
			BitbucketParser parser = getBitbucketParser(isBitbucketCloud);
			for (JsonNode commitNode : valuesNode) {
				BitbucketCommit commit = parser.parseCommitNode(commitNode, isBitbucketCloud);
				commits.add(commit);
			}
		}

		commitsResponse.setValues(commits);

		// CHANGE: Extract pagination parsing
		parsePaginationInfo(rootNode, commitsResponse, isBitbucketCloud);

		return commitsResponse;
	}

	// CHANGE: Extract pagination parsing logic
	private void parsePaginationInfo(JsonNode rootNode, BitbucketCommitsResponse response, boolean isBitbucketCloud) {
		if (isBitbucketCloud) {
			JsonNode nextNode = rootNode.get(JSON_FIELD_NEXT);
			if (nextNode != null && !nextNode.isNull()) {
				response.setNext(nextNode.asText());
			}
		} else {
			JsonNode isLastPageNode = rootNode.get(JSON_FIELD_IS_LAST_PAGE);
			JsonNode nextPageStartNode = rootNode.get(JSON_FIELD_NEXT_PAGE_START);
			if (isLastPageNode != null && !isLastPageNode.asBoolean() && nextPageStartNode != null) {
				response.setNext(PARAM_NEXT_PAGE_START + nextPageStartNode.asInt());
			}
		}
	}

	/**
	 * Fetches pull requests from a Bitbucket repository.
	 */
	public List<BitbucketPullRequest> fetchPullRequests(String owner, String repository, String branchName,
			String username, String appPassword, LocalDateTime since, String repositoryUrl)
			throws PlatformApiException {

		checkRateLimit(username, appPassword, repository, repositoryUrl);

		WebClient client = getBitbucketClientFromRepoUrl(username, appPassword, repositoryUrl);
		List<BitbucketPullRequest> allPullRequests = new ArrayList<>();
		boolean isBitbucketCloud = isBitbucketCloud(repositoryUrl);

		// CHANGE: Extract URL building
		String initialUrl = buildPullRequestsUrl(owner, repository, isBitbucketCloud);

		// CHANGE: Extract pagination logic
		fetchPullRequestsPaginated(client, initialUrl, allPullRequests, branchName, since, isBitbucketCloud,
				repository);

		log.info("Fetched {} pull requests from Bitbucket repository {}/{}", allPullRequests.size(), owner, repository);
		return allPullRequests;
	}

	// CHANGE: Extract URL building logic
	private String buildPullRequestsUrl(String owner, String repository, boolean isBitbucketCloud) {
		if (isBitbucketCloud) {
			return String.format("/repositories/%s/%s/pullrequests?state=ALL", owner, repository);
		} else {
			return String.format("/projects/%s/repos/%s/pull-requests?state=all", owner, repository);
		}
	}

	// CHANGE: Extract pagination logic for pull requests
	private void fetchPullRequestsPaginated(WebClient client, String initialUrl,
			List<BitbucketPullRequest> allPullRequests, String branchName, LocalDateTime since,
			boolean isBitbucketCloud, String repository) throws PlatformApiException {

		String nextUrl = initialUrl;

		while (nextUrl != null) {
			try {
				log.debug("Fetching pull requests from: {}", nextUrl);
				String response = fetchFromUrl(client, nextUrl);

				BitbucketPullRequestsResponse pullRequestsResponse = parsePullRequestsResponse(response,
						isBitbucketCloud);

				// CHANGE: Extract PR processing
				processPullRequests(client, pullRequestsResponse, allPullRequests, branchName, since);

				// CHANGE: Reuse next URL calculation logic
				nextUrl = calculateNextUrlForPullRequests(pullRequestsResponse, initialUrl, isBitbucketCloud);

			} catch (WebClientResponseException e) {
				handleWebClientException(e, repository);
			} catch (JsonProcessingException e) {
				throw new PlatformApiException(PLATFORM_NAME, "Failed to parse Bitbucket response: " + e.getMessage(),
						e);
			} catch (Exception e) {
				throw new PlatformApiException(PLATFORM_NAME, "Unexpected error: " + e.getMessage(), e);
			}
		}
	}

	// CHANGE: Extract PR processing logic
	private void processPullRequests(WebClient client, BitbucketPullRequestsResponse response,
			List<BitbucketPullRequest> allPullRequests, String branchName, LocalDateTime since) {

		if (response.getValues() == null) {
			return;
		}

		for (BitbucketPullRequest pr : response.getValues()) {
			if (shouldIncludePullRequest(pr, branchName, since)) {
				fetchPullRequestActivity(client, pr);
				allPullRequests.add(pr);
			}
		}
	}

	// CHANGE: Extract PR filtering logic
	private boolean shouldIncludePullRequest(BitbucketPullRequest pr, String branchName, LocalDateTime since) {
		// Check branch filter
		if (!matchesBranchFilter(pr, branchName)) {
			return false;
		}

		// Check date filter
		return isPullRequestInDateRange(pr, since, LocalDateTime.now());
	}

	// CHANGE: Extract branch matching logic
	private boolean matchesBranchFilter(BitbucketPullRequest pr, String branchName) {
		if (branchName == null || branchName.trim().isEmpty()) {
			return true;
		}

		return pr.getDestination() != null && pr.getDestination().getBranch() != null
				&& branchName.equals(pr.getDestination().getBranch().getName());
	}

	// CHANGE: Extract next URL calculation for PRs
	private String calculateNextUrlForPullRequests(BitbucketPullRequestsResponse response, String baseUrl,
			boolean isBitbucketCloud) {

		String nextUrl = response.getNext();

		if (nextUrl == null) {
			return null;
		}

		if (isBitbucketCloud && nextUrl.startsWith("http")) {
			return nextUrl.substring(nextUrl.indexOf("/repositories"));
		} else if (!isBitbucketCloud && nextUrl.startsWith(PARAM_NEXT_PAGE_START)) {
			String pageStart = nextUrl.substring(PARAM_NEXT_PAGE_START.length());
			return baseUrl + "&start=" + pageStart;
		}

		return nextUrl;
	}

	// CHANGE: Simplified and extracted activity fetching logic
	public void fetchPullRequestActivity(WebClient webClient, BitbucketPullRequest pullRequest) {
		String mrUrl = pullRequest.getSelfLink();

		try {
			boolean isBitbucketCloud = mrUrl.contains(BITBUCKET_CLOUD_HOST);

			if (isBitbucketCloud) {
				return;
			}

			String activityUrl = mrUrl + "/activities";
			fetchAndProcessActivities(webClient, activityUrl, pullRequest);

		} catch (WebClientResponseException e) {
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch pull request activities: " + e.getMessage(),
					e);
		} catch (JsonProcessingException e) {
			throw new PlatformApiException(PLATFORM_NAME, "Failed to parse activity response: " + e.getMessage(), e);
		} catch (Exception e) {
			throw new PlatformApiException(PLATFORM_NAME, "Unexpected error: " + e.getMessage(), e);
		}
	}

	// CHANGE: Extract activity processing logic
	private void fetchAndProcessActivities(WebClient webClient, String activityUrl, BitbucketPullRequest pullRequest)
			throws Exception {

		String nextUrl = activityUrl;

		while (nextUrl != null) {
			log.debug("Fetching activities from: {}", nextUrl);
			String response = fetchFromUrl(webClient, nextUrl);

			JsonNode jsonNode = objectMapper.readTree(response);
			JsonNode valuesNode = jsonNode.get(JSON_FIELD_VALUES);

			if (valuesNode != null) {
				processActivityNodes(valuesNode, pullRequest);
			}

			// Handle pagination for activities
			nextUrl = getNextActivityUrl(jsonNode, activityUrl);
		}
	}

	// CHANGE: Extract activity node processing
	private void processActivityNodes(JsonNode valuesNode, BitbucketPullRequest pullRequest) {
		long earliestActivityDate = Long.MAX_VALUE;

		for (JsonNode value : valuesNode) {
			String action = value.get("action").asText();
			long date = value.get("createdDate").asLong();

			if (EVENT_TYPES_API_V1.contains(action)) {
				earliestActivityDate = Math.min(earliestActivityDate, date);
			} else if (action.contains("merge")) {
				setPickedUpDate(pullRequest, date);
			}
		}

		if (earliestActivityDate != Long.MAX_VALUE) {
			setPickedUpDate(pullRequest, earliestActivityDate);
		}
	}

	// CHANGE: Extract date setting logic
	private void setPickedUpDate(BitbucketPullRequest pullRequest, Long timestamp) {
		LocalDateTime dateTime = LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC);
		pullRequest.setPickedUpOn(dateTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
	}

	// CHANGE: Extract next activity URL calculation
	private String getNextActivityUrl(JsonNode jsonNode, String baseUrl) {
		JsonNode nextPageStartNode = jsonNode.get(JSON_FIELD_NEXT_PAGE_START);

		if (nextPageStartNode != null && !jsonNode.get(JSON_FIELD_IS_LAST_PAGE).asBoolean()) {
			return baseUrl + "?start=" + nextPageStartNode.asInt();
		}

		return null;
	}

	/**
	 * Parses pull requests response from both Bitbucket Cloud and Server APIs
	 */
	private BitbucketPullRequestsResponse parsePullRequestsResponse(String response, boolean isBitbucketCloud)
			throws JsonProcessingException {
		JsonNode rootNode = objectMapper.readTree(response);
		BitbucketPullRequestsResponse pullRequestsResponse = new BitbucketPullRequestsResponse();
		List<BitbucketPullRequest> pullRequests = new ArrayList<>();

		JsonNode valuesNode = rootNode.get(JSON_FIELD_VALUES);
		if (valuesNode != null && valuesNode.isArray()) {
			BitbucketParser parser = getBitbucketParser(isBitbucketCloud);
			for (JsonNode prNode : valuesNode) {
				BitbucketPullRequest pr = parser.parsePullRequestNode(prNode);
				pullRequests.add(pr);
			}
		}

		pullRequestsResponse.setValues(pullRequests);

		// CHANGE: Reuse pagination parsing logic
		parsePullRequestPaginationInfo(rootNode, pullRequestsResponse, isBitbucketCloud);

		return pullRequestsResponse;
	}

	// CHANGE: Extract PR pagination parsing
	private void parsePullRequestPaginationInfo(JsonNode rootNode, BitbucketPullRequestsResponse response,
			boolean isBitbucketCloud) {
		if (isBitbucketCloud) {
			JsonNode nextNode = rootNode.get(JSON_FIELD_NEXT);
			if (nextNode != null && !nextNode.isNull()) {
				response.setNext(nextNode.asText());
			}
		} else {
			JsonNode isLastPageNode = rootNode.get(JSON_FIELD_IS_LAST_PAGE);
			JsonNode nextPageStartNode = rootNode.get(JSON_FIELD_NEXT_PAGE_START);
			if (isLastPageNode != null && !isLastPageNode.asBoolean() && nextPageStartNode != null) {
				response.setNext(PARAM_NEXT_PAGE_START + nextPageStartNode.asInt());
			}
		}
	}

	/**
	 * Fetches latest pull requests.
	 */
	public List<BitbucketPullRequest> fetchLatestPullRequests(String owner, String repository, String branchName,
			String username, String appPassword, int limit, String repositoryUrl) throws PlatformApiException {

		checkRateLimit(username, appPassword, repository, repositoryUrl);

		WebClient client = getBitbucketClientFromRepoUrl(username, appPassword, repositoryUrl);
		List<BitbucketPullRequest> pullRequests = new ArrayList<>();

		String url = String.format("/repositories/%s/%s/pullrequests?pagelen=%d", owner, repository,
				Math.min(limit, MAX_PULL_REQUESTS_LIMIT));

		try {
			log.debug("Fetching latest pull requests from: {}", url);
			String response = client.get().uri(url).retrieve().bodyToMono(String.class).block();

			BitbucketPullRequestsResponse pullRequestsResponse = objectMapper.readValue(response,
					BitbucketPullRequestsResponse.class);

			if (pullRequestsResponse.getValues() != null) {
				for (BitbucketPullRequest pr : pullRequestsResponse.getValues()) {
					if (matchesBranchFilter(pr, branchName)) {
						pullRequests.add(pr);
						if (pullRequests.size() >= limit) {
							break;
						}
					}
				}
			}

			log.info("Fetched {} latest pull requests from Bitbucket repository {}/{}", pullRequests.size(), owner,
					repository);
			return pullRequests;

		} catch (WebClientResponseException e) {
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch latest pull requests: " + e.getMessage(), e);
		} catch (JsonProcessingException e) {
			throw new PlatformApiException(PLATFORM_NAME, "Failed to parse response: " + e.getMessage(), e);
		} catch (Exception e) {
			throw new PlatformApiException(PLATFORM_NAME, "Unexpected error: " + e.getMessage(), e);
		}
	}

	/**
	 * Tests the connection to Bitbucket.
	 */
	public boolean testConnection(String username, String appPassword) {
		try {
			WebClient client = getBitbucketClient(username, appPassword, defaultBitbucketApiUrl);

			String response = client.get().uri("/user").retrieve().bodyToMono(String.class).block();

			return response != null && !response.trim().isEmpty();

		} catch (Exception e) {
			log.error("Failed to test Bitbucket connection: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Gets the API URL.
	 */
	public String getApiUrl() {
		return defaultBitbucketApiUrl;
	}

	/**
	 * Gets the API URL from repository URL.
	 */
	public String getApiUrlFromRepoUrl(String repositoryUrl) {
		if (repositoryUrl == null || repositoryUrl.trim().isEmpty()) {
			return defaultBitbucketApiUrl;
		}

		// Check if it's Bitbucket Cloud
		if (repositoryUrl.contains(BITBUCKET_CLOUD_HOST)) {
			return BITBUCKET_CLOUD_API_URL;
		}

		// For Bitbucket Server (on-premise), extract the base URL
		return extractServerApiUrl(repositoryUrl);
	}

	// CHANGE: Extract server API URL logic to reduce complexity
	private String extractServerApiUrl(String repositoryUrl) {
		try {
			String baseUrl = repositoryUrl;
			final String GIT_SUFFIX = ".git";

			if (baseUrl.contains("/scm/")) {
				baseUrl = baseUrl.substring(0, baseUrl.indexOf("/scm/"));
			} else if (baseUrl.contains("/projects/")) {
				baseUrl = baseUrl.substring(0, baseUrl.indexOf("/projects/"));
			}

			// Remove .git suffix if present
			if (baseUrl.endsWith(GIT_SUFFIX)) {
				baseUrl = baseUrl.substring(0, baseUrl.length() - GIT_SUFFIX.length());
			}

			// Remove trailing slash
			if (baseUrl.endsWith("/")) {
				baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
			}

            return baseUrl + "/rest/api/1.0";
		} catch (Exception e) {
			log.warn("Failed to extract API URL from repository URL: {}. Using default.", repositoryUrl);
			return defaultBitbucketApiUrl;
		}
	}

	/**
	 * Fetches commit diffs from Bitbucket.
	 */
	public String fetchCommitDiffs(String owner, String repository, String commitSha, String username,
			String appPassword, String repositoryUrl) throws PlatformApiException {
		try {
			WebClient client = getBitbucketClientFromRepoUrl(username, appPassword, repositoryUrl);
			boolean isBitbucketCloud = isBitbucketCloud(repositoryUrl);

			// CHANGE: Extract URL building
			String url = buildDiffUrl(owner, repository, commitSha, isBitbucketCloud, false);

			return executeWithRedirectHandling(client, url, username, appPassword);

		} catch (WebClientResponseException e) {
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch commit diffs: " + e.getMessage(), e);
		} catch (Exception e) {
			throw new PlatformApiException(PLATFORM_NAME, "Unexpected error: " + e.getMessage(), e);
		}
	}

	/**
	 * Fetches pull request diffs from Bitbucket.
	 */
	public String fetchPullRequestDiffs(String owner, String repository, Long pullRequestId, String username,
			String appPassword, String repositoryUrl) throws PlatformApiException {
		try {
			WebClient client = getBitbucketClientFromRepoUrl(username, appPassword, repositoryUrl);
			boolean isBitbucketCloud = isBitbucketCloud(repositoryUrl);

			// CHANGE: Extract URL building
			String url = buildDiffUrl(owner, repository, pullRequestId.toString(), isBitbucketCloud, true);

			return executeWithRedirectHandling(client, url, username, appPassword);

		} catch (WebClientResponseException e) {
			throw new PlatformApiException(PLATFORM_NAME, "Failed to fetch pull request diffs: " + e.getMessage(), e);
		} catch (Exception e) {
			throw new PlatformApiException(PLATFORM_NAME, "Unexpected error: " + e.getMessage(), e);
		}
	}

	// CHANGE: Extract diff URL building logic
	private String buildDiffUrl(String owner, String repository, String identifier, boolean isBitbucketCloud,
			boolean isPullRequest) {
		if (isBitbucketCloud) {
			if (isPullRequest) {
				return String.format("/repositories/%s/%s/pullrequests/%s/diff", owner, repository, identifier);
			} else {
				return String.format("/repositories/%s/%s/diff/%s", owner, repository, identifier);
			}
		} else {
			if (isPullRequest) {
				return String.format("/projects/%s/repos/%s/pull-requests/%s/diff", owner, repository, identifier);
			} else {
				return String.format("/projects/%s/repos/%s/commits/%s/diff", owner, repository, identifier);
			}
		}
	}

	// Helper methods

	private boolean isCommitInDateRange(BitbucketCommit commit, LocalDateTime since, LocalDateTime until) {
		if (commit.getDate() == null) {
			return true; // Include commits without date
		}

		try {
			LocalDateTime commitDate = parseCommitDate(commit.getDate());
			return isDateInRange(commitDate, since, until);
		} catch (Exception e) {
			log.warn("Failed to parse commit date: {}. Including commit in results.", commit.getDate());
			return true; // Include commits with unparseable dates
		}
	}

	// CHANGE: Extract date parsing logic
	private LocalDateTime parseCommitDate(String dateString) {
		// Handle both Bitbucket Cloud (ISO format) and Server (timestamp) formats
		if (dateString.contains("T")) {
			// Bitbucket Cloud format: ISO date-time
			return LocalDateTime.parse(dateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		} else {
			// Fallback: try to parse as timestamp if it's numeric
			try {
				long timestamp = Long.parseLong(dateString);
				return LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC);
			} catch (NumberFormatException e) {
				// If not numeric, try ISO format anyway
				return LocalDateTime.parse(dateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
			}
		}
	}

	private boolean isPullRequestInDateRange(BitbucketPullRequest pr, LocalDateTime since, LocalDateTime until) {
		if (pr.getUpdatedOn() == null) {
			return true; // Include PRs without date
		}

		try {
			LocalDateTime prDate = LocalDateTime.parse(pr.getUpdatedOn(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
			return isDateInRange(prDate, since, until);
		} catch (Exception e) {
			log.warn("Failed to parse pull request date: {}", pr.getUpdatedOn());
			return true; // Include PRs with unparseable dates
		}
	}

	// CHANGE: Extract date range checking logic
	private boolean isDateInRange(LocalDateTime date, LocalDateTime since, LocalDateTime until) {
		return (since == null || !date.isBefore(since)) && (until == null || !date.isAfter(until));
	}

	/**
	 * Executes a WebClient GET request with automatic redirect handling. This
	 * method handles HTTP 302 redirects by following the Location header.
	 */
	private String executeWithRedirectHandling(WebClient client, String uri, String username, String appPassword) {
		return client.get().uri(uri).exchangeToMono(response -> {
			if (response.statusCode().is3xxRedirection()) {
				return handleRedirect(response, username, appPassword);
			} else if (response.statusCode().is2xxSuccessful()) {
				return streamResponse(response);
			} else {
				log.error("Unexpected response status: {}", response.statusCode());
				return response.createException().flatMap(Mono::error);
			}
		}).block();
	}

	// CHANGE: Extract redirect handling logic
	private Mono<String> handleRedirect(org.springframework.web.reactive.function.client.ClientResponse response,
			String username, String appPassword) {
		String location = response.headers().header(HEADER_LOCATION).stream().findFirst().orElse(null);

		if (location == null) {
			log.warn("Redirect response received but no Location header found");
			return Mono.empty();
		}

		log.info("Following redirect to: {}", location);

		try {
			location = URLDecoder.decode(location, StandardCharsets.UTF_8).trim();
			if (!location.startsWith("http")) {
				log.warn("Invalid redirect URL: {}", location);
				return Mono.empty();
			}
		} catch (Exception e) {
			log.error("Failed to process redirect URL: {}", location, e);
			return Mono.empty();
		}

		// CHANGE: Create redirect client
		WebClient redirectClient = createRedirectClient(username, appPassword);

		return redirectClient.get().uri(location).retrieve().bodyToFlux(DataBuffer.class).map(this::dataBufferToString)
				.reduce(String::concat).onErrorResume(e -> {
					log.error("Error streaming redirect response: {}", e.getMessage());
					return Mono.empty();
				});
	}

	// CHANGE: Extract redirect client creation
	private WebClient createRedirectClient(String username, String appPassword) {
		String credentials = Base64.getEncoder().encodeToString((username + ":" + appPassword).getBytes());
		return webClientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE).build();
	}

	// CHANGE: Extract response streaming logic
	private Mono<String> streamResponse(org.springframework.web.reactive.function.client.ClientResponse response) {
		return response.bodyToFlux(DataBuffer.class).map(this::dataBufferToString).reduce(String::concat)
				.onErrorResume(e -> {
					log.error("Error streaming response: {}", e.getMessage());
					return Mono.empty();
				});
	}

	// CHANGE: Extract DataBuffer to String conversion
	private String dataBufferToString(DataBuffer dataBuffer) {
		byte[] bytes = new byte[dataBuffer.readableByteCount()];
		dataBuffer.read(bytes);
		DataBufferUtils.release(dataBuffer); // Release buffer
		return new String(bytes, StandardCharsets.UTF_8);
	}

	public BitbucketParser getBitbucketParser(Boolean isBitbucketCloud) {
		// CHANGE: Use ternary operator for simple conditional return
		return Boolean.TRUE.equals(isBitbucketCloud) ? new CloudBitBucketParser() : new ServerBitbucketParser();
	}

	// Data classes for Bitbucket API responses
	// CHANGE: Convert inner classes to use Lombok annotations to reduce boilerplate

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BitbucketCommitsResponse {
		private List<BitbucketCommit> values;
		private String next;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BitbucketPullRequestsResponse {
		private List<BitbucketPullRequest> values;
		private String next;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BitbucketCommit {
		private String hash;
		private String message;
		private String date;
		private BitbucketUser author;
		private BitbucketCommitStats stats;
		private List<String> parents;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BitbucketPullRequest {
		private Long id;
		private String title;
		private String description;
		private String state;
		@JsonProperty("created_on")
		private String createdOn;
		@JsonProperty("updated_on")
		private String updatedOn;
		private String closedOn;
		@JsonProperty("merge_commit")
		private BitbucketCommit mergeCommit;
		@JsonProperty("close_source_branch")
		private Boolean closeSourceBranch;
		private BitbucketUser author;
		private List<BitbucketUser> reviewers;
		private BitbucketBranch source;
		private BitbucketBranch destination;
		private String selfLink;
		private String pickedUpOn;
		private String mergedOn;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BitbucketUser {
		private String type;
		private BbUser user;
		@JsonProperty("display_name")
		private String displayName;
		private String uuid;
		@JsonProperty("account_id")
		private String accountId;
		private String nickname;

		// Additional fields for Bitbucket Server
		private String name;
		private String emailAddress;
		private Integer serverId;
		private String slug;
		private Boolean active;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BbUser {
		@JsonProperty("display_name")
		private String displayName;
		private String username;
		private String uuid;
		@JsonProperty("account_id")
		private String accountId;
		private String nickname;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BitbucketBranch {
		private BbBranch branch;
		private BitbucketCommit commit;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BbBranch {
		private String name;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BitbucketCommitStats {
		private Integer total;
		private Integer additions;
		private Integer deletions;
	}
}
