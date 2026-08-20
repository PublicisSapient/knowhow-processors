package com.publicissapient.kpidashboard.jiratest.service;

import java.util.Set;
import javax.annotation.Nullable;

import org.codehaus.jettison.json.JSONException;

import com.publicissapient.kpidashboard.common.model.processortool.ProcessorToolConnection;
import com.publicissapient.kpidashboard.jiratest.exception.JiraTestApiException;
import com.publicissapient.kpidashboard.jiratest.model.JiraTestSearchResponse;

public interface JiraTestApiV3SearchService {

	JiraTestSearchResponse searchJql(
			@Nullable String jql,
			@Nullable Integer maxResults,
			@Nullable Set<String> fields,
			String nextPageToken,
			ProcessorToolConnection processorToolConnection)
			throws JSONException, JiraTestApiException;
}
