package org.vcl.qasentinel.qa.model;

/** Integration readiness for dashboard status panel. */
public record IntegrationHealthResponse(
		boolean jiraConfigured,
		boolean jiraReachable,
		boolean groqConfigured,
		boolean playwrightRunnerReady,
		String lastStoryFetchError,
		String detail) {

	public IntegrationHealthResponse {
		lastStoryFetchError = lastStoryFetchError == null ? "" : lastStoryFetchError.trim();
		detail = detail == null ? "" : detail.trim();
	}
}
