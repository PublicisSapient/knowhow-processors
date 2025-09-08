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
