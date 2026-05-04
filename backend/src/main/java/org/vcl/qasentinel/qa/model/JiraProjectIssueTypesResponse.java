package org.vcl.qasentinel.qa.model;

import java.util.List;

/** Issue type names for a Jira project (for configuring {@code qa.flow.batch-story-issue-type-name}). */
public record JiraProjectIssueTypesResponse(List<String> names, String error) {

	public JiraProjectIssueTypesResponse {
		names = names == null ? List.of() : List.copyOf(names);
		error = error == null ? "" : error.trim();
	}

	public static JiraProjectIssueTypesResponse ok(List<String> names) {
		return new JiraProjectIssueTypesResponse(names, "");
	}

	public static JiraProjectIssueTypesResponse fail(String message) {
		String m = message == null || message.isBlank() ? "Could not load issue types." : message.trim();
		return new JiraProjectIssueTypesResponse(List.of(), m);
	}
}
