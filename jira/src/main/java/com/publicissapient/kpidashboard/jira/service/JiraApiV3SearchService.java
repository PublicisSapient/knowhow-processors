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

import java.util.Set;
import javax.annotation.Nullable;

import org.codehaus.jettison.json.JSONException;

import com.publicissapient.kpidashboard.jira.exception.JiraApiException;
import com.publicissapient.kpidashboard.jira.model.JiraSearchResponse;
import com.publicissapient.kpidashboard.jira.model.JiraToolConfig;

/**
 * Service interface for JIRA API v3 search operations. This service provides methods to fetch
 * issues using the new JIRA API v3 /search/jql endpoint which uses token-based pagination instead
 * of offset-based pagination.
 */
public interface JiraApiV3SearchService {

	/**
	 * Executes JQL search using JIRA API v3 advanced search endpoint
	 *
	 * @param jql JQL query string
	 * @param maxResults Maximum number of results per page
	 * @param fields Set of fields to include in the response
	 * @param nextPageToken Token for pagination (token-based pagination)
	 * @param jiraToolConfig JIRA tool configuration
	 * @return JiraSearchResponse containing issues and pagination info
	 * @throws JSONException if JSON parsing fails
	 * @throws JiraApiException if API call fails
	 */
	JiraSearchResponse searchJql(
			@Nullable String jql,
			@Nullable Integer maxResults,
			@Nullable Set<String> fields,
			String nextPageToken,
			JiraToolConfig jiraToolConfig)
			throws JSONException, JiraApiException;
}
