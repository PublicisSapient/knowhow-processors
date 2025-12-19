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

import static com.atlassian.jira.rest.client.api.IssueRestClient.Expandos.CHANGELOG;
import static com.atlassian.jira.rest.client.api.IssueRestClient.Expandos.NAMES;
import static com.atlassian.jira.rest.client.api.IssueRestClient.Expandos.SCHEMA;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.stereotype.Service;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.publicissapient.kpidashboard.common.client.KerberosClient;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.jira.client.ProcessorJiraRestClient;
import com.publicissapient.kpidashboard.jira.config.JiraProcessorConfig;
import com.publicissapient.kpidashboard.jira.constant.JiraConstants;
import com.publicissapient.kpidashboard.jira.exception.JiraApiException;
import com.publicissapient.kpidashboard.jira.model.JiraSearchResponse;
import com.publicissapient.kpidashboard.jira.model.JiraToolConfig;
import com.publicissapient.kpidashboard.jira.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.jira.parser.JiraSearchResponseParser;

import io.atlassian.util.concurrent.Promise;
import jakarta.ws.rs.core.UriBuilder;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FetchEpicDataImpl implements FetchEpicData {

	private static final Function<IssueRestClient.Expandos, String> EXPANDO_TO_PARAM = from -> from.name().toLowerCase(); //NOSONAR
	private static final String KEY = "key";
	private static final String JQL_SEARCH_URL = "/rest/api/latest/search/jql";
	private static final String ACCEPT = "accept";
	private static final String APPLICATION_JSON = "application/json";
	private static final String CONTENT_TYPE = "Content-Type";
	private static final int PAGE_SIZE = 50;

	private final JiraCommonService jiraCommonService;
	private final JiraProcessorConfig jiraProcessorConfig;

	public FetchEpicDataImpl(JiraCommonService jiraCommonService, JiraProcessorConfig jiraProcessorConfig) {
		this.jiraCommonService = jiraCommonService;
		this.jiraProcessorConfig = jiraProcessorConfig;
	}
	@Override
	public List<Issue> fetchEpic(ProjectConfFieldMapping projectConfig, String boardId, ProcessorJiraRestClient client,
								 KerberosClient krb5Client) throws InterruptedException, IOException {

		List<String> epicList = new ArrayList<>();
		try {
			JiraToolConfig jiraToolConfig = projectConfig.getJira();
			if (null != jiraToolConfig) {
				boolean isLast = false;
				int startIndex = 0;
				do {
					URL url = getEpicUrl(projectConfig, boardId, startIndex);
					String jsonResponse = jiraCommonService.getDataFromClient(projectConfig, url, krb5Client);
					isLast = populateData(jsonResponse, epicList);
					startIndex = epicList.size();
					TimeUnit.MILLISECONDS.sleep(jiraProcessorConfig.getSubsequentApiCallDelayInMilli());
				} while (!isLast);
			}
		} catch (RestClientException rce) {
			log.error("Client exception when loading epic data", rce);
			throw rce;
		} catch (MalformedURLException mfe) {
			log.error("Malformed url for loading epic data", mfe);
			throw mfe;
		}

		List<Issue> issues = new ArrayList<>();
		try {
			// Attempt fetching using REST client
			issues = getEpicIssuesQuery(epicList, client);
		} catch (RestClientException rce) {
			Throwable cause = rce.getCause();
			if (cause != null && cause.getMessage() != null && cause.getMessage().contains("410")) {
				log.warn("Received 410 Gone error. Falling back to advanced JQL search.");
				issues = getEpicIssuesViaAdvancedJql(epicList, projectConfig.getJira());
			} else {
				throw rce;
			}
		}

		return issues;
	}

	private List<Issue> getEpicIssuesQuery(List<String> epicKeyList, ProcessorJiraRestClient client)
			throws InterruptedException {

		List<Issue> issueList = new ArrayList<>();
		SearchResult searchResult = null;
		try {
			if (CollectionUtils.isNotEmpty(epicKeyList)) {
				String query = "key in (" + String.join(",", epicKeyList) + ")";
				int pageStart = 0;
				int totalEpic = 0;
				int fetchedEpic = 0;
				boolean continueFlag = true;
				do {
					Promise<SearchResult> promise = client.getProcessorSearchClient().searchJql(query,
							jiraProcessorConfig.getPageSize(), pageStart, null);
					searchResult = promise.claim();
					if (null != searchResult && null != searchResult.getIssues()) {
						if (totalEpic == 0) {
							totalEpic = searchResult.getTotal();
						}
						int issueCount = 0;
						for (Issue issue : searchResult.getIssues()) {
							issueList.add(issue);
							issueCount++;
						}
						fetchedEpic += issueCount;
						pageStart += issueCount;
						if (totalEpic <= fetchedEpic) {
							fetchedEpic = totalEpic;
							continueFlag = false;
						}
					} else {
						break;
					}
					TimeUnit.MILLISECONDS.sleep(jiraProcessorConfig.getSubsequentApiCallDelayInMilli());
				} while (totalEpic < fetchedEpic || continueFlag);
			}
		} catch (RestClientException e) {
			log.error("Error while fetching issues", e.getCause());
			throw e;
		}
		return issueList;
	}

	public List<Issue> getEpicIssuesViaAdvancedJql(List<String> epicKeyList,JiraToolConfig jiraToolConfig) {
		List<Issue> allIssues = new ArrayList<>();

		if (CollectionUtils.isEmpty(epicKeyList)) {
			return allIssues;
		}

		try {
			String jql = "key in (" + String.join(",", epicKeyList) + ")";

			String nextPageToken = null;
			boolean isLast;

			do {

				Set<String> fields = new HashSet<>();
				fields.add("*all");

				JiraSearchResponse result = searchJql(jql,PAGE_SIZE, fields, nextPageToken, jiraToolConfig);

				for (Issue issue : result.getIssues()) {
					allIssues.add(issue);
				}

				isLast = result.isLast();
				nextPageToken = result.getNextPageToken();

			} while (!isLast && nextPageToken != null);

		} catch (JiraApiException | JSONException e) {
			log.error("Error while fetching Epic issues", e.getCause());
		}

		return allIssues;
	}

	public JiraSearchResponse searchJql(@Nullable String jql, @Nullable Integer maxResults,
										   @Nullable Set<String> fields, String nextPageToken, JiraToolConfig jiraToolConfig) throws JSONException, JiraApiException {
		final Iterable<String> expandosValues = Iterables.transform(java.util.List.of(SCHEMA, NAMES, CHANGELOG),
				EXPANDO_TO_PARAM);
		final String notNullJql = StringUtils.defaultString(jql);
		if (notNullJql.length() > (JiraConstants.MAX_JQL_LENGTH_FOR_HTTP_GET)) {
			return advancedJqlSearchPost(maxResults, expandosValues, notNullJql, fields, nextPageToken, jiraToolConfig);
		} else {
			return advancedJqlSearchGet(maxResults, expandosValues, notNullJql, fields, nextPageToken, jiraToolConfig);
		}
	}
	private JiraSearchResponse advancedJqlSearchGet(
			@Nullable Integer maxResults,
			Iterable<String> expandosValues,
			String jql,
			@Nullable Set<String> fields,
			String nextPageToken,
			JiraToolConfig jiraToolConfig) throws JSONException, JiraApiException {

		Connection connection = jiraToolConfig.getConnection()
				.orElseThrow(() -> new JiraApiException("No connection available in JiraToolConfig"));

		String password = connection.isBearerToken()
				? jiraCommonService.decryptJiraPassword(connection.getPatOAuthToken())
				: jiraCommonService.decryptJiraPassword(connection.getPassword());

		String expandJoined = (expandosValues != null)
				? StreamSupport.stream(expandosValues.spliterator(), false)
				.collect(Collectors.joining(","))
				: null;

		String fieldsJoined = (fields != null && !fields.isEmpty())
				? String.join(",", fields)
				: null;

		final URI baseUri = buildJqlSearchUri(connection);

		HttpResponse<JsonNode> response = Unirest.get(baseUri.toString())
				.basicAuth(connection.getUsername(), password)
				.header(ACCEPT, APPLICATION_JSON)
				.queryString(JiraConstants.JQL_ATTRIBUTE, jql)
				.queryString(JiraConstants.FIELDS_BY_KEYS_ATTRIBUTE, true)
				.queryString(JiraConstants.MAX_RESULTS_ATTRIBUTE, maxResults)
				.queryString(JiraConstants.NEXT_PAGE_TOKEN_ATTRIBUTE, nextPageToken)
				.queryString(JiraConstants.EXPAND_ATTRIBUTE, expandJoined)
				.queryString(JiraConstants.FIELDS_ATTRIBUTE, fieldsJoined)
				.asJson();

		if (response.getStatus() != 200) {
			throw new JiraApiException("Failed to fetch issues: HTTP " + response.getStatus());
		}

		kong.unirest.json.JSONObject jsonFromUnirest = response.getBody().getObject();
		org.codehaus.jettison.json.JSONObject jsonObject =
				new org.codehaus.jettison.json.JSONObject(jsonFromUnirest.toString());

		return new JiraSearchResponseParser().parse(jsonObject);
	}

	private URI buildJqlSearchUri(Connection connection) {
		return UriBuilder
				.fromUri(connection.getBaseUrl())
				.path(JQL_SEARCH_URL)
				.build();
	}


	private JiraSearchResponse advancedJqlSearchPost(
			@Nullable Integer maxResults,
			Iterable<String> expandosValues,
			String jql,
			@Nullable Set<String> fields,
			String nextPageToken,
			JiraToolConfig jiraToolConfig) throws JSONException, JiraApiException {

		Connection connection = jiraToolConfig.getConnection()
				.orElseThrow(() -> new JiraApiException("No connection available in JiraToolConfig"));

		String password = connection.isBearerToken()
				? jiraCommonService.decryptJiraPassword(connection.getPatOAuthToken())
				: jiraCommonService.decryptJiraPassword(connection.getPassword());

		ObjectNode payload = JsonNodeFactory.instance.objectNode();

		String expandJoined = (expandosValues != null)
				? StreamSupport.stream(expandosValues.spliterator(), false).collect(Collectors.joining(","))
				: null;
		if (expandJoined != null) {
			payload.put(JiraConstants.EXPAND_ATTRIBUTE, expandJoined);
		}

		ArrayNode fieldsArray = payload.putArray(JiraConstants.FIELDS_ATTRIBUTE);
		if (fields != null && !fields.isEmpty()) {
			for (String field : fields) {
				fieldsArray.add(field);
			}
		}

		payload.put(JiraConstants.FIELDS_BY_KEYS_ATTRIBUTE, true);
		payload.put(JiraConstants.JQL_ATTRIBUTE, jql);
		if (maxResults != null) {
			payload.put(JiraConstants.MAX_RESULTS_ATTRIBUTE, maxResults);
		}
		if (nextPageToken != null) {
			payload.put(JiraConstants.NEXT_PAGE_TOKEN_ATTRIBUTE, nextPageToken);
		}

		final URI baseUri = buildJqlSearchUri(connection);

		HttpResponse<JsonNode> response = Unirest.post(baseUri.toString())
				.basicAuth(connection.getUsername(), password)
				.header(ACCEPT, APPLICATION_JSON)
				.header(CONTENT_TYPE, APPLICATION_JSON)
				.body(payload.toString())
				.asJson();

		if (response.getStatus() != 200) {
			throw new JiraApiException("Failed to fetch issues: HTTP " + response.getStatus());
		}

		kong.unirest.json.JSONObject jsonFromUnirest = response.getBody().getObject();
		org.codehaus.jettison.json.JSONObject jsonObject =
				new org.codehaus.jettison.json.JSONObject(jsonFromUnirest.toString());

		return new JiraSearchResponseParser().parse(jsonObject);
	}

	private boolean populateData(String sprintReportObj, List<String> epicList) {
		boolean isLast = true;
		if (StringUtils.isNotBlank(sprintReportObj)) {
			JSONArray valuesJson = new JSONArray();
			try {
				JSONObject obj = (JSONObject) new JSONParser().parse(sprintReportObj);
				if (null != obj) {
					valuesJson = (JSONArray) obj.get("values");
				}
				getEpic(valuesJson, epicList);
				isLast = Boolean.parseBoolean(Objects.requireNonNull(obj).get("isLast").toString());
			} catch (ParseException pe) {
				log.error("Parser exception when parsing statuses", pe);
			}
		}
		return isLast;
	}

	private void getEpic(JSONArray valuesJson, List<String> epicList) {
		for (int i = 0; i < valuesJson.size(); i++) {
			JSONObject sprintJson = (JSONObject) valuesJson.get(i);
			if (null != sprintJson) {
				epicList.add(sprintJson.get(KEY).toString());
			}
		}
	}

	private URL getEpicUrl(ProjectConfFieldMapping projectConfig, String boardId, int startIndex)
			throws MalformedURLException {

		Optional<Connection> connectionOptional = projectConfig.getJira().getConnection();
		String serverURL = jiraProcessorConfig.getJiraEpicApi();

		serverURL = serverURL.replace("{startAtIndex}", String.valueOf(startIndex)).replace("{boardId}", boardId);
		String baseUrl = connectionOptional.map(Connection::getBaseUrl).orElse("");
		return new URL(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + serverURL);
	}
}
