package com.publicissapient.kpidashboard.jiratest.model;

import com.atlassian.jira.rest.client.api.domain.Issue;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JiraTestSearchResponse {
	private final Iterable<Issue> issues;
	private boolean isLast;
	private String nextPageToken;
}
