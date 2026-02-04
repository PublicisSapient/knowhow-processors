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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.springframework.stereotype.Service;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.jira.constant.JiraConstants;
import com.publicissapient.kpidashboard.jira.exception.JiraApiException;
import com.publicissapient.kpidashboard.jira.model.JiraSearchResponse;
import com.publicissapient.kpidashboard.jira.model.JiraToolConfig;
import com.publicissapient.kpidashboard.jira.parser.JiraSearchResponseParser;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of JIRA API v3 search service.
 * This service uses the new JIRA API v3 /rest/api/latest/search/jql endpoint
 * which is a replacement for the deprecated API v2 search endpoint.
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class JiraApiV3SearchServiceImpl implements JiraApiV3SearchService {

	private static final Function<com.atlassian.jira.rest.client.api.IssueRestClient.Expandos, String>
			EXPANDO_TO_PARAM = from -> from.name().toLowerCase(); // NOSONAR

	private static final String JQL_SEARCH_URL = "/rest/api/latest/search/jql";
	private static final String ACCEPT = "accept";
	private static final String APPLICATION_JSON = "application/json";
	private static final String CONTENT_TYPE = "Content-Type";

	private final JiraCommonService jiraCommonService;

	@Override
	public JiraSearchResponse searchJql(
			@Nullable String jql,
			@Nullable Integer maxResults,
			@Nullable Set<String> fields,
			String nextPageToken,
			JiraToolConfig jiraToolConfig)
			throws JSONException, JiraApiException {

		final Iterable<String> expandosValues =
				Iterables.transform(java.util.List.of(SCHEMA, NAMES, CHANGELOG), EXPANDO_TO_PARAM);
		final String notNullJql = StringUtils.defaultString(jql);

		// Choose GET or POST based on JQL length
		if (notNullJql.length() > (JiraConstants.MAX_JQL_LENGTH_FOR_HTTP_GET)) {
			return advancedJqlSearchPost(
					maxResults, expandosValues, notNullJql, fields, nextPageToken, jiraToolConfig);
		} else {
			return advancedJqlSearchGet(
					maxResults, expandosValues, notNullJql, fields, nextPageToken, jiraToolConfig);
		}
	}

	private JiraSearchResponse advancedJqlSearchGet(
			@Nullable Integer maxResults,
			Iterable<String> expandosValues,
			String jql,
			@Nullable Set<String> fields,
			String nextPageToken,
			JiraToolConfig jiraToolConfig)
			throws JSONException, JiraApiException {

		Connection connection =
				jiraToolConfig
						.getConnection()
						.orElseThrow(() -> new JiraApiException("No connection available in JiraToolConfig"));

		String password =
				connection.isBearerToken()
						? jiraCommonService.decryptJiraPassword(connection.getPatOAuthToken())
						: jiraCommonService.decryptJiraPassword(connection.getPassword());

		String expandJoined =
				(expandosValues != null)
						? StreamSupport.stream(expandosValues.spliterator(), false)
								.collect(Collectors.joining(","))
						: null;

		String fieldsJoined = (fields != null && !fields.isEmpty()) ? String.join(",", fields) : null;

		final URI baseUri = buildJqlSearchUri(connection);

		HttpResponse<JsonNode> response =
				Unirest.get(baseUri.toString())
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
		return UriBuilder.fromUri(connection.getBaseUrl()).path(JQL_SEARCH_URL).build();
	}

	private JiraSearchResponse advancedJqlSearchPost(
			@Nullable Integer maxResults,
			Iterable<String> expandosValues,
			String jql,
			@Nullable Set<String> fields,
			String nextPageToken,
			JiraToolConfig jiraToolConfig)
			throws JSONException, JiraApiException {

		Connection connection =
				jiraToolConfig
						.getConnection()
						.orElseThrow(() -> new JiraApiException("No connection available in JiraToolConfig"));

		String password =
				connection.isBearerToken()
						? jiraCommonService.decryptJiraPassword(connection.getPatOAuthToken())
						: jiraCommonService.decryptJiraPassword(connection.getPassword());

		ObjectNode payload = JsonNodeFactory.instance.objectNode();

		String expandJoined =
				(expandosValues != null)
						? StreamSupport.stream(expandosValues.spliterator(), false)
								.collect(Collectors.joining(","))
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

		HttpResponse<JsonNode> response =
				Unirest.post(baseUri.toString())
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
}
