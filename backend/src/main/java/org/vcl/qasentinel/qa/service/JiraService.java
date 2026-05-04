package org.vcl.qasentinel.qa.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.vcl.qasentinel.config.JiraProperties;
import org.vcl.qasentinel.config.PlaywrightProperties;
import org.vcl.qasentinel.config.QaFlowProperties;
import org.vcl.qasentinel.jira.JiraAssigneeResolver;
import org.vcl.qasentinel.jira.JiraBugService;
import org.vcl.qasentinel.jira.JiraIssuePrd;
import org.vcl.qasentinel.jira.JiraIssueView;
import org.vcl.qasentinel.jira.JiraSearchClient;
import org.vcl.qasentinel.qa.model.TestStep;

@Service
@RequiredArgsConstructor
@Slf4j
public class JiraService {

	public static final String QA_SENTINEL_BUG_SUMMARY = "Auto-detected bug from QA Sentinel";

	private final JiraSearchClient jiraSearchClient;
	private final JiraBugService jiraBugService;
	private final JiraAssigneeResolver jiraAssigneeResolver;
	private final PlaywrightProperties playwrightProperties;
	private final QaFlowProperties qaFlowProperties;
	private final JiraProperties jiraProperties;

	public List<String> listIssueTypeNamesForProject(String projectKey) {
		return jiraSearchClient.listIssueTypeNamesForProject(projectKey);
	}

	public List<JiraIssueView> searchIssues(String projectKey) {
		return jiraSearchClient.searchByProject(projectKey);
	}

	public JiraIssuePrd fetchPrdForIssue(String issueKey) {
		JiraIssuePrd prd = jiraSearchClient.fetchIssuePrd(issueKey);
		String ac = prd.acceptanceCriteriaPlain() == null ? "" : prd.acceptanceCriteriaPlain().trim();
		if (ac.isEmpty()) {
			log.info(
					"Jira PRD for {} — Acceptance Criteria: (empty; set jira.acceptance-criteria-field-ids to ingest AC custom field(s))",
					prd.issueKey());
		}
		else {
			int maxLog = 4000;
			if (ac.length() > maxLog) {
				log.info(
						"Jira PRD for {} — Acceptance Criteria ({} chars, first {} logged):\n{}…",
						prd.issueKey(),
						ac.length(),
						maxLog,
						ac.substring(0, maxLog));
			}
			else {
				log.info("Jira PRD for {} — Acceptance Criteria ({} chars):\n{}", prd.issueKey(), ac.length(), ac);
			}
		}
		return prd;
	}

	/**
	 * External CI / generic failure body with POC-style summary (relates to issue in summary line).
	 */
	public String fileBug(String projectKey, String relatedIssueKey, String traceId, String bodyText, String screenshotHint) {
		String body = bodyText == null ? "" : bodyText;
		if (screenshotHint != null && !screenshotHint.isBlank()) {
			body = body + "\nScreenshot: " + screenshotHint;
		}
		List<Path> attachments = new ArrayList<>();
		Path screenshotFile = resolvePlaywrightScreenshot(screenshotHint);
		if (screenshotFile != null) {
			attachments.add(screenshotFile);
		}
		return jiraBugService.createPocFinding(
				projectKey, relatedIssueKey, traceId, body, attachments, resolveDefaultAssigneeAccountId());
	}

	/**
	 * Story / user-story keys for batch QA (JQL: project + issuetype + optional suffix).
	 */
	public List<String> searchStoryIssueKeys(String projectKey, String issueTypeName, String jqlSuffix, int maxResults) {
		String pk = projectKey == null ? "" : projectKey.trim().toUpperCase(Locale.ROOT);
		if (pk.isBlank()) {
			return List.of();
		}
		String type = resolveStoryIssueTypeName(issueTypeName);
		String quotedType = jqlQuotedString(type);
		String suffix = jqlSuffix == null || jqlSuffix.isBlank() ? "" : " " + jqlSuffix.trim();
		String jql = "project = " + pk + " AND issuetype = " + quotedType + suffix + " ORDER BY created DESC";
		List<String> keys = jiraSearchClient.searchIssueKeys(jql, maxResults);
		if (keys.isEmpty()) {
			log.info("No stories found with issueType filter, retrying without filter...");
			keys = jiraSearchClient.searchIssueKeys("project = " + pk + " ORDER BY created DESC", maxResults);
		}
		return keys;
	}

