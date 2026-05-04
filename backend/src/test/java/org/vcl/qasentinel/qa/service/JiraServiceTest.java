package org.vcl.qasentinel.qa.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.vcl.qasentinel.qa.model.TestStep;

class JiraServiceTest {

	@Test
	void buildPlaywrightFailureDescriptionIncludesSections() {
		TestStep failing = new TestStep(2, "verify", "see welcome", "welcome", null, "text visible");
		String desc = JiraService.buildPlaywrightFailureDescription(
				"DEMO-1",
				"trace-abc",
				List.of(new TestStep(1, "navigate", "Open https://x"), new TestStep(2, "verify", "see body")),
				"Timeout",
				"qa-runner/test-results/x.png",
				"https://app.example/login?err=1",
				failing,
				"Locator timeout waiting for welcome",
				"2026-01-15T10:00:00Z",
				"test-results/dynamic-failure-trace.zip",
				"test-results/dynamic-failure-console.log",
				"https://acme.atlassian.net/browse/DEMO-1");
		assertThat(desc).contains("QA Sentinel");
		assertThat(desc).contains("Source user story / issue key: DEMO-1");
		assertThat(desc).contains("Open this story in Jira: https://acme.atlassian.net/browse/DEMO-1");
		assertThat(desc).contains("Correlation ID (service logs): trace-abc");
		assertThat(desc).contains("Steps");
		assertThat(desc).contains("1. navigate");
		assertThat(desc).contains("2. verify");
		assertThat(desc).contains("Failed step (first breaking action)");
		assertThat(desc).contains("see welcome");
		assertThat(desc).contains("Expected");
		assertThat(desc).contains("text visible");
		assertThat(desc).contains("Actual");
		assertThat(desc).contains("Locator timeout waiting for welcome");
		assertThat(desc).contains("Additional runner output");
		assertThat(desc).contains("Timeout");
		assertThat(desc).contains("Evidence");
		assertThat(desc).contains("https://app.example/login?err=1");
		assertThat(desc).contains("2026-01-15T10:00:00Z");
		assertThat(desc).contains("x.png");
		assertThat(desc).contains("playwright show-trace");
		assertThat(desc).contains("Jira linkage");
		assertThat(desc).contains("Linked work");
		assertThat(desc).contains("dynamic-failure.png");
	}

	@Test
	void screenshotFileNameOnlyExtractsBasename() {
		assertThat(JiraService.screenshotFileNameOnly("qa-runner/test-results/dynamic-failure.png"))
				.isEqualTo("dynamic-failure.png");
		assertThat(JiraService.screenshotFileNameOnly("")).isEqualTo("(none)");
	}
}
