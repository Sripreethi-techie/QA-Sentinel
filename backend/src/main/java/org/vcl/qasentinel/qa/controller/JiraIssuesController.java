package org.vcl.qasentinel.qa.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.vcl.qasentinel.config.QaFlowProperties;
import org.vcl.qasentinel.jira.JiraConfigurationException;
import org.vcl.qasentinel.jira.JiraIssueView;
import org.vcl.qasentinel.qa.model.JiraStoriesResponse;
import org.vcl.qasentinel.qa.service.JiraService;

/**
 * Live Jira data for the UI (dashboard story list). Uses the same story JQL settings as batch QA
 * ({@code qa.flow.batch-story-issue-type-name}, {@code qa.flow.batch-jql-suffix}, {@code qa.flow.batch-max-stories}).
 */
@RestController
@RequestMapping("/api/v1/jira")
@RequiredArgsConstructor
@Slf4j
public class JiraIssuesController {

	private static final String ISSUETYPE_HINT =
			" Confirm the issue type name in Jira (Project settings → Issue types) matches qa.flow.batch-story-issue-type-name (often \"Story\" or \"User Story\").";

	private final JiraService jiraService;
	private final QaFlowProperties qaFlowProperties;

	/**
	 * Always returns HTTP 200 with {@link JiraStoriesResponse}: {@code items} may be empty; {@code error} is non-blank
	 * when Jira is not configured or the search failed (so the UI can show the server message instead of a bare 502).
	 */
	@GetMapping("/projects/{projectKey}/stories")
	public JiraStoriesResponse listStories(@PathVariable("projectKey") String projectKey) {
		try {
			List<JiraIssueView> rows = jiraService.searchStoryIssuesForDashboard(
					projectKey,
					qaFlowProperties.getBatchStoryIssueTypeName(),
					qaFlowProperties.getBatchJqlSuffix(),
					qaFlowProperties.getBatchMaxStories());
			return JiraStoriesResponse.ok(rows);
		}
		catch (JiraConfigurationException e) {
			String msg = e.getMessage() == null || e.getMessage().isBlank()
					? "Jira is not configured (set jira.base-url, jira.email, jira.api-token)."
					: e.getMessage();
			return JiraStoriesResponse.fail(msg);
		}
		catch (IllegalStateException e) {
			log.warn("Jira stories list failed for project {}: {}", projectKey, e.getMessage());
			String msg = e.getMessage() == null || e.getMessage().isBlank()
					? "Jira search failed."
					: e.getMessage();
			if (looksLikeJqlOrIssueTypeProblem(msg)) {
				msg = msg + ISSUETYPE_HINT;
			}
			return JiraStoriesResponse.fail(msg);
		}
	}

	private static boolean looksLikeJqlOrIssueTypeProblem(String msg) {
		if (msg == null) {
			return false;
		}
		String m = msg.toLowerCase();
		if (m.contains("410") || m.contains("has been removed") || m.contains("migrate to")) {
			return false;
		}
		return m.contains("jql")
				|| m.contains("issuetype")
				|| m.contains("does not exist")
				|| m.contains("400")
				|| m.contains("parse")
				|| m.contains("field");
	}
}
