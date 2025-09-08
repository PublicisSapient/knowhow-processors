package com.publicissapient.kpidashboard.jira.parser;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.publicissapient.kpidashboard.jira.model.JiraSearchResponse;
import com.atlassian.jira.rest.client.internal.json.GenericJsonArrayParser;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.util.Collections;

public class JiraSearchResponseParser {

    public JiraSearchResponse parse(JSONObject json) throws JSONException {
        JSONArray issuesJsonArray = json.getJSONArray("issues");

        Iterable<Issue> issues;
        if (issuesJsonArray.length() > 0) {
            CustomIssueJsonParser issueParser = new CustomIssueJsonParser(
                    json.optJSONObject("names"), json.optJSONObject("schema"));
            GenericJsonArrayParser<Issue> issuesParser = GenericJsonArrayParser.create(issueParser);
            issues = issuesParser.parse(issuesJsonArray);
        } else {
            issues = Collections.emptyList();
        }

        boolean isLast = json.optBoolean("isLast", true);
        String nextPageToken = json.optString("nextPageToken", null);

        return new JiraSearchResponse(issues, isLast, nextPageToken);
    }
}