	/**
	 * Dashboard: user stories in a project (same JQL as {@link #searchStoryIssueKeys} but returns full rows).
	 */
	public List<JiraIssueView> searchStoryIssuesForDashboard(
			String projectKey,
			String issueTypeName,
			String jqlSuffix,
			int maxResults) {
		String pk = projectKey == null ? "" : projectKey.trim().toUpperCase(Locale.ROOT);
		if (pk.isBlank()) {
			return List.of();
		}
		String type = resolveStoryIssueTypeName(issueTypeName);
		String quotedType = jqlQuotedString(type);
		String suffix = jqlSuffix == null || jqlSuffix.isBlank() ? "" : " " + jqlSuffix.trim();
		String jql = "project = " + pk + " AND issuetype = " + quotedType + suffix + " ORDER BY created DESC";
		List<JiraIssueView> issues = jiraSearchClient.searchByJql(jql, maxResults);
		if (issues.isEmpty()) {
			log.info("No stories found with issueType filter, retrying without filter...");
			issues = jiraSearchClient.searchByJql("project = " + pk + " ORDER BY created DESC", maxResults);
		}
		return issues;
	}

	/** Issue type for JQL {@code issuetype = "…"} — must match Jira exactly (often {@code Story}). */
	private String resolveStoryIssueTypeName(String issueTypeName) {
		if (issueTypeName != null && !issueTypeName.isBlank()) {
			return issueTypeName.trim();
		}
		String fromConfig = qaFlowProperties.getBatchStoryIssueTypeName();
		if (fromConfig != null && !fromConfig.isBlank()) {
			return fromConfig.trim();
		}
		return "Story";
	}

