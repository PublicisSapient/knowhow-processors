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
package com.publicissapient.kpidashboard.jira.processor;

import org.bson.types.ObjectId;
import org.codehaus.jettison.json.JSONException;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.publicissapient.kpidashboard.common.model.jira.KanbanJiraIssue;
import com.publicissapient.kpidashboard.jira.model.ProjectConfFieldMapping;

/**
 * @author purgupta2
 */
public interface KanbanJiraIssueProcessor {
	/**
	 * @param issue
	 *          issue
	 * @param projectConfFieldMapping
	 *          projectConfFieldMapping
	 * @param boardId
	 *          boardId
	 * @param processorId
	 * @return KanbanJiraIssue
	 * @throws JSONException
	 *           JSONException
	 */
	KanbanJiraIssue convertToKanbanJiraIssue(Issue issue, ProjectConfFieldMapping projectConfFieldMapping, String boardId,
			ObjectId processorId) throws JSONException;
}
