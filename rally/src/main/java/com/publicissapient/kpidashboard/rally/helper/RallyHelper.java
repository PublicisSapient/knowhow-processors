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

package com.publicissapient.kpidashboard.rally.helper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.joda.time.DateTime;
import org.json.simple.JSONArray;
import org.springframework.stereotype.Component;

import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.ChangelogGroup;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.User;
import com.atlassian.jira.rest.client.api.domain.Version;
import com.google.common.collect.Lists;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import com.publicissapient.kpidashboard.rally.model.HierarchicalRequirement;
import com.publicissapient.kpidashboard.rally.model.RallyResponse;
import com.publicissapient.kpidashboard.rally.util.JiraProcessorUtil;

import lombok.extern.slf4j.Slf4j;
/**
 * @author girpatha
 */
@Slf4j
@Component
public class RallyHelper {


	private RallyHelper() {
	}
	public static final Comparator<SprintDetails> SPRINT_COMPARATOR = (SprintDetails o1, SprintDetails o2) -> {
		int cmp1 = ObjectUtils.compare(o1.getStartDate(), o2.getStartDate());
		if (cmp1 != 0) {
			return cmp1;
		}
		return ObjectUtils.compare(o1.getEndDate(), o2.getEndDate());
	};

	public static String getFieldValue(String customFieldId, Map<String, IssueField> fields) {
		Object fieldValue = fields.get(customFieldId).getValue();
		try {
			if (fieldValue instanceof Double doubleValue) {
				return doubleValue.toString();
			} else if (fieldValue instanceof JSONObject jsonObject) {
				return jsonObject.getString(RallyConstants.VALUE);
			} else if (fieldValue instanceof String stringValue) {
				return stringValue;
			}
		} catch (JSONException e) {
			log.error("RALLY Processor | Error while parsing RCA Custom_Field", e);
		}
		return fieldValue != null ? fieldValue.toString() : null;
	}

	public static List<ChangelogGroup> sortChangeLogGroup(Issue issue) {
		Iterable<ChangelogGroup> changelogItr = issue.getChangelog();
		List<ChangelogGroup> changeLogList = new ArrayList<>();
		if (null != changelogItr) {
			changeLogList = Lists.newArrayList(changelogItr.iterator());
			changeLogList.sort((ChangelogGroup obj1, ChangelogGroup obj2) -> {
				DateTime activityDate1 = obj1.getCreated();
				DateTime activityDate2 = obj2.getCreated();
				return activityDate1.compareTo(activityDate2);
			});
		}
		return changeLogList;
	}

	public static List<HierarchicalRequirement> getIssuesFromResult(RallyResponse rallyResponse) {
		if (rallyResponse != null) {
			return Lists.newArrayList(rallyResponse.getQueryResult().getResults());
		}
		return new ArrayList<>();
	}

	public static String hash(String input) {
		return String.valueOf(Objects.hash(input));
	}

	public static String getAssignee(User user) {
		String userId = "";
		String query = user.getSelf().getQuery();
		if (StringUtils.isNotEmpty(query) && (query.contains("accountId") || query.contains("username"))) {
			userId = query.split("=")[1];
		}
		return userId;
	}

	public static Collection getListFromJson(IssueField issueField) {
		Object value = issueField.getValue();
		final List<Object> list = new ArrayList<>();
		if (value instanceof JSONArray jsonArray) {
			jsonArray.forEach(v -> {
				try {
					list.add(((JSONObject) v).get(RallyConstants.VALUE));
				} catch (JSONException e) {
					log.error("RALLY PROCESSOR | Error while parsing Atlassian Issue JSON Object", e);
				}
			});
		} else if (value instanceof JSONObject jsonObject) {
			try {
				list.add(jsonObject.get(RallyConstants.VALUE));
			} catch (JSONException e) {
				log.error("RALLY PROCESSOR | Error while parsing Atlassian Issue JSON Object", e);
			}
		}
		return list;
	}

	public static String convertDateToCustomFormat(long currentTimeMillis) {
		Date inputDate = new Date(currentTimeMillis);
		SimpleDateFormat outputFormat = new SimpleDateFormat("MMMM dd, yyyy, EEEE, hh:mm:ss a");
		return outputFormat.format(inputDate);
	}
}
