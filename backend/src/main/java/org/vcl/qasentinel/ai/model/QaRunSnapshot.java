package org.vcl.qasentinel.ai.model;

import java.time.Instant;

import org.vcl.qasentinel.qa.model.QaResult;
import org.vcl.qasentinel.qa.model.TestStep;

/**
 * Compact view of a completed QA run for the AI agent and observability.
 */
public record QaRunSnapshot(
		Instant at,
		String issueKey,
		String status,
		String traceId,
		String failureReason,
		String jiraBugKey,
		String screenshotPath,
		int stepCount,
		String failedStepSummary,
		TestStep failedStep) {

	public static QaRunSnapshot from(String issueKey, QaResult r) {
		String reason = r.failureReason() == null ? "" : r.failureReason();
		if (reason.length() > 2000) {
			reason = reason.substring(0, 1997) + "...";
		}
		TestStep fs = r.failedStep();
		String failedSummary = "";
		if (fs != null) {
			failedSummary = (fs.action() == null ? "" : fs.action())
					+ " · "
					+ (fs.description() == null ? "" : fs.description());
			if (failedSummary.length() > 500) {
				failedSummary = failedSummary.substring(0, 497) + "...";
			}
		}
		int n = r.stepsExecuted() == null ? 0 : r.stepsExecuted().size();
		return new QaRunSnapshot(
				Instant.now(),
				issueKey == null ? "" : issueKey,
				r.status() == null ? "" : r.status(),
				r.traceId() == null ? "" : r.traceId(),
				reason,
				r.jiraBugKey() == null ? "" : r.jiraBugKey(),
				r.screenshotPath() == null ? "" : r.screenshotPath(),
				n,
				failedSummary,
				fs);
	}
}
