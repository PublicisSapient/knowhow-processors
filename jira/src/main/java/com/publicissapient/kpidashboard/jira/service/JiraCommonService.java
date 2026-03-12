/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.publicissapient.kpidashboard.jira.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.publicissapient.kpidashboard.common.client.KerberosClient;
import com.publicissapient.kpidashboard.common.exceptions.ClientErrorMessageEnum;
import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.model.ToolCredential;
import com.publicissapient.kpidashboard.common.model.application.ErrorDetail;
import com.publicissapient.kpidashboard.common.model.application.ProjectVersion;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.common.processortool.service.ProcessorToolConnectionService;
import com.publicissapient.kpidashboard.common.repository.tracelog.ProcessorExecutionTraceLogRepository;
import com.publicissapient.kpidashboard.common.service.AesEncryptionService;
import com.publicissapient.kpidashboard.common.service.ToolCredentialProvider;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import com.publicissapient.kpidashboard.jira.client.ProcessorJiraRestClient;
import com.publicissapient.kpidashboard.jira.config.JiraProcessorConfig;
import com.publicissapient.kpidashboard.jira.constant.JiraConstants;
import com.publicissapient.kpidashboard.jira.helper.JiraHelper;
import com.publicissapient.kpidashboard.jira.model.JiraSearchResponse;
import com.publicissapient.kpidashboard.jira.model.JiraToolConfig;
import com.publicissapient.kpidashboard.jira.model.ProjectConfFieldMapping;

