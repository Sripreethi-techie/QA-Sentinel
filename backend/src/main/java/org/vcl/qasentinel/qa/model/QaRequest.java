package org.vcl.qasentinel.qa.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Full autonomous run: optional PRD override; otherwise Jira issue description is used.
 */
public record QaRequest(
		@NotBlank String projectKey,
		@NotBlank String issueKey,
		@NotBlank String envBaseUrl,
		String prdOverride) {
}
