package org.vcl.qasentinel.qa.model;

import java.util.List;

/**
 * Outcome of a synchronous autonomous QA run.
 *
 * @param status            {@code PASS}, {@code FAIL}, or {@code ERROR}
 * @param stepsExecuted     Steps that were planned (and attempted for Playwright)
 * @param failureReason     Empty on {@code PASS}; error or Playwright output on failure
 * @param screenshotPath    Path hint when Playwright captured a failure screenshot (may be empty)
 * @param traceId           Correlation id for logs and optional Jira bug text
 * @param jiraBugKey        Created Jira issue key when a bug was filed on failure (empty otherwise)
 * @param failurePageUrl    Browser URL at failure when captured by the runner (empty if unknown)
 * @param failedStep        Step that failed when captured by the runner (null if unknown)
 */
public record QaResult(
		String status,
		List<TestStep> stepsExecuted,
		String failureReason,
		String screenshotPath,
		String traceId,
		String jiraBugKey,
		String failurePageUrl,
		TestStep failedStep) {

	/** Shorter constructor for non-Playwright outcomes (error paths, legacy callers). */
	public QaResult(String status, List<TestStep> stepsExecuted, String failureReason, String screenshotPath, String traceId, String jiraBugKey) {
		this(status, stepsExecuted, failureReason, screenshotPath, traceId, jiraBugKey, "", null);
	}

	public QaResult {
		if (stepsExecuted == null) {
			stepsExecuted = List.of();
		}
		else {
			stepsExecuted = List.copyOf(stepsExecuted);
		}
		if (failureReason == null) {
			failureReason = "";
		}
		if (screenshotPath == null) {
			screenshotPath = "";
		}
		if (traceId == null) {
			traceId = "";
		}
		if (jiraBugKey == null) {
			jiraBugKey = "";
		}
		if (failurePageUrl == null) {
			failurePageUrl = "";
		}
	}

	public boolean passed() {
		return "PASS".equalsIgnoreCase(status);
	}
}
