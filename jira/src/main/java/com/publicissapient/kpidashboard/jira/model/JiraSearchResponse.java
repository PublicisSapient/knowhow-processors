package com.publicissapient.kpidashboard.jira.model;

import com.atlassian.jira.rest.client.api.domain.Issue;
import lombok.Data;

@Data
public class JiraSearchResponse {
    private final Iterable<Issue> issues;
    private boolean isLast;
    private String nextPageToken;

    public JiraSearchResponse(Iterable<Issue> issues, boolean isLast, String nextPageToken) {
        this.issues = issues;
        this.isLast = isLast;
        this.nextPageToken = nextPageToken;
    }
}

