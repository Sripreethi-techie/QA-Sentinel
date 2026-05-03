package org.vcl.qasentinel.qa.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** External Playwright (CI) posts outcome; failure may create a Jira Task when Jira is configured. */
public record QaReportRequest(
		@NotBlank String projectKey,
		@NotBlank String issueKey,
		String traceId,
		@NotNull Boolean passed,
		String message,
		String screenshotHint) {
}
