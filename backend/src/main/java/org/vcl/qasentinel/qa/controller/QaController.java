package org.vcl.qasentinel.qa.controller;

import java.util.Map;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.vcl.qasentinel.qa.model.QaBatchResult;
import org.vcl.qasentinel.qa.model.QaReportRequest;
import org.vcl.qasentinel.qa.model.QaReportResponse;
import org.vcl.qasentinel.qa.model.QaRequest;
import org.vcl.qasentinel.qa.model.QaResult;
import org.vcl.qasentinel.qa.service.QaOrchestratorService;

/** Versioned QA API. Same pipeline as {@link QaRunController} {@code POST /api/qa/run/{issueKey}} (live Jira required). */
@RestController
@RequestMapping("/api/v1/qa")
@RequiredArgsConstructor
public class QaController {

	private final QaOrchestratorService qaOrchestratorService;

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP", "service", "qa-sentinel");
	}

	/** Synchronous full flow: Jira PRD → Groq steps → Playwright → Jira bug on failure. */
	@PostMapping("/run")
	public QaResult run(@Valid @RequestBody QaRequest request) {
		return qaOrchestratorService.run(request);
	}

	/**
	 * Same pipeline as {@code /run}, but only requires the Jira issue key: project is derived
	 * ({@code ABC-42} → {@code ABC}) and env URL comes from {@code qa.flow.default-env-base-url}.
	 * Requires a real Jira issue key in your project. Minimal alias: {@code POST /api/qa/run/{issueKey}}.
	 */
	@PostMapping("/flow/{issueKey}")
	public QaResult runFlow(@PathVariable("issueKey") String issueKey) {
		return qaOrchestratorService.runQaFlow(issueKey);
	}

	/**
	 * Runs {@link QaOrchestratorService#runQaFlowAllStoriesInProject(String)}: each story in the project gets
	 * Groq-planned FE+API steps, Playwright execution, and Jira bugs (linked + assignee per {@code jira.*} config).
	 */
	@PostMapping("/flow/project/{projectKey}/stories/run")
	public QaBatchResult runAllStories(@PathVariable("projectKey") String projectKey) {
		return qaOrchestratorService.runQaFlowAllStoriesInProject(projectKey);
	}

	/** Optional: external CI / Playwright posts outcome (Theme 1 style). */
	@PostMapping("/report")
	public QaReportResponse report(@Valid @RequestBody QaReportRequest request) {
		return qaOrchestratorService.acceptExternalReport(request);
	}
}
