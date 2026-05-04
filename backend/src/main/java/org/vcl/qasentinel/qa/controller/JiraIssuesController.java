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
import org.vcl.qasentinel.qa.diagnostics.StoryFetchDiagnosticsHolder;
import org.vcl.qasentinel.qa.model.JiraProjectIssueTypesResponse;
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
	private final StoryFetchDiagnosticsHolder storyFetchDiagnosticsHolder;

	/**
	 * Always returns HTTP 200 with {@link JiraStoriesResponse}: {@code items} may be empty; {@code error} is non-blank
	 * when Jira is not configured, the search failed, or the list is empty after a successful call (so mis-tuned JQL
	 * is visible in the UI).
	 */
	@GetMapping("/projects/{projectKey}/stories")
	public JiraStoriesResponse listStories(@PathVariable("projectKey") String projectKey) {
		try {
			List<JiraIssueView> rows = jiraService.searchStoryIssuesForDashboard(
					projectKey,
					qaFlowProperties.getBatchStoryIssueTypeName(),
					qaFlowProperties.getBatchJqlSuffix(),
					qaFlowProperties.getBatchMaxStories());
			if (rows.isEmpty()) {
				storyFetchDiagnosticsHolder.recordSuccess();
				return new JiraStoriesResponse(rows, emptyStoriesHint(projectKey));
			}
			storyFetchDiagnosticsHolder.recordSuccess();
			return JiraStoriesResponse.ok(rows);
		}
		catch (JiraConfigurationException e) {
			String msg = e.getMessage() == null || e.getMessage().isBlank()
					? "Jira is not configured (set JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN or jira.* in application-local.properties)."
					: e.getMessage();
			storyFetchDiagnosticsHolder.recordError(msg);
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
			storyFetchDiagnosticsHolder.recordError(msg);
			return JiraStoriesResponse.fail(msg);
		}
	}

	/** Lists Jira issue type names for {@code projectKey} (use to set {@code qa.flow.batch-story-issue-type-name}). */
	@GetMapping("/projects/{projectKey}/issue-types")
	public JiraProjectIssueTypesResponse listIssueTypes(@PathVariable("projectKey") String projectKey) {
		try {
			return JiraProjectIssueTypesResponse.ok(jiraService.listIssueTypeNamesForProject(projectKey));
		}
		catch (JiraConfigurationException e) {
			String msg = e.getMessage() == null || e.getMessage().isBlank() ? "Jira is not configured." : e.getMessage();
			return JiraProjectIssueTypesResponse.fail(msg);
		}
		catch (IllegalStateException e) {
			return JiraProjectIssueTypesResponse.fail(e.getMessage());
		}
		catch (Exception e) {
			log.warn("Issue types list failed: {}", e.getMessage());
			return JiraProjectIssueTypesResponse.fail(e.getMessage() == null ? "Unexpected error." : e.getMessage());
		}
	}

	private String emptyStoriesHint(String projectKey) {
		String pk = projectKey == null ? "" : projectKey.trim();
		String type = qaFlowProperties.getBatchStoryIssueTypeName() == null
				? ""
				: qaFlowProperties.getBatchStoryIssueTypeName().trim();
		return "No Jira issues returned for project "
				+ pk
				+ ". The server tried issuetype \""
				+ type
				+ "\" plus a broad project-only fallback (see JiraService). Check qa.flow.batch-story-issue-type-name, "
				+ "qa.flow.batch-jql-suffix, and GET /api/v1/jira/projects/"
				+ pk
				+ "/issue-types for names that exist in your site.";
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