import io.atlassian.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JiraCommonService {

	public static final String PROCESSING_ISSUES_PRINT_LOG = "Processing issues %d - %d out of %d";
	private static final String MSG_JIRA_CLIENT_SETUP_FAILED =
			"Jira client setup failed. No results obtained. Check your jira setup.";

	// Cache for API v3 pagination tokens - key format: projectKey_queryHash
	private final Map<String, String> advancedJqlNextPageTokenCache = new ConcurrentHashMap<>();

	@Autowired private JiraProcessorConfig jiraProcessorConfig;

	private ProcessorJiraRestClient client;

	@Autowired private ToolCredentialProvider toolCredentialProvider;

	@Autowired private AesEncryptionService aesEncryptionService;
	@Autowired private ProcessorToolConnectionService processorToolConnectionService;
	@Autowired private ProcessorExecutionTraceLogRepository processorExecutionTraceLogRepository;
	@Lazy @Autowired private JiraApiV3SearchService jiraApiV3SearchService;

	/**
	 * @param projectConfig projectConfig
	 * @param url url
	 * @param krb5Client krb5Client
	 * @return String
	 * @throws IOException IOException
	 */
	public String getDataFromClient(
			ProjectConfFieldMapping projectConfig, URL url, KerberosClient krb5Client)
			throws IOException {
		Optional<Connection> connectionOptional = projectConfig.getJira().getConnection();
		ObjectId projectConfigId = projectConfig.getBasicProjectConfigId();
		boolean spenagoClient = connectionOptional.map(Connection::isJaasKrbAuth).orElse(false);
		if (spenagoClient) {
			HttpUriRequest request =
					RequestBuilder.get()
							.setUri(url.toString())
							.setHeader(org.apache.http.HttpHeaders.ACCEPT, "application/json")
							.setHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, "application/json")
							.build();
			String responce = krb5Client.getResponse(request);
			return responce;
		} else {
			return getDataFromServer(url, connectionOptional, projectConfigId);
		}
	}

	/**
	 * @param url url
	 * @param connectionOptional connectionOptional
	 * @return String
	 * @throws IOException IOException
	 */
	public String getDataFromServer(
			URL url, Optional<Connection> connectionOptional, ObjectId projectConfigId)
			throws IOException {
		HttpURLConnection request = (HttpURLConnection) url.openConnection();

		String username = null;
		String password = null;

		if (connectionOptional.isPresent()) {
			Connection conn = connectionOptional.get();
			if (conn.isVault()) {
				ToolCredential toolCredential = toolCredentialProvider.findCredential(conn.getUsername());
				if (toolCredential != null) {
					username = toolCredential.getUsername();
					password = toolCredential.getPassword();
				}

			} else {
				username = connectionOptional.map(Connection::getUsername).orElse(null);
				password =
						decryptJiraPassword(connectionOptional.map(Connection::getPassword).orElse(null));
			}
		}
		if (connectionOptional.isPresent() && connectionOptional.get().isBearerToken()) {
			String patOAuthToken = decryptJiraPassword(connectionOptional.get().getPatOAuthToken());
			request.setRequestProperty("Authorization", "Bearer " + patOAuthToken); // NOSONAR
		} else {
			request.setRequestProperty(
					"Authorization", "Basic " + encodeCredentialsToBase64(username, password)); // NOSONAR
		}
		request.connect();
		// process the client error
		processClientError(connectionOptional, request, projectConfigId);
		StringBuilder sb = new StringBuilder();
		try (InputStream in = (InputStream) request.getContent();
				BufferedReader inReader =
						new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			int cp;
			while ((cp = inReader.read()) != -1) {
				sb.append((char) cp);
			}
			request.disconnect();
		} catch (IOException ie) {
			log.error("Read exception when connecting to server {}", ie);
			String errorMessage = ie.getMessage();
			// Regular expression pattern to extract the status code
			Pattern pattern = Pattern.compile("\\b(\\d{3})\\b");
			Matcher matcher = pattern.matcher(errorMessage);
			isClientException(connectionOptional, matcher);
			request.disconnect();
		}
		return sb.toString();
	}

	/**
	 * Method to process client error and update the connection broken flag
	 *
	 * @param connectionOptional connectionOptional
	 * @param request request
	 * @throws IOException throw IO Error
	 */
	private void processClientError(
			Optional<Connection> connectionOptional,
			HttpURLConnection request,
			ObjectId basicProjectConfigId)
			throws IOException {
		int responseCode = request.getResponseCode();
		if (responseCode >= 400 && responseCode < 500) {
			// Read error message from the server
			String errorMessage = readErrorStream(request.getErrorStream());
			if (responseCode == 404) {
				ErrorDetail errorDetail =
						new ErrorDetail(
								responseCode,
								request.getURL().toString(),
								errorMessage,
								determineImpactBasedOnUrl(request.getURL().toString()));
				Optional<ProcessorExecutionTraceLog> existingTraceLog =
						processorExecutionTraceLogRepository
								.findByProcessorNameAndBasicProjectConfigIdAndProgressStatsTrue(
										JiraConstants.JIRA, basicProjectConfigId.toString());
				existingTraceLog.ifPresent(
						traceLog -> {
							List<ErrorDetail> errorDetailList =
									Optional.ofNullable(traceLog.getErrorDetailList()).orElseGet(ArrayList::new);
							errorDetailList.add(errorDetail);
							traceLog.setErrorDetailList(errorDetailList);
							processorExecutionTraceLogRepository.save(traceLog);
						});
			}
			// flagging the connection flag w.r.t error code.
			connectionOptional.ifPresent(
					connection -> {
						String errMsg = ClientErrorMessageEnum.fromValue(responseCode).getReasonPhrase();
						processorToolConnectionService.updateBreakingConnection(connection.getId(), errMsg);
					});
			log.error("Exception when reading from server {} - {}", responseCode, errorMessage);
			// Throw exception for non-404 errors, as 404 indicates the resource mightn't
			// exist
			if (responseCode != 404) {
				request.disconnect();
				throw new IOException(String.format("Error: %d - %s", responseCode, errorMessage));
			}
		}
	}

	private String readErrorStream(InputStream errorStream) throws IOException {
		StringBuilder response = new StringBuilder();
		try (BufferedReader reader =
				new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
		}
		return response.toString();
	}

	private String determineImpactBasedOnUrl(String url) {
		if (url.contains("sprint")) {
			return "Sprint KPI's";
		} else if (url.contains("versions")) {
			return "Release KPI's";
		} else if (url.contains("epic")) {
			return "Epic KPI's";
		}
		return ""; // Default or unknown impact
	}

	/**
	 * @param connectionOptional connectionOptional
	 * @param matcher matcher
	 */
	private void isClientException(Optional<Connection> connectionOptional, Matcher matcher) {
		if (matcher.find()) {
			String statusCodeString = matcher.group(1);
			int statusCode = Integer.parseInt(statusCodeString);
			if (statusCode >= 400 && statusCode < 500 && connectionOptional.isPresent()) {
				String errMsg = ClientErrorMessageEnum.fromValue(statusCode).getReasonPhrase();
				processorToolConnectionService.updateBreakingConnection(
						connectionOptional.get().getId(), errMsg);
			}
		}
	}

	/**
	 * @param encryptedPassword encryptedPassword
	 * @return String
	 */
	public String decryptJiraPassword(String encryptedPassword) {
		return aesEncryptionService.decrypt(
				encryptedPassword, jiraProcessorConfig.getAesEncryptionKey());
	}

	/**
	 * @param username username
	 * @param password password
	 * @return String
	 */
	public String encodeCredentialsToBase64(String username, String password) {
		String cred = username + ":" + password;
		return Base64.getEncoder().encodeToString(cred.getBytes());
	}

	/**
	 * @param projectConfig projectConfig
	 * @param clientIncoming clientIncoming
	 * @param pageNumber pageNumber
	 * @param deltaDate deltaDate
	 * @return List of Issue
	 * @throws InterruptedException InterruptedException
	 */
	public List<Issue> fetchIssuesBasedOnJql(
			ProjectConfFieldMapping projectConfig,
			ProcessorJiraRestClient clientIncoming,
			int pageNumber,
			String deltaDate)
			throws InterruptedException {

		client = clientIncoming;
		List<Issue> issues = new ArrayList<>();
		if (client == null) {
			log.error(MSG_JIRA_CLIENT_SETUP_FAILED);
		} else {

			String queryDate =
					DateUtil.dateTimeFormatter(
							DateUtil.stringToLocalDateTime(deltaDate, JiraConstants.QUERYDATEFORMAT)
									.minusDays(jiraProcessorConfig.getDaysToReduce()),
							JiraConstants.QUERYDATEFORMAT);

			SearchResult searchResult = getJqlIssues(projectConfig, queryDate, pageNumber);
			issues = JiraHelper.getIssuesFromResult(searchResult);
		}
		return issues;
	}

	/**
	 * Checks if the exception is a 410 Gone error from deprecated JIRA API
	 *
	 * @param e RestClientException
	 * @return true if 410 error, false otherwise
	 */
	private boolean is410Error(RestClientException e) {
		Throwable cause = e.getCause();
		return cause != null && cause.getMessage() != null && cause.getMessage().contains("410");
	}

	/**
	 * Centralized JQL search with automatic 410 fallback to API v3 This method provides a single
	 * point for handling deprecated API errors
	 *
	 * @param jql JQL query string
	 * @param maxResults Maximum results per page
	 * @param startAt Starting position (offset)
	 * @param fields Fields to retrieve
	 * @param client JIRA REST client
	 * @param projectConfig Project configuration
	 * @return SearchResult with issues
	 * @throws InterruptedException if interrupted
	 */
	public SearchResult searchJqlWithFallback(
			String jql,
			int maxResults,
			int startAt,
			Set<String> fields,
			ProcessorJiraRestClient client,
			ProjectConfFieldMapping projectConfig)
			throws InterruptedException {

		try {
			Promise<SearchResult> promise =
					client.getProcessorSearchClient().searchJql(jql, maxResults, startAt, fields);
			return promise.claim();
		} catch (RestClientException e) {
			if (is410Error(e)) {
				log.warn("410 Gone - using API v3 fallback for JQL search: {}", jql);
				// Use the exact JQL that was provided (already complete)
				return getJqlIssuesViaAdvancedJql(jql, startAt, fields, projectConfig);
			} else {
				// Handle other errors (4xx/5xx)
				if (e.getStatusCode().isPresent()
						&& e.getStatusCode().get() >= 400
						&& e.getStatusCode().get() < 500) {
					String errMsg =
							ClientErrorMessageEnum.fromValue(e.getStatusCode().get()).getReasonPhrase();
					processorToolConnectionService.updateBreakingConnection(
							projectConfig.getProjectToolConfig().getConnectionId(), errMsg);
				}
				throw e;
			}
		}
	}

	/**
	 * Get issue count only (optimized for JobListeners) Uses API v3 fallback if 410 error occurs
	 *
	 * @param jql JQL query string
	 * @param client JIRA REST client
	 * @param projectConfig Project configuration
	 * @return Total count of issues from JIRA
	 */
	public long getJqlIssueCountWithFallback(
			String jql, ProcessorJiraRestClient client, ProjectConfFieldMapping projectConfig) {

		try {
			Promise<SearchResult> promise = client.getProcessorSearchClient().searchJql(jql, 0, 0, null);
			SearchResult result = promise.claim();
			return result != null ? (long) result.getTotal() : 0L;
		} catch (RestClientException e) {
			if (is410Error(e)) {
				log.warn("410 Gone - using API v3 fallback to count issues. JQL: {}", jql);
				// Fall back to API v3 to get accurate count
				return getIssueCountViaApiV3(jql, projectConfig);
			} else {
				// Handle other errors (4xx/5xx)
				if (e.getStatusCode().isPresent()
						&& e.getStatusCode().get() >= 400
						&& e.getStatusCode().get() < 500) {
					String errMsg =
							ClientErrorMessageEnum.fromValue(e.getStatusCode().get()).getReasonPhrase();
					processorToolConnectionService.updateBreakingConnection(
							projectConfig.getProjectToolConfig().getConnectionId(), errMsg);
				}
				throw e;
			}
		}
	}

	/**
	 * Gets total issue count via API v3 by fetching all pages This is used when old API returns 410
	 * Gone
	 *
	 * @param jql JQL query string
	 * @param projectConfig Project configuration
	 * @return Total count of issues
	 */
	private long getIssueCountViaApiV3(String jql, ProjectConfFieldMapping projectConfig) {
		long totalCount = 0;
		String nextPageToken = null;
		boolean isLast;

		try {
			log.info("Counting issues via API v3 for validation. JQL: {}", jql);

			java.util.Set<String> fields = new java.util.HashSet<>();
			fields.add("*all");

			do {
				JiraSearchResponse result =
						jiraApiV3SearchService.searchJql(
								jql,
								jiraProcessorConfig.getPageSize(),
								fields,
								nextPageToken,
								projectConfig.getJira());

				if (result.getIssues() != null) {
					totalCount += StreamSupport.stream(result.getIssues().spliterator(), false).count();
				}

				// Update pagination state
				isLast = result.isLast();
				nextPageToken = result.getNextPageToken();

			} while (!isLast && nextPageToken != null);

			log.info("API v3 count completed: {} issues found", totalCount);
			return totalCount;

		} catch (Exception e) {
			log.error("Error counting issues via API v3. Returning 0.", e);
			return 0L;
		}
	}

	/**
	 * @param projectConfig projectConfig
	 * @param deltaDate deltaDate
	 * @param pageStart pageStart
	 * @return SearchResult
	 * @throws InterruptedException InterruptedException
	 */
	public SearchResult getJqlIssues(
			ProjectConfFieldMapping projectConfig, String deltaDate, int pageStart)
			throws InterruptedException {
		SearchResult searchResult = null;
		String[] jiraIssueTypeNames = projectConfig.getFieldMapping().getJiraIssueTypeNames();
		if (client == null) {
			log.warn(MSG_JIRA_CLIENT_SETUP_FAILED);
		} else if (StringUtils.isEmpty(projectConfig.getProjectToolConfig().getProjectKey())
				|| StringUtils.isEmpty(projectConfig.getProjectToolConfig().getBoardQuery())
				|| null == jiraIssueTypeNames) {
			log.error(
					"Either Project key is empty or Jql Query not provided or jiraIssueTypeNames not configured in fieldmapping . key {} jql query {} ",
					projectConfig.getProjectToolConfig().getProjectKey(),
					projectConfig.getProjectToolConfig().getBoardQuery());
		} else {
			try {
				String issueTypes =
						Arrays.stream(jiraIssueTypeNames)
								.map(array -> "\"" + String.join("\", \"", array) + "\"")
								.collect(Collectors.joining(", "));
				StringBuilder query =
						new StringBuilder("project in (")
								.append(projectConfig.getProjectToolConfig().getProjectKey())
								.append(") and ");

				String userQuery =
						projectConfig.getJira().getBoardQuery().toLowerCase().split(JiraConstants.ORDERBY)[0];
				query.append(userQuery);
				query.append(
						" and issuetype in (" + issueTypes + " ) and updatedDate>='" + deltaDate + "' ");
				query.append(" order BY updatedDate asc");
				log.info("jql query :{}", query);

				// Use centralized wrapper with 410 fallback
				searchResult =
						searchJqlWithFallback(
								query.toString(),
								jiraProcessorConfig.getPageSize(),
								pageStart,
								JiraConstants.ISSUE_FIELD_SET,
								client,
								projectConfig);

				if (searchResult != null) {
					saveSearchDetailsInContext(
							searchResult, pageStart, null, StepSynchronizationManager.getContext());
					log.info(
							String.format(
									PROCESSING_ISSUES_PRINT_LOG,
									pageStart,
									Math.min(
											pageStart + jiraProcessorConfig.getPageSize() - 1, searchResult.getTotal()),
									searchResult.getTotal()));
				}
			} catch (RestClientException e) {
				throw e;
			}
		}
		return searchResult;
	}

	/**
	 * Fetches JQL issues using advanced JQL search API (API v3) This is the fallback method when the
	 * old API returns 410 Uses token-based pagination with caching to return ONE page at a time
	 * (matching old API behavior for batch processing compatibility)
	 *
	 * @param jql Complete JQL query string (as provided to old API)
	 * @param pageStart pageStart (used to reset cache on page 0)
	 * @param fields Fields to retrieve
	 * @param projectConfig projectConfig
	 * @return SearchResult with ONE page of issues
	 */
	private SearchResult getJqlIssuesViaAdvancedJql(
			String jql, int pageStart, Set<String> fields, ProjectConfFieldMapping projectConfig) {
		SearchResult searchResult = null;
		int cumulativeTotal = 0;

		try {
			String cacheKey = buildCacheKey(projectConfig.getProjectToolConfig().getProjectKey(), jql);
			handleCacheAndLogging(pageStart, cacheKey, projectConfig);
			String nextPageToken = advancedJqlNextPageTokenCache.get(cacheKey);
			validateTokenForNonFirstPage(pageStart, nextPageToken, projectConfig);

			java.util.Set<String> requestFields = getRequestFields(fields);
			JiraSearchResponse apiResponse =
					jiraApiV3SearchService.searchJql(
							jql,
							jiraProcessorConfig.getPageSize(),
							requestFields,
							nextPageToken,
							projectConfig.getJira());

			List<Issue> pageIssues = extractIssuesFromResponse(apiResponse);
			cumulativeTotal = pageStart + pageIssues.size();
			updateTokenCache(cacheKey, apiResponse, cumulativeTotal, projectConfig);
			searchResult =
					createSearchResultBasedOnIssues(pageIssues, pageStart, cumulativeTotal, apiResponse);
		} catch (Exception e) {
			log.error(
					"Error while fetching issues via JIRA API v3 for project: {} at page: {}",
					projectConfig.getProjectToolConfig().getProjectKey(),
					pageStart,
					e);
			throw new RestClientException(e);
		}

		return searchResult;
	}

	private void handleCacheAndLogging(
			int pageStart, String cacheKey, ProjectConfFieldMapping projectConfig) {
		if (pageStart == 0) {
			advancedJqlNextPageTokenCache.remove(cacheKey);
			log.info(
					"Starting JIRA API v3 search for project: {} with JQL: {}",
					projectConfig.getProjectToolConfig().getProjectKey(),
					projectConfig.getJira().getBoardQuery());
		} else {
			log.debug("Fetching page {} via JIRA API v3", pageStart / jiraProcessorConfig.getPageSize());
		}
	}

	private void validateTokenForNonFirstPage(
			int pageStart, String nextPageToken, ProjectConfFieldMapping projectConfig) {
		if (pageStart > 0 && nextPageToken == null) {
			log.warn(
					"API v3 pagination token missing for project: {}. This may indicate pagination reset.",
					projectConfig.getProjectToolConfig().getProjectKey());
		}
	}

	private java.util.Set<String> getRequestFields(Set<String> fields) {
		java.util.Set<String> requestFields = fields;
		if (requestFields == null || requestFields.isEmpty()) {
			requestFields = new java.util.HashSet<>();
			requestFields.add("*all");
		}
		return requestFields;
	}

	private List<Issue> extractIssuesFromResponse(JiraSearchResponse apiResponse) {
		List<Issue> pageIssues = new ArrayList<>();
		if (apiResponse.getIssues() != null) {
			apiResponse.getIssues().forEach(pageIssues::add);
		}
		return pageIssues;
	}

	private void updateTokenCache(
			String cacheKey,
			JiraSearchResponse apiResponse,
			int cumulativeTotal,
			ProjectConfFieldMapping projectConfig) {
		if (!apiResponse.isLast() && apiResponse.getNextPageToken() != null) {
			advancedJqlNextPageTokenCache.put(cacheKey, apiResponse.getNextPageToken());
			log.debug(
					"Cached next page token for project: {}, cumulative count: {}",
					projectConfig.getProjectToolConfig().getProjectKey(),
					cumulativeTotal);
		} else {
			advancedJqlNextPageTokenCache.remove(cacheKey);
			logCompletionSummary(apiResponse, cumulativeTotal, projectConfig);
		}
	}

	private void logCompletionSummary(
			JiraSearchResponse apiResponse, int cumulativeTotal, ProjectConfFieldMapping projectConfig) {
		if (apiResponse.isLast()) {
			int totalPages =
					(cumulativeTotal / jiraProcessorConfig.getPageSize())
							+ (cumulativeTotal % jiraProcessorConfig.getPageSize() > 0 ? 1 : 0);
			log.info(
					"API v3 fetch completed for project: {} - Total: {} issues across {} pages",
					projectConfig.getProjectToolConfig().getProjectKey(),
					cumulativeTotal,
					totalPages);
		}
	}

	private SearchResult createSearchResultBasedOnIssues(
			List<Issue> pageIssues, int pageStart, int cumulativeTotal, JiraSearchResponse apiResponse) {
		SearchResult searchResult;
		if (!pageIssues.isEmpty()) {
			int displayTotal = cumulativeTotal;
			String totalSuffix = apiResponse.isLast() ? "" : "+";
			searchResult = createSearchResultFromIssues(pageIssues, pageStart, displayTotal);
			saveSearchDetailsInContext(
					searchResult, pageStart, null, StepSynchronizationManager.getContext());
			log.info(
					"Processing issues {} - {} out of {}{}",
					pageStart,
					pageStart + pageIssues.size() - 1,
					displayTotal,
					totalSuffix);
		} else {
			searchResult = createSearchResultFromIssues(pageIssues, pageStart, pageStart);
		}
		return searchResult;
	}

	/**
	 * Gets the total count of issues for a JQL query by iterating through all pages Uses JIRA API v3
	 * search with minimal fields to count all matching issues
	 *
	 * @param jql Complete JQL query string
	 * @param projectConfig projectConfig
	 * @return Total count of issues matching the query
	 */
	public int getTotalIssueCount(String jql, ProjectConfFieldMapping projectConfig) {
		try {
			log.info(
					"Fetching total issue count for project: {} with JQL: {}",
					projectConfig.getProjectToolConfig().getProjectKey(),
					jql);

			int totalCount = 0;
			String nextPageToken = null;
			boolean hasMorePages = true;

			// Use minimal fields for counting
			java.util.Set<String> minimalFields = new java.util.HashSet<>();
			minimalFields.add("key");

			while (hasMorePages) {
				JiraSearchResponse apiResponse =
						jiraApiV3SearchService.searchJql(
								jql,
								jiraProcessorConfig.getPageSize(),
								minimalFields,
								nextPageToken,
								projectConfig.getJira());

				if (apiResponse.getIssues() != null) {
					totalCount +=
							(int) StreamSupport.stream(apiResponse.getIssues().spliterator(), false).count();
				}

				hasMorePages = !apiResponse.isLast();
				nextPageToken = apiResponse.getNextPageToken();
			}

			log.info(
					"Total issue count for project: {} is {}",
					projectConfig.getProjectToolConfig().getProjectKey(),
					totalCount);

			return totalCount;
		} catch (Exception e) {
			log.error(
					"Error while fetching total issue count for project: {}",
					projectConfig.getProjectToolConfig().getProjectKey(),
					e);
			throw new RestClientException(e);
		}
	}

	/**
	 * Builds cache key for pagination token storage
	 *
	 * @param projectKey project key
	 * @param query JQL query
	 * @return cache key
	 */
	private String buildCacheKey(String projectKey, String query) {
		return projectKey + "_" + Integer.toHexString(query.hashCode());
	}

	/**
	 * Creates a SearchResult object from a list of issues
	 *
	 * @param issues List of issues
	 * @param startAt Starting position
	 * @param total Total number of issues
	 * @return SearchResult
	 */
	private SearchResult createSearchResultFromIssues(List<Issue> issues, int startAt, int total) {
		return new SearchResult(startAt, jiraProcessorConfig.getPageSize(), total, issues);
	}

	/**
	 * Method to save the search details in context.
	 *
	 * @param searchResult searchResult
	 * @param pageStart pageStart
	 * @param stepContext stepContext
	 */
	public void saveSearchDetailsInContext(
			SearchResult searchResult, int pageStart, String boardId, StepContext stepContext) {
		if (stepContext == null) {
			log.error("StepContext is null");
			return;
		}
		JobExecution jobExecution = stepContext.getStepExecution().getJobExecution();
		int total = searchResult.getTotal();
		int processed = Math.min(pageStart + jiraProcessorConfig.getPageSize() - 1, total);

		// Saving Progress details in context
		jobExecution.getExecutionContext().putInt(JiraConstants.TOTAL_ISSUES, total);
		jobExecution.getExecutionContext().putInt(JiraConstants.PROCESSED_ISSUES, processed);
		jobExecution.getExecutionContext().putInt(JiraConstants.PAGE_START, pageStart);
		jobExecution.getExecutionContext().putString(JiraConstants.BOARD_ID, boardId);
	}

	/**
	 * @param projectConfig projectConfig
	 * @param clientIncoming clientIncoming
	 * @param pageNumber pageNumber
	 * @param boardId boardId
	 * @param deltaDate deltaDate
	 * @return List of Issue
	 * @throws InterruptedException InterruptedException
	 * @throws IOException throws IOException *
	 */
	public List<Issue> fetchIssueBasedOnBoard(
			ProjectConfFieldMapping projectConfig,
			ProcessorJiraRestClient clientIncoming,
			int pageNumber,
			String boardId,
			String deltaDate)
			throws InterruptedException, IOException {

		client = clientIncoming;
		List<Issue> issues = new ArrayList<>();
		if (client == null) {
			log.error(MSG_JIRA_CLIENT_SETUP_FAILED);
		} else {
			String queryDate =
					DateUtil.dateTimeFormatter(
							DateUtil.stringToLocalDateTime(deltaDate, JiraConstants.QUERYDATEFORMAT)
									.minusDays(jiraProcessorConfig.getDaysToReduce()),
							JiraConstants.QUERYDATEFORMAT);

			SearchResult searchResult = getBoardIssues(boardId, projectConfig, queryDate, pageNumber);
			issues = JiraHelper.getIssuesFromResult(searchResult);
		}
		return issues;
	}

	/**
	 * @param boardId boardId
	 * @param projectConfig projectConfig
	 * @param deltaDate deltaDate
	 * @param pageStart pageStart
	 * @return SearchResult
	 * @throws InterruptedException InterruptedException
	 */
	public SearchResult getBoardIssues(
			String boardId, ProjectConfFieldMapping projectConfig, String deltaDate, int pageStart)
			throws InterruptedException {
		SearchResult searchResult = null;
		String[] jiraIssueTypeNames = projectConfig.getFieldMapping().getJiraIssueTypeNames();
		if (client == null) {
			log.warn(MSG_JIRA_CLIENT_SETUP_FAILED);
		} else if (StringUtils.isEmpty(projectConfig.getProjectToolConfig().getProjectKey())
				|| null == jiraIssueTypeNames) {
			log.error(
					"Either Project key is empty or jiraIssueTypeNames not provided. key {} ",
					projectConfig.getProjectToolConfig().getProjectKey());
		} else {
			try {
				String query = "updatedDate>='" + deltaDate + "' order by updatedDate asc";
				Promise<SearchResult> promisedRs =
						client
								.getCustomIssueClient()
								.searchBoardIssue(
										boardId,
										query,
										jiraProcessorConfig.getPageSize(),
										pageStart,
										JiraConstants.ISSUE_FIELD_SET);
				searchResult = promisedRs.claim();
				if (searchResult != null) {
					saveSearchDetailsInContext(
							searchResult, pageStart, boardId, StepSynchronizationManager.getContext());
					log.info(
							String.format(
									PROCESSING_ISSUES_PRINT_LOG,
									pageStart,
									Math.min(
											pageStart + jiraProcessorConfig.getPageSize() - 1, searchResult.getTotal()),
									searchResult.getTotal()));
				}
			} catch (RestClientException e) {
				if (e.getStatusCode().isPresent()
						&& e.getStatusCode().get() >= 400
						&& e.getStatusCode().get() < 500) {
					String errMsg =
							ClientErrorMessageEnum.fromValue(e.getStatusCode().get()).getReasonPhrase();
					processorToolConnectionService.updateBreakingConnection(
							projectConfig.getProjectToolConfig().getConnectionId(), errMsg);
				}
				throw e;
			}
		}

		return searchResult;
	}

	/**
	 * @param projectConfig projectConfig
	 * @param krb5Client krb5Client
	 * @return List of ProjectVersion
	 * @throws IOException IOException
	 * @throws ParseException ParseException
	 */
	public List<ProjectVersion> getVersion(
			ProjectConfFieldMapping projectConfig, KerberosClient krb5Client)
			throws IOException, ParseException {
		List<ProjectVersion> projectVersionList = new ArrayList<>();
		try {
			JiraToolConfig jiraToolConfig = projectConfig.getJira();
			if (null != jiraToolConfig) {
				URL url = getVersionUrl(projectConfig);
				parseVersionData(getDataFromClient(projectConfig, url, krb5Client), projectVersionList);
			}
		} catch (RestClientException rce) {
			if (rce.getStatusCode().isPresent()
					&& rce.getStatusCode().get() >= 400
					&& rce.getStatusCode().get() < 500) {
				String errMsg =
						ClientErrorMessageEnum.fromValue(rce.getStatusCode().get()).getReasonPhrase();
				processorToolConnectionService.updateBreakingConnection(
						projectConfig.getProjectToolConfig().getConnectionId(), errMsg);
			}
			log.error("Client exception when fetching versions " + rce);
			throw rce;
		} catch (MalformedURLException mfe) {
			log.error("Malformed url for fetching versions", mfe);
			throw mfe;
		}
		return projectVersionList;
	}

	private URL getVersionUrl(ProjectConfFieldMapping projectConfig) throws MalformedURLException {

		Optional<Connection> connectionOptional = projectConfig.getJira().getConnection();
		boolean isCloudEnv = connectionOptional.map(Connection::isCloudEnv).orElse(false);
		String serverURL = jiraProcessorConfig.getJiraVersionApi();
		if (isCloudEnv) {
			serverURL = jiraProcessorConfig.getJiraCloudVersionApi();
		}
		serverURL = serverURL.replace("{projectKey}", projectConfig.getJira().getProjectKey());
		String baseUrl = connectionOptional.map(Connection::getBaseUrl).orElse("");
		return new URL(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + serverURL);
	}

	private void parseVersionData(
			String dataFromServer, List<ProjectVersion> projectVersionDetailList) throws ParseException {
		if (StringUtils.isNotBlank(dataFromServer)) {
			try {
				JSONArray obj = (JSONArray) new JSONParser().parse(dataFromServer);
				if (null != obj) {
					((JSONArray) new JSONParser().parse(dataFromServer))
							.forEach(
									values -> {
										ProjectVersion projectVersion = new ProjectVersion();
										projectVersion.setId(
												Long.valueOf(
														Objects.requireNonNull(getOptionalString((JSONObject) values, "id"))));
										projectVersion.setName(getOptionalString((JSONObject) values, "name"));
										projectVersion.setArchived(
												Boolean.parseBoolean(getOptionalString((JSONObject) values, "archived")));
										projectVersion.setReleased(
												Boolean.parseBoolean(getOptionalString((JSONObject) values, "released")));
										if (getOptionalString((JSONObject) values, "startDate") != null) {
											projectVersion.setStartDate(
													DateUtil.stringToDateTime(
															Objects.requireNonNull(
																	getOptionalString((JSONObject) values, "startDate")),
															"yyyy-MM-dd"));
										}
										if (getOptionalString((JSONObject) values, "releaseDate") != null) {
											projectVersion.setReleaseDate(
													DateUtil.stringToDateTime(
															Objects.requireNonNull(
																	getOptionalString((JSONObject) values, "releaseDate")),
															"yyyy-MM-dd"));
										}
										projectVersionDetailList.add(projectVersion);
									});
				}
			} catch (Exception pe) {
				log.error("Parser exception when parsing versions", pe);
				throw pe;
			}
		}
	}

	private String getOptionalString(final JSONObject jsonObject, final String attributeName) {
		final Object res = jsonObject.get(attributeName);
		if (res == null) {
			return null;
		}
		return res.toString();
	}

	/**
	 * * Gets api host
	 *
	 * @return apiHost
	 * @throws UnknownHostException UnknownHostException
	 */
	public String getApiHost() throws UnknownHostException {

		StringBuilder urlPath = new StringBuilder();
		if (StringUtils.isNotEmpty(jiraProcessorConfig.getUiHost())) {
			urlPath
					.append("https")
					.append(':')
					.append(File.separator + File.separator)
					.append(jiraProcessorConfig.getUiHost().trim());
		} else {
			throw new UnknownHostException("Api host not found in properties.");
		}

		return urlPath.toString();
	}
}