	private static String jqlQuotedString(String value) {
		String v = value == null ? "" : value;
		return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private String resolveDefaultAssigneeAccountId() {
		return jiraAssigneeResolver.resolveDefaultBugAssigneeAccountId().orElse(null);
	}

	/**
	 * Playwright failure from the synchronous QA flow: QA-style description (Steps / Expected / Actual / Evidence)
	 * and attachments (screenshot, trace.zip, optional console log) when present on disk.
	 */
	public String fileQaSentinelPlaywrightBug(
			String projectKey,
			String relatedIssueKey,
			String traceId,
			List<TestStep> steps,
			String failureReason,
			String screenshotHint,
			String failurePageUrl,
			TestStep failedStep,
			String exactError,
			String failureTimestampUtc,
			String traceZipHint,
			String consoleLogHint) {
		String description = buildPlaywrightFailureDescription(
				relatedIssueKey,
				traceId,
				steps,
				failureReason,
				screenshotHint,
				failurePageUrl,
				failedStep,
				exactError,
				failureTimestampUtc,
				traceZipHint,
				consoleLogHint,
				storyJiraBrowseUrl(relatedIssueKey));
		String summary = buildQaSentinelBugSummary(relatedIssueKey, failureReason);
		List<Path> attachments = new ArrayList<>();
		Path screenshotFile = resolvePlaywrightScreenshot(screenshotHint);
		if (screenshotFile != null) {
			attachments.add(screenshotFile);
		}
		Path traceFile = resolvePlaywrightArtifact(traceZipHint, "test-results/dynamic-failure-trace.zip");
		if (traceFile != null) {
			attachments.add(traceFile);
		}
		Path consoleFile = resolvePlaywrightArtifact(consoleLogHint, "test-results/dynamic-failure-console.log");
		if (consoleFile != null) {
			attachments.add(consoleFile);
		}
		return jiraBugService.createQaSentinelBug(
				projectKey,
				summary,
				description,
				relatedIssueKey,
				attachments,
				resolveDefaultAssigneeAccountId());
	}

	/** Jira browse URL for the user story under test (so the bug description links back to the story). */
	private String storyJiraBrowseUrl(String relatedIssueKey) {
		if (relatedIssueKey == null || relatedIssueKey.isBlank()) {
			return "";
		}
		String base = jiraProperties.getApiBaseUrl();
		if (base == null || base.isBlank()) {
			return "";
		}
		return base.trim().replaceAll("/+$", "") + "/browse/" + relatedIssueKey.trim();
	}

	/**
	 * Bug summary: ties the ticket to the story in lists and search (Jira caps length in {@link JiraBugService}).
	 */
	private static String buildQaSentinelBugSummary(String relatedIssueKey, String failureReason) {
		String key = relatedIssueKey == null || relatedIssueKey.isBlank() ? "story" : relatedIssueKey.trim();
		String one = failureReason == null ? "" : failureReason.replace('\r', ' ').replace('\n', ' ').trim();
		if (one.length() > 100) {
			one = one.substring(0, 97) + "...";
		}
		String tail = one.isEmpty() ? "Playwright regression failure" : one;
		return "QA Sentinel — " + key + " — " + tail;
	}

	/**
	 * Resolves Playwright failure screenshot to an on-disk path for Jira multipart upload.
	 * Uses {@code test-results/dynamic-failure.png} under the configured working directory, then path hints
	 * normalized with {@link PlaywrightArtifactPathResolver} (legacy {@code qa-runner/test-results/…} included).
	 */
	Path resolvePlaywrightScreenshot(String screenshotHint) {
		File wd = new File(playwrightProperties.getWorkingDir()).getAbsoluteFile();
		Path root = wd.toPath().toAbsolutePath().normalize();
		Path underRunner = root.resolve("test-results").resolve("dynamic-failure.png");
		if (Files.isRegularFile(underRunner)) {
			return underRunner;
		}
		return PlaywrightArtifactPathResolver.resolveExisting(root, screenshotHint).orElse(null);
	}

	/**
	 * Resolves optional artifacts (Playwright trace zip, console log) next to the runner working directory.
	 */
	Path resolvePlaywrightArtifact(String hint, String defaultRelativeToRunner) {
		File wd = new File(playwrightProperties.getWorkingDir()).getAbsoluteFile();
		Path root = wd.toPath().toAbsolutePath().normalize();
		Path underRunner = root.resolve(defaultRelativeToRunner.replace("\\", "/"));
		if (Files.isRegularFile(underRunner)) {
			return underRunner;
		}
		return PlaywrightArtifactPathResolver.resolveExisting(root, hint).orElse(null);
	}

	static String buildPlaywrightFailureDescription(
			String relatedIssueKey,
			String traceId,
			List<TestStep> steps,
			String failureReason,
			String screenshotHint,
			String failurePageUrl,
			TestStep failedStep,
			String exactError,
			String failureTimestampUtc,
			String traceZipHint,
			String consoleLogHint,
			String storyBrowseUrl) {
		String story = relatedIssueKey == null || relatedIssueKey.isBlank() ? "(none)" : relatedIssueKey.trim();
		String corr = traceId == null || traceId.isBlank() ? "(none)" : traceId.trim();
		String url = failurePageUrl == null ? "" : failurePageUrl.trim();
		String ts = failureTimestampUtc == null || failureTimestampUtc.isBlank()
				? Instant.now().toString()
				: failureTimestampUtc.trim();
		String exact = exactError == null ? "" : exactError.trim();
		String reason = failureReason == null ? "" : failureReason.trim();
		String browse = storyBrowseUrl == null ? "" : storyBrowseUrl.trim();

		StringBuilder sb = new StringBuilder();
		sb.append("QA Sentinel — automated regression failure\n\n");
		sb.append("Context\n\n");
		sb.append("Source user story / issue key: ").append(story).append("\n\n");
		if (!browse.isEmpty()) {
			sb.append("Open this story in Jira: ").append(browse).append("\n\n");
		}
		sb.append("Correlation ID (service logs): ").append(corr);

		sb.append("\n\nSteps\n\n");
		if (steps == null || steps.isEmpty()) {
			sb.append("(no structured steps were recorded for this run.)");
		}
		else {
			sb.append(steps.stream().map(JiraService::formatStepLine).collect(Collectors.joining("\n\n")));
		}
		if (failedStep != null) {
			sb.append("\n\nFailed step (first breaking action)\n\n");
			sb.append(formatStepLine(failedStep));
		}

		sb.append("\n\nExpected\n\n");
		if (failedStep != null && failedStep.expected() != null && !failedStep.expected().isBlank()) {
			sb.append(failedStep.expected().trim());
			sb.append("\n\n");
		}
		sb.append(
				"Overall: all planned automation steps complete without assertion, navigation, or API errors, "
						+ "consistent with the linked story and its acceptance criteria.");

		sb.append("\n\nActual\n\n");
		if (!exact.isEmpty()) {
			sb.append(exact);
			if (!reason.isEmpty() && !reason.equals(exact)) {
				sb.append("\n\nAdditional runner output:\n").append(reason);
			}
		}
		else if (!reason.isEmpty()) {
			sb.append(reason);
		}
		else {
			sb.append("(failure details were not captured — see Evidence and server logs.)");
		}

		sb.append("\n\nEvidence\n\n");
		sb.append("- URL at failure: ").append(url.isEmpty() ? "(not captured)" : url);
		sb.append("\n\n- Timestamp (UTC): ").append(ts);
		sb.append("\n\n- Exact error (runner): ").append(exact.isEmpty() ? "(see Actual above or logs)" : exact);
		sb.append("\n\n- Attachments (when files exist on the QA host they are uploaded to this issue):\n");
		sb.append("  • Screenshot: ").append(screenshotFileNameOnly(screenshotHint)).append('\n');
		sb.append("  • Playwright trace (ZIP): ")
				.append(artifactNameOrDefault(traceZipHint, "dynamic-failure-trace.zip"))
				.append(" — view locally: npx playwright show-trace <file>\n");
		sb.append("  • Browser console export (optional): ")
				.append(artifactNameOrDefault(consoleLogHint, "dynamic-failure-console.log"));
		sb.append("\n\n- Jira linkage: this bug is posted under / linked to user story ")
				.append(story)
				.append(" using the configured issue link type (see Linked work / Issue links on both the bug and the story in Jira).");
		sb.append("\nA failure screenshot is attached to this bug when Playwright saved dynamic-failure.png.");
		return sb.toString();
	}

	private static String artifactNameOrDefault(String hint, String defaultFileName) {
		if (hint == null || hint.isBlank()) {
			return defaultFileName + " (if produced)";
		}
		String n = screenshotFileNameOnly(hint);
		return n.isEmpty() || "(none)".equals(n) ? defaultFileName : n;
	}

	static String screenshotFileNameOnly(String screenshotHint) {
		if (screenshotHint == null || screenshotHint.isBlank()) {
			return "(none)";
		}
		Path p = Paths.get(screenshotHint.trim());
		Path name = p.getFileName();
		return name == null ? screenshotHint.trim() : name.toString();
	}

	private static String formatStepLine(TestStep s) {
		StringBuilder line = new StringBuilder();
		line.append(s.stepNumber()).append(". ").append(s.action() == null ? "" : s.action());
		if (s.description() != null && !s.description().isBlank()) {
			line.append(" — ").append(s.description().trim());
		}
		if (s.target() != null && !s.target().isBlank()) {
			line.append(" [target: ").append(s.target().trim()).append(']');
		}
		if (s.value() != null && !s.value().isBlank()) {
			line.append(" [value: ").append(s.value().trim()).append(']');
		}
		if (s.expected() != null && !s.expected().isBlank()) {
			line.append(" [expected: ").append(s.expected().trim()).append(']');
		}
		return line.toString();
	}
}
