package com.publicissapient.kpidashboard.jiratest.adapter.atlassianbespoke.parser;

import java.util.Collections;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.internal.json.GenericJsonArrayParser;
import com.publicissapient.kpidashboard.jiratest.model.JiraTestSearchResponse;

public class JiraTestSearchResponseParser {

	public JiraTestSearchResponse parse(JSONObject json) throws JSONException {
		JSONArray issuesJsonArray = json.optJSONArray("issues");

		Iterable<Issue> issues;
		if (issuesJsonArray != null && issuesJsonArray.length() > 0) {
			JSONObject names = json.optJSONObject("names");
			JSONObject schema = json.optJSONObject("schema");
			CustomIssueJsonParser issueParser =
					new CustomIssueJsonParser(
							names != null ? names : new JSONObject(), schema != null ? schema : new JSONObject());
			GenericJsonArrayParser<Issue> issuesParser = GenericJsonArrayParser.create(issueParser);
			issues = issuesParser.parse(issuesJsonArray);
		} else {
			issues = Collections.emptyList();
		}

		boolean isLast = json.optBoolean("isLast", true);
		String nextPageToken = json.has("nextPageToken") ? json.getString("nextPageToken") : null;

		return new JiraTestSearchResponse(issues, isLast, nextPageToken);
	}
}
