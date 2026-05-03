package org.vcl.qasentinel.qa.model;

import java.util.List;

import org.vcl.qasentinel.jira.JiraIssueView;

/** Payload for {@code GET /api/v1/jira/projects/{projectKey}/stories} — always HTTP 200 for easy UI parsing. */
public record JiraStoriesResponse(List<JiraIssueView> items, String error) {

	public JiraStoriesResponse {
		items = items == null ? List.of() : List.copyOf(items);
		error = error == null ? "" : error.trim();
	}

	public static JiraStoriesResponse ok(List<JiraIssueView> items) {
		return new JiraStoriesResponse(items, "");
	}

	public static JiraStoriesResponse fail(String message) {
		String m = message == null || message.isBlank() ? "Could not load stories from Jira." : message.trim();
		return new JiraStoriesResponse(List.of(), m);
	}
}
