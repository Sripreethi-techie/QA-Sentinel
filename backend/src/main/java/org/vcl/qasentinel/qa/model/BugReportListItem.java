package org.vcl.qasentinel.qa.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Bug filed from a failed QA run, exposed to the Sentinel UI.
 *
 * @param screenshotUrl Relative URL to fetch PNG when {@link #screenshotPath} is resolvable (may be null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BugReportListItem(
		String id,
		String title,
		String jiraKey,
		String jiraBrowseUrl,
		String linkedIssueKey,
		String runStatus,
		String traceId,
		String failureReason,
		String screenshotPath,
		String screenshotUrl,
		String failedStepSummary,
		TestStep failedStep,
		String recordedAt) {}
