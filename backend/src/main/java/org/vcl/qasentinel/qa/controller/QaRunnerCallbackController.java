package org.vcl.qasentinel.qa.controller;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import org.vcl.qasentinel.qa.model.QaReportRequest;
import org.vcl.qasentinel.qa.model.QaReportResponse;
import org.vcl.qasentinel.qa.service.QaOrchestratorService;

/**
 * Ingest-only callback for the Playwright runner ({@code dynamic-test.spec.js}). Does not file Jira;
 * use {@link QaController#report} for external CI that should create issues on failure.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class QaRunnerCallbackController {

	private final QaOrchestratorService qaOrchestratorService;

	@PostMapping("/qa-report")
	public QaReportResponse qaReport(@Valid @RequestBody QaReportRequest request) {
		log.info(
				"[QA-RUNNER] trace={} issue={} passed={} message={} screenshotHint={}",
				request.traceId(),
				request.issueKey(),
				request.passed(),
				summarize(request.message()),
				request.screenshotHint() == null ? "" : request.screenshotHint());
		return qaOrchestratorService.acceptRunnerCallback(request);
	}

	private static String summarize(String m) {
		if (m == null || m.isEmpty()) {
			return "";
		}
		String one = m.replace('\r', ' ').replace('\n', ' ').trim();
		return one.length() > 200 ? one.substring(0, 197) + "..." : one;
	}
}
