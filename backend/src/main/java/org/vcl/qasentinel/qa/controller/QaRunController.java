package org.vcl.qasentinel.qa.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import org.vcl.qasentinel.qa.model.QaBatchResult;
import org.vcl.qasentinel.qa.model.QaRunResponse;
import org.vcl.qasentinel.qa.service.QaOrchestratorService;

/**
 * <b>Zero-setup demo entry point:</b> {@code POST /api/qa/run/{issueKey}} runs the full pipeline
 * (live Jira PRD → Groq-derived steps → Playwright → real Jira Bug linked to the story on failure).
 * Use issue key {@code DEMO-1} with Groq configured for a sample PRD. Versioned twin: {@link QaController}
 * {@code POST /api/v1/qa/flow/{issueKey}}.
 */
@RestController
@RequiredArgsConstructor
public class QaRunController {

	private final QaOrchestratorService qaOrchestratorService;

	@PostMapping("/api/qa/run/{issueKey}")
	public QaRunResponse runByIssueKey(@PathVariable("issueKey") String issueKey) {
		return QaRunResponse.from(qaOrchestratorService.runQaFlow(issueKey));
	}

	/** Batch: all stories in project (see {@code qa.flow.batch-*} and {@code POST /api/v1/qa/flow/project/.../stories/run}). */
	@PostMapping("/api/qa/run/project/{projectKey}/stories")
	public QaBatchResult runAllStories(@PathVariable("projectKey") String projectKey) {
		return qaOrchestratorService.runQaFlowAllStoriesInProject(projectKey);
	}
}
