package com.publicissapient.kpidashboard.jiratest.service;

import static com.atlassian.jira.rest.client.api.IssueRestClient.Expandos.CHANGELOG;
import static com.atlassian.jira.rest.client.api.IssueRestClient.Expandos.NAMES;
import static com.atlassian.jira.rest.client.api.IssueRestClient.Expandos.SCHEMA;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.publicissapient.kpidashboard.common.model.processortool.ProcessorToolConnection;
import com.publicissapient.kpidashboard.common.service.AesEncryptionService;
import com.publicissapient.kpidashboard.jiratest.adapter.atlassianbespoke.parser.JiraTestSearchResponseParser;
import com.publicissapient.kpidashboard.jiratest.config.JiraTestProcessorConfig;
import com.publicissapient.kpidashboard.jiratest.exception.JiraTestApiException;
import com.publicissapient.kpidashboard.jiratest.model.JiraTestSearchResponse;
import com.publicissapient.kpidashboard.jiratest.util.JiraConstants;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraTestApiV3SearchServiceImpl implements JiraTestApiV3SearchService {

	private static final Function<com.atlassian.jira.rest.client.api.IssueRestClient.Expandos, String>
			EXPANDO_TO_PARAM = from -> from.name().toLowerCase(); // NOSONAR

	private static final String JQL_SEARCH_PATH = "/rest/api/latest/search/jql";
	private static final String ACCEPT = "accept";
	private static final String APPLICATION_JSON = "application/json";
	private static final String CONTENT_TYPE = "Content-Type";

	private final AesEncryptionService aesEncryptionService;
	private final JiraTestProcessorConfig jiraTestProcessorConfig;

	@Override
	public JiraTestSearchResponse searchJql(
			@Nullable String jql,
			@Nullable Integer maxResults,
			@Nullable Set<String> fields,
			String nextPageToken,
			ProcessorToolConnection processorToolConnection)
			throws JSONException, JiraTestApiException {

		final Iterable<String> expandosValues =
				Iterables.transform(List.of(SCHEMA, NAMES, CHANGELOG), EXPANDO_TO_PARAM);
		final String notNullJql = StringUtils.defaultString(jql);

		if (notNullJql.length() > JiraConstants.MAX_JQL_LENGTH_FOR_HTTP_GET) {
			return searchPost(
					maxResults, expandosValues, notNullJql, fields, nextPageToken, processorToolConnection);
		} else {
			return searchGet(
					maxResults, expandosValues, notNullJql, fields, nextPageToken, processorToolConnection);
		}
	}

	private JiraTestSearchResponse searchGet(
			@Nullable Integer maxResults,
			Iterable<String> expandosValues,
			String jql,
			@Nullable Set<String> fields,
			String nextPageToken,
			ProcessorToolConnection processorToolConnection)
			throws JSONException, JiraTestApiException {

		String password = decryptPassword(processorToolConnection);
		String expandJoined = joinExpandos(expandosValues);
		String fieldsJoined = (fields != null && !fields.isEmpty()) ? String.join(",", fields) : null;
		URI searchUri = buildSearchUri(processorToolConnection);

		HttpResponse<JsonNode> response =
				Unirest.get(searchUri.toString())
						.basicAuth(processorToolConnection.getUsername(), password)
						.header(ACCEPT, APPLICATION_JSON)
						.queryString(JiraConstants.JQL_ATTRIBUTE, jql)
						.queryString(JiraConstants.FIELDS_BY_KEYS_ATTRIBUTE, true)
						.queryString(JiraConstants.MAX_RESULTS_ATTRIBUTE, maxResults)
						.queryString(JiraConstants.NEXT_PAGE_TOKEN_ATTRIBUTE, nextPageToken)
						.queryString(JiraConstants.EXPAND_ATTRIBUTE, expandJoined)
						.queryString(JiraConstants.FIELDS_ATTRIBUTE, fieldsJoined)
						.asJson();

		if (response.getStatus() != 200) {
			throw new JiraTestApiException("Failed to fetch issues: HTTP " + response.getStatus());
		}

		kong.unirest.json.JSONObject jsonFromUnirest = response.getBody().getObject();
		org.codehaus.jettison.json.JSONObject jsonObject =
				new org.codehaus.jettison.json.JSONObject(jsonFromUnirest.toString());

		return new JiraTestSearchResponseParser().parse(jsonObject);
	}

	private JiraTestSearchResponse searchPost(
			@Nullable Integer maxResults,
			Iterable<String> expandosValues,
			String jql,
			@Nullable Set<String> fields,
			String nextPageToken,
			ProcessorToolConnection processorToolConnection)
			throws JSONException, JiraTestApiException {

		String password = decryptPassword(processorToolConnection);
		URI searchUri = buildSearchUri(processorToolConnection);

		ObjectNode payload = JsonNodeFactory.instance.objectNode();

		String expandJoined = joinExpandos(expandosValues);
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

		HttpResponse<JsonNode> response =
				Unirest.post(searchUri.toString())
						.basicAuth(processorToolConnection.getUsername(), password)
						.header(ACCEPT, APPLICATION_JSON)
						.header(CONTENT_TYPE, APPLICATION_JSON)
						.body(payload.toString())
						.asJson();

		if (response.getStatus() != 200) {
			throw new JiraTestApiException("Failed to fetch issues: HTTP " + response.getStatus());
		}

		kong.unirest.json.JSONObject jsonFromUnirest = response.getBody().getObject();
		org.codehaus.jettison.json.JSONObject jsonObject =
				new org.codehaus.jettison.json.JSONObject(jsonFromUnirest.toString());

		return new JiraTestSearchResponseParser().parse(jsonObject);
	}

	private URI buildSearchUri(ProcessorToolConnection processorToolConnection) {
		return UriBuilder.fromUri(processorToolConnection.getUrl()).path(JQL_SEARCH_PATH).build();
	}

	private String decryptPassword(ProcessorToolConnection processorToolConnection) {
		String encrypted =
				processorToolConnection.isBearerToken()
						? processorToolConnection.getPatOAuthToken()
						: processorToolConnection.getPassword();
		return aesEncryptionService.decrypt(encrypted, jiraTestProcessorConfig.getAesEncryptionKey());
	}

	private String joinExpandos(Iterable<String> expandosValues) {
		if (expandosValues == null) {
			return null;
		}
		return StreamSupport.stream(expandosValues.spliterator(), false)
				.collect(Collectors.joining(","));
	}
}
