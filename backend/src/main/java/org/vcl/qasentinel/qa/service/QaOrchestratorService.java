package org.vcl.qasentinel.qa.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.vcl.qasentinel.ai.QaRunHistoryStore;
import org.vcl.qasentinel.config.QaFlowProperties;
import org.vcl.qasentinel.jira.JiraConfigurationException;
import org.vcl.qasentinel.jira.JiraIssuePrd;
import org.vcl.qasentinel.logging.QaSentinelNarrative;
import org.vcl.qasentinel.qa.model.QaBatchItem;
import org.vcl.qasentinel.qa.model.QaBatchResult;
import org.vcl.qasentinel.qa.model.QaReportRequest;
import org.vcl.qasentinel.qa.model.QaReportResponse;
import org.vcl.qasentinel.qa.model.QaRequest;
import org.vcl.qasentinel.qa.model.QaResult;
import org.vcl.qasentinel.qa.model.TestStep;

/**
 * Synchronous autonomous QA: Jira → Groq steps → Playwright → Jira bug on failure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QaOrchestratorService {

	private static final String DEMO_LOCKED_LOG = "Demo mode active: ensuring deterministic execution";

	private final JiraService jiraService;
	private final GroqReasoningService groqReasoningService;
	private final PlaywrightService playwrightService;
	private final QaFlowProperties qaFlowProperties;
	private final QaRunHistoryStore runHistory;
	private final DemoLockedPlanLoader demoLockedPlanLoader;

	/**
	 * Full flow using only a Jira issue key. Project key is derived ({@code PROJ-123} → {@code PROJ}).
	 * Environment URL comes from {@link QaFlowProperties#getDefaultEnvBaseUrl()}.
	 */
	public QaResult runQaFlow(String issueKey) {
		String traceId = UUID.randomUUID().toString();
		if (issueKey == null || issueKey.isBlank()) {
			QaSentinelNarrative.banner(log);
			QaSentinelNarrative.line(log, "— Stopped: issue key is required.");
			return finish(
					"",
					new QaResult(
							"ERROR",
							List.of(),
							"Issue key is required.",
							"",
							traceId,
							""));
		}
		String key = issueKey.trim().toUpperCase(Locale.ROOT);
		String projectKey = deriveProjectKey(key);
		String envBaseUrl = normalizeUrl(qaFlowProperties.getDefaultEnvBaseUrl());
		if (qaFlowProperties.isDemoLockedMode()) {
			log.info(DEMO_LOCKED_LOG);
			String fixed = qaFlowProperties.getDemoLockedIssueKey();
			if (fixed != null && !fixed.isBlank()) {
				key = fixed.trim().toUpperCase(Locale.ROOT);
				projectKey = deriveProjectKey(key);
			}
		}

		QaSentinelNarrative.banner(log);
		QaSentinelNarrative.line(log, "Session trace: " + traceId);
		QaSentinelNarrative.line(log, "Issue: " + key + " · Project: " + projectKey + " · Target URL: " + envBaseUrl);
		QaSentinelNarrative.agentAnalyzingRequirement(log);
		QaSentinelNarrative.line(log, "Step 1: Fetching Jira issue");
		JiraIssuePrd issue;
		try {
			issue = jiraService.fetchPrdForIssue(key);
		}
		catch (JiraConfigurationException e) {
			QaSentinelNarrative.line(log, "— Stopped after Step 1: " + e.getMessage());
			return finish(key, new QaResult("ERROR", List.of(), e.getMessage(), "", traceId, ""));
		}
		catch (IllegalStateException e) {
			QaSentinelNarrative.line(log, "— Stopped after Step 1: could not load the Jira issue.");
			log.error("Jira fetch failed: {}", e.getMessage(), e);
			return finish(
					key,
					new QaResult("ERROR", List.of(), "Jira fetch failed: " + safeMessage(e), "", traceId, ""));
		}
		catch (Exception e) {
			QaSentinelNarrative.line(log, "— Stopped after Step 1: could not load the Jira issue.");
			log.error("Jira fetch failed: {}", e.getMessage(), e);
			return finish(
					key,
					new QaResult(
							"ERROR",
							List.of(),
							"Jira fetch failed: " + safeMessage(e),
							"",
							traceId,
							""));
		}

		String summary = issue.summary() == null ? "" : issue.summary();
		String description = issue.descriptionPlain() == null ? "" : issue.descriptionPlain();
		String acceptanceCriteria =
				issue.acceptanceCriteriaPlain() == null ? "" : issue.acceptanceCriteriaPlain();

		return executePlaywrightAndMaybeFileBug(
				traceId, projectKey, key, envBaseUrl, summary, description, acceptanceCriteria);
	}

	/**
	 * Runs {@link #runQaFlow(String)} for each Jira story in the project (see {@link org.vcl.qasentinel.config.QaFlowProperties}
	 * batch settings). Failures file bugs linked to that story; assignee uses {@code jira.default-bug-assignee-*}.
	 */
	public QaBatchResult runQaFlowAllStoriesInProject(String projectKey) {
		String pk = projectKey == null ? "" : projectKey.trim().toUpperCase(Locale.ROOT);
		if (pk.isBlank()) {
			return new QaBatchResult(
					"",
					List.of(new QaBatchItem("", "ERROR", "", "Project key is required.")));
		}
		if (qaFlowProperties.isDemoLockedMode()) {
			return new QaBatchResult(
					pk,
					List.of(
							new QaBatchItem(
									"",
									"ERROR",
									"",
									"Batch story run is disabled when qa.flow.demo-locked-mode is true.")));
		}
		QaSentinelNarrative.banner(log);
		QaSentinelNarrative.line(log, "Batch QA: project " + pk + " — listing stories…");
		List<String> keys;
		try {
			String type = qaFlowProperties.getBatchStoryIssueTypeName();
			String suffix = qaFlowProperties.getBatchJqlSuffix();
			int max = qaFlowProperties.getBatchMaxStories();
			keys = jiraService.searchStoryIssueKeys(pk, type, suffix, max);
		}
		catch (JiraConfigurationException e) {
			return new QaBatchResult(pk, List.of(new QaBatchItem("", "ERROR", "", e.getMessage())));
		}
		catch (Exception e) {
			log.error("Batch: Jira search failed: {}", e.getMessage(), e);
			return new QaBatchResult(pk, List.of(new QaBatchItem("", "ERROR", "", safeMessage(e))));
		}
		if (keys.isEmpty()) {
			QaSentinelNarrative.line(log, "— No matching stories found for batch QA.");
			return new QaBatchResult(pk, List.of());
		}
		QaSentinelNarrative.line(log, "— Running QA for " + keys.size() + " story/stories…");
		List<QaBatchItem> items = new ArrayList<>();
		for (String storyKey : keys) {
			log.info("Batch QA: {}", storyKey);
			QaResult r = runQaFlow(storyKey);
			String msg = r.failureReason() == null || r.failureReason().isBlank() ? r.status() : r.failureReason();
			items.add(new QaBatchItem(storyKey, r.status(), r.jiraBugKey(), msg));
		}
		QaSentinelNarrative.line(log, "— Batch QA finished for project " + pk + " (" + items.size() + " item(s)).");
		return new QaBatchResult(pk, items);
	}

	/** Full flow with explicit project, env, and optional PRD override (sync). */
	public QaResult run(QaRequest request) {
		String traceId = UUID.randomUUID().toString();
		String envBaseUrl = normalizeUrl(request.envBaseUrl());
		String projectKey = request.projectKey().trim().toUpperCase(Locale.ROOT);
		String issueKey = request.issueKey().trim().toUpperCase(Locale.ROOT);
		if (qaFlowProperties.isDemoLockedMode()) {
			log.info(DEMO_LOCKED_LOG);
			String fixed = qaFlowProperties.getDemoLockedIssueKey();
			if (fixed != null && !fixed.isBlank()) {
				issueKey = fixed.trim().toUpperCase(Locale.ROOT);
				projectKey = deriveProjectKey(issueKey);
			}
		}

		QaSentinelNarrative.banner(log);
		QaSentinelNarrative.line(log, "Session trace: " + traceId);
		QaSentinelNarrative.line(log, "Issue: " + issueKey + " · Project: " + projectKey + " · Target URL: " + envBaseUrl);
		QaSentinelNarrative.agentAnalyzingRequirement(log);

		if (request.prdOverride() != null && !request.prdOverride().isBlank()) {
			String override = request.prdOverride().trim();
			QaSentinelNarrative.line(log, "Step 1: Using manual PRD (Jira fetch skipped)");
			return executePlaywrightAndMaybeFileBug(
					traceId, projectKey, issueKey, envBaseUrl, "Manual PRD override", override, "");
		}

		QaSentinelNarrative.line(log, "Step 1: Fetching Jira issue");
		JiraIssuePrd issue;
		try {
			issue = jiraService.fetchPrdForIssue(issueKey);
		}
		catch (JiraConfigurationException e) {
			QaSentinelNarrative.line(log, "— Stopped after Step 1: " + e.getMessage());
			return finish(issueKey, new QaResult("ERROR", List.of(), e.getMessage(), "", traceId, ""));
		}
		catch (IllegalStateException e) {
			QaSentinelNarrative.line(log, "— Stopped after Step 1: could not load the Jira issue.");
			log.error("Jira fetch failed: {}", e.getMessage(), e);
			return finish(
					issueKey,
					new QaResult("ERROR", List.of(), "Jira fetch failed: " + safeMessage(e), "", traceId, ""));
		}
		catch (Exception e) {
			QaSentinelNarrative.line(log, "— Stopped after Step 1: could not load the Jira issue.");
			log.error("Jira fetch failed: {}", e.getMessage(), e);
			return finish(
					issueKey,
					new QaResult(
							"ERROR",
							List.of(),
							"Jira fetch failed: " + safeMessage(e),
							"",
							traceId,
							""));
		}

		String summary = issue.summary() == null ? "" : issue.summary();
		String description = issue.descriptionPlain() == null ? "" : issue.descriptionPlain();
		String acceptanceCriteria =
				issue.acceptanceCriteriaPlain() == null ? "" : issue.acceptanceCriteriaPlain();

		return executePlaywrightAndMaybeFileBug(
				traceId, projectKey, issueKey, envBaseUrl, summary, description, acceptanceCriteria);
	}

	private QaResult executePlaywrightAndMaybeFileBug(
			String traceId,
			String projectKey,
			String issueKey,
			String envBaseUrl,
			String issueTitle,
			String issueDescription,
			String acceptanceCriteria) {

		List<TestStep> steps;
		if (qaFlowProperties.isDemoLockedMode()) {
			try {
				steps = resolveDemoLockedSteps(issueTitle, issueDescription, acceptanceCriteria, issueKey, envBaseUrl);
			}
			catch (IllegalStateException e) {
				QaSentinelNarrative.line(log, "— Stopped after Step 2: " + e.getMessage());
				log.error("Demo locked plan: {}", e.getMessage(), e);
				return finish(
						issueKey,
						new QaResult("ERROR", List.of(), e.getMessage(), "", traceId, ""));
			}
			QaSentinelNarrative.line(log, "— Planned " + steps.size() + " browser action(s) (demo locked: cached Groq-shaped plan)");
			return executePlaywrightDemoLocked(traceId, projectKey, issueKey, envBaseUrl, steps);
		}

		QaSentinelNarrative.line(log, "Step 2: Generating test steps via Groq");
		QaSentinelNarrative.agentGeneratingStepsViaAi(log);
		try {
			steps = groqReasoningService.planSteps(
					issueTitle == null ? "" : issueTitle,
					issueDescription == null ? "" : issueDescription,
					acceptanceCriteria == null ? "" : acceptanceCriteria,
					issueKey,
					envBaseUrl);
		}
		catch (AiReasoningUnavailableException e) {
			QaSentinelNarrative.line(log, "— Stopped after Step 2: " + AiReasoningUnavailableException.MESSAGE);
			log.warn("Step planning aborted: {}", e.getMessage());
			return finish(
					issueKey,
					new QaResult(
							"ERROR",
							List.of(),
							AiReasoningUnavailableException.MESSAGE,
							"",
							traceId,
							""));
		}
		catch (Exception e) {
			QaSentinelNarrative.line(log, "— Stopped after Step 2: could not generate test steps.");
			log.error("Groq planning failed: {}", e.getMessage(), e);
			return finish(
					issueKey,
					new QaResult(
							"ERROR",
							List.of(),
							"Groq planning failed: " + safeMessage(e),
							"",
							traceId,
							""));
		}

		QaSentinelNarrative.line(log, "— Planned " + steps.size() + " browser action(s)");

		QaSentinelNarrative.line(log, "Step 3: Executing browser actions");
		try {
			List<TestStep> executedPlan = new ArrayList<>(steps);
			AgentExecutionResult agentResult =
					executeStepsWithAgentRecovery(executedPlan, envBaseUrl, traceId, projectKey, issueKey);
			if (agentResult.passed()) {
				QaSentinelNarrative.line(log, "Step 4: Validating results — passed");
				QaSentinelNarrative.line(log, "— Run finished successfully for " + issueKey + ".");
				return finish(issueKey, new QaResult("PASS", executedPlan, "", "", traceId, ""));
			}

			PlaywrightService.RunOutcome outcome = agentResult.failedOutcome();
			String reason = outcome.message() == null ? "Playwright reported failure." : outcome.message();
			String shot = outcome.screenshotPath() == null ? "" : outcome.screenshotPath();
			String pageUrl = outcome.failurePageUrl() == null ? "" : outcome.failurePageUrl();
			TestStep failedStep = outcome.failedStep() != null
					? outcome.failedStep()
					: (agentResult.failedPlanIndex() != null ? executedPlan.get(agentResult.failedPlanIndex()) : null);
			QaSentinelNarrative.line(log, "Agent analyzing UI state...");
			QaSentinelNarrative.line(log, "Agent verifying expected outcome...");
			QaSentinelNarrative.line(log, "Agent detected mismatch");
			QaSentinelNarrative.line(log, "— " + errorTextForAgent(outcome));
			QaSentinelNarrative.line(log, "Failed step: " + formatStepForAgentLog(failedStep));
			QaSentinelNarrative.line(log, "Step 4: Validating results — failed");
			QaSentinelNarrative.agentFailureCreatingBug(log);
			QaSentinelNarrative.line(log, "Step 5: Creating Jira ticket");

			String jiraKey = safeFileQaSentinelPlaywrightBug(projectKey, issueKey, traceId, executedPlan, outcome, failedStep);
			logJiraCreated(jiraKey);
			QaSentinelNarrative.line(log, "— Run finished with failure for " + issueKey + ".");
			return finish(
					issueKey,
					new QaResult("FAIL", executedPlan, reason, shot, traceId, jiraKey, pageUrl, failedStep));
		}
		catch (JiraConfigurationException e) {
			QaSentinelNarrative.line(log, "— " + e.getMessage());
			return finish(issueKey, new QaResult("ERROR", new ArrayList<>(steps), e.getMessage(), "", traceId, ""));
		}
		catch (IllegalStateException e) {
			log.error("Jira bug filing failed: {}", e.getMessage(), e);
			QaSentinelNarrative.line(log, "— " + safeMessage(e));
			return finish(
					issueKey,
					new QaResult("ERROR", new ArrayList<>(steps), safeMessage(e), "", traceId, ""));
		}
		catch (Exception e) {
			log.error("Playwright execution failed: {}", e.getMessage(), e);
			String reason = "Playwright execution failed: " + safeMessage(e);
			QaSentinelNarrative.line(log, "Agent detected mismatch");
			QaSentinelNarrative.line(log, "— " + reason);
			QaSentinelNarrative.line(log, "Step 4: Validating results — failed (runner error)");
			QaSentinelNarrative.agentFailureCreatingBug(log);
			QaSentinelNarrative.line(log, "Step 5: Creating Jira ticket");
			try {
				String jiraKey = safeFileQaSentinelPlaywrightBug(
						projectKey,
						issueKey,
						traceId,
						new ArrayList<>(steps),
						PlaywrightService.RunOutcome.runnerHostError(reason),
						null);
				logJiraCreated(jiraKey);
				QaSentinelNarrative.line(log, "— Run finished with failure for " + issueKey + ".");
				return finish(issueKey, new QaResult("FAIL", steps, reason, "", traceId, jiraKey, "", null));
			}
			catch (JiraConfigurationException jce) {
				return finish(issueKey, new QaResult("ERROR", new ArrayList<>(steps), jce.getMessage(), "", traceId, ""));
			}
			catch (IllegalStateException jse) {
				return finish(issueKey, new QaResult("ERROR", new ArrayList<>(steps), safeMessage(jse), "", traceId, ""));
			}
		}
	}

	private List<TestStep> resolveDemoLockedSteps(
			String issueTitle,
			String issueDescription,
			String acceptanceCriteria,
			String issueKey,
			String envBaseUrl) {
		List<TestStep> cached = demoLockedPlanLoader.loadCachedSteps();
		QaSentinelNarrative.line(log, "Step 2: Generating test steps via Groq");
		QaSentinelNarrative.agentGeneratingStepsViaAi(log);
		try {
			List<TestStep> fromGroq = groqReasoningService.planSteps(
					issueTitle == null ? "" : issueTitle,
					issueDescription == null ? "" : issueDescription,
					acceptanceCriteria == null ? "" : acceptanceCriteria,
					issueKey,
					envBaseUrl);
			log.info(
					"Demo locked: Groq returned {} step(s); executing pre-validated cached AI plan ({} step(s))",
					fromGroq.size(),
					cached.size());
		}
		catch (AiReasoningUnavailableException e) {
			log.info(
					"Demo locked: Groq unavailable — using pre-cached AI-generated steps ({})",
					e.getMessage() == null || e.getMessage().isBlank()
							? AiReasoningUnavailableException.MESSAGE
							: e.getMessage());
		}
		catch (Exception e) {
			log.info("Demo locked: Groq planning failed — using pre-cached AI-generated steps: {}", safeMessage(e));
		}
		return cached;
	}

	private QaResult executePlaywrightDemoLocked(
			String traceId,
			String projectKey,
			String issueKey,
			String envBaseUrl,
			List<TestStep> steps) {
		QaSentinelNarrative.line(log, "Step 3: Executing browser actions (demo locked: deterministic plan)");
		try {
			List<TestStep> executedPlan = new ArrayList<>(steps);
			PlaywrightService.RunOutcome outcome =
					playwrightService.run(executedPlan, envBaseUrl, traceId, projectKey, issueKey);
			if (outcome.passed()) {
				log.warn("Demo locked: Playwright passed unexpectedly; filing Jira for invariant breach");
				String reason =
						"Demo locked mode expected a failing Playwright assertion; run passed — check demo-locked plan.";
				QaSentinelNarrative.line(log, "Step 4: Validating results — passed (unexpected for demo locked)");
				QaSentinelNarrative.agentFailureCreatingBug(log);
				QaSentinelNarrative.line(log, "Step 5: Creating Jira ticket");
				PlaywrightService.RunOutcome invariantBreach =
						PlaywrightService.RunOutcome.runnerHostError(reason);
				String jiraKey =
						safeFileQaSentinelPlaywrightBug(projectKey, issueKey, traceId, executedPlan, invariantBreach, null);
				logJiraCreated(jiraKey);
				QaSentinelNarrative.line(log, "— Run finished with failure for " + issueKey + ".");
				return finish(
						issueKey,
						new QaResult("FAIL", executedPlan, reason, "", traceId, jiraKey, "", null));
			}
			String reason = outcome.message() == null ? "Playwright reported failure." : outcome.message();
			String shot = outcome.screenshotPath() == null ? "" : outcome.screenshotPath();
			String pageUrl = outcome.failurePageUrl() == null ? "" : outcome.failurePageUrl();
			TestStep failedStep =
					outcome.failedStep() != null ? outcome.failedStep() : lastStepOrNull(executedPlan);
			QaSentinelNarrative.line(log, "Agent analyzing UI state...");
			QaSentinelNarrative.line(log, "Agent verifying expected outcome...");
			QaSentinelNarrative.line(log, "Agent detected mismatch");
			QaSentinelNarrative.line(log, "— " + errorTextForAgent(outcome));
			QaSentinelNarrative.line(log, "Failed step: " + formatStepForAgentLog(failedStep));
			QaSentinelNarrative.line(log, "Step 4: Validating results — failed");
			QaSentinelNarrative.agentFailureCreatingBug(log);
			QaSentinelNarrative.line(log, "Step 5: Creating Jira ticket");
			String jiraKey = safeFileQaSentinelPlaywrightBug(projectKey, issueKey, traceId, executedPlan, outcome, failedStep);
			logJiraCreated(jiraKey);
			QaSentinelNarrative.line(log, "— Run finished with failure for " + issueKey + ".");
			return finish(
					issueKey,
					new QaResult("FAIL", executedPlan, reason, shot, traceId, jiraKey, pageUrl, failedStep));
		}
		catch (JiraConfigurationException e) {
			QaSentinelNarrative.line(log, "— " + e.getMessage());
			return finish(issueKey, new QaResult("ERROR", new ArrayList<>(steps), e.getMessage(), "", traceId, ""));
		}
		catch (IllegalStateException e) {
			log.error("Jira bug filing failed: {}", e.getMessage(), e);
			return finish(issueKey, new QaResult("ERROR", new ArrayList<>(steps), safeMessage(e), "", traceId, ""));
		}
		catch (Exception e) {
			log.error("Playwright execution failed: {}", e.getMessage(), e);
			String reason = "Playwright execution failed: " + safeMessage(e);
			QaSentinelNarrative.line(log, "Agent detected mismatch");
			QaSentinelNarrative.line(log, "— " + safeMessage(e));
			QaSentinelNarrative.line(log, "Step 4: Validating results — failed (runner error)");
			QaSentinelNarrative.agentFailureCreatingBug(log);
			QaSentinelNarrative.line(log, "Step 5: Creating Jira ticket");
			try {
				String jiraKey = safeFileQaSentinelPlaywrightBug(
						projectKey,
						issueKey,
						traceId,
						new ArrayList<>(steps),
						PlaywrightService.RunOutcome.runnerHostError(reason),
						null);
				logJiraCreated(jiraKey);
				QaSentinelNarrative.line(log, "— Run finished with failure for " + issueKey + ".");
				return finish(issueKey, new QaResult("FAIL", steps, reason, "", traceId, jiraKey, "", null));
			}
			catch (JiraConfigurationException jce) {
				return finish(issueKey, new QaResult("ERROR", new ArrayList<>(steps), jce.getMessage(), "", traceId, ""));
			}
			catch (IllegalStateException jse) {
				return finish(issueKey, new QaResult("ERROR", new ArrayList<>(steps), safeMessage(jse), "", traceId, ""));
			}
		}
	}

	private static TestStep lastStepOrNull(List<TestStep> plan) {
		if (plan == null || plan.isEmpty()) {
			return null;
		}
		return plan.get(plan.size() - 1);
	}

	private void logJiraCreated(String jiraKey) {
		if (jiraKey == null || jiraKey.isEmpty()) {
			QaSentinelNarrative.line(log, "— Jira ticket was not created (configure Jira or check logs).");
		}
		else {
			QaSentinelNarrative.line(log, "— Jira ticket created: " + jiraKey);
		}
	}

	private QaResult finish(String issueKey, QaResult result) {
		try {
			runHistory.record(issueKey, result);
		}
		catch (Exception ex) {
			log.warn("Could not record QA run for AI/history: {}", ex.getMessage());
		}
		return result;
	}

	/** Initial attempt plus up to this many Groq-driven retries per plan step. */
	private static final int MAX_AGENT_RETRIES_PER_STEP = 2;

	/**
	 * Runs Playwright with growing prefixes. After each successful step, logs agent-style analysis and
	 * verification lines. On mismatch, logs recovery intent, calls Groq for the next best action (up to
	 * {@link #MAX_AGENT_RETRIES_PER_STEP} times per step), logs the AI recovery step, and re-runs the prefix.
	 */
	private AgentExecutionResult executeStepsWithAgentRecovery(
			List<TestStep> plan,
			String envBaseUrl,
			String traceId,
			String projectKey,
			String issueKey)
			throws Exception {
		int i = 0;
		while (i < plan.size()) {
			QaSentinelNarrative.agentExecutingStep(log, i + 1, plan.size());
			List<TestStep> prefix = new ArrayList<>(plan.subList(0, i + 1));
			int retriesUsed = 0;
			PlaywrightService.RunOutcome outcome = null;
			while (true) {
				outcome = playwrightService.run(prefix, envBaseUrl, traceId, projectKey, issueKey);
				if (outcome.passed()) {
					logAgentAfterSuccessfulStep();
					QaSentinelNarrative.line(log, "Agent deciding next action...");
					break;
				}
				QaSentinelNarrative.line(log, "Agent detected mismatch");
				QaSentinelNarrative.line(log, "— " + errorTextForAgent(outcome));
				if (retriesUsed >= MAX_AGENT_RETRIES_PER_STEP) {
					return new AgentExecutionResult(false, outcome, i);
				}
				TestStep failed = plan.get(i);
				QaSentinelNarrative.line(log, "Agent requesting alternative action from AI");
				QaSentinelNarrative.line(log, "Failed step: " + formatStepForAgentLog(failed));
				QaSentinelNarrative.line(log, "Suggest next best action");
				Optional<TestStep> altOpt = groqReasoningService.suggestAlternativeStep(
						failed,
						errorTextForAgent(outcome),
						issueKey,
						envBaseUrl);
				if (altOpt.isEmpty()) {
					QaSentinelNarrative.line(log, "— " + AiReasoningUnavailableException.MESSAGE + " (no AI recovery step).");
					return new AgentExecutionResult(false, outcome, i);
				}
				TestStep recovery = altOpt.get();
				QaSentinelNarrative.line(log, "Agent re-planning step");
				QaSentinelNarrative.line(log, "AI-generated recovery step: " + formatStepForAgentLog(recovery));
				QaSentinelNarrative.line(log, "Executing fallback strategy");
				plan.set(i, recovery);
				renumberSteps(plan);
				prefix = new ArrayList<>(plan.subList(0, i + 1));
				retriesUsed++;
			}
			i++;
		}
		return AgentExecutionResult.ok();
	}

	private void logAgentAfterSuccessfulStep() {
		QaSentinelNarrative.line(log, "Agent analyzing UI state...");
		QaSentinelNarrative.line(log, "Agent verifying expected outcome...");
	}

	private static String formatStepForAgentLog(TestStep s) {
		if (s == null) {
			return "(none)";
		}
		String a = s.action() == null ? "" : s.action();
		String t = s.target() == null ? "" : s.target();
		String v = s.value() == null ? "" : s.value();
		String e = s.expected() == null ? "" : s.expected();
		String d = s.description() == null ? "" : s.description();
		return "stepNumber="
				+ s.stepNumber()
				+ " action="
				+ a
				+ " target="
				+ truncateForLog(t, 120)
				+ " value="
				+ truncateForLog(v, 80)
				+ " expected="
				+ truncateForLog(e, 80)
				+ " description="
				+ truncateForLog(d, 160);
	}

	private static String errorTextForAgent(PlaywrightService.RunOutcome outcome) {
		if (outcome == null) {
			return "(unknown)";
		}
		String exact = outcome.exactError() == null ? "" : outcome.exactError().trim();
		if (!exact.isEmpty()) {
			return truncateForLog(exact, 900);
		}
		String msg = outcome.message() == null ? "" : outcome.message().trim();
		return msg.isEmpty() ? "(no error text)" : truncateForLog(msg, 900);
	}

	private static String truncateForLog(String s, int max) {
		if (s == null) {
			return "";
		}
		if (s.length() <= max) {
			return s.replace('\r', ' ').replace('\n', ' ');
		}
		String oneLine = s.replace('\r', ' ').replace('\n', ' ').trim();
		return oneLine.length() <= max ? oneLine : oneLine.substring(0, max - 3) + "...";
	}

	private record AgentExecutionResult(
			boolean passed,
			PlaywrightService.RunOutcome failedOutcome,
			Integer failedPlanIndex) {

		static AgentExecutionResult ok() {
			return new AgentExecutionResult(true, null, null);
		}
	}

	private static void renumberSteps(List<TestStep> plan) {
		for (int j = 0; j < plan.size(); j++) {
			TestStep s = plan.get(j);
			plan.set(j, new TestStep(j + 1, s.action(), s.description(), s.target(), s.value(), s.expected()));
		}
	}

	private String safeFileQaSentinelPlaywrightBug(
			String projectKey,
			String issueKey,
			String traceId,
			List<TestStep> steps,
			PlaywrightService.RunOutcome outcome,
			TestStep failedStep) {
		String failureReason = outcome.message() == null ? "" : outcome.message();
		String screenshotHint = outcome.screenshotPath() == null ? "" : outcome.screenshotPath();
		String failurePageUrl = outcome.failurePageUrl() == null ? "" : outcome.failurePageUrl();
		return jiraService.fileQaSentinelPlaywrightBug(
				projectKey,
				issueKey,
				traceId,
				steps,
				failureReason,
				screenshotHint,
				failurePageUrl,
				failedStep,
				outcome.exactError(),
				outcome.failureTimestampUtc(),
				outcome.tracePathHint(),
				outcome.consoleLogPathHint());
	}

	/**
	 * Playwright runner callback at {@code POST /qa-report}: records outcome only; does not create
	 * Jira issues (avoids duplicate bugs when the same run is also handled synchronously).
	 */
	public QaReportResponse acceptRunnerCallback(QaReportRequest req) {
		String msg = req.message() == null ? "" : req.message();
		if (Boolean.TRUE.equals(req.passed())) {
			QaSentinelNarrative.banner(log);
			QaSentinelNarrative.line(log, "Runner callback: pass · " + req.issueKey());
			return new QaReportResponse("OK", "Runner reported pass.", "");
		}
		QaSentinelNarrative.banner(log);
		QaSentinelNarrative.line(log, "Runner callback: fail · " + req.issueKey() + " — " + summarizeForLog(msg));
		return new QaReportResponse(
				"FAIL_LOGGED",
				"Runner reported failure (Jira filing uses /api/v1/qa/report or sync /run).",
				"");
	}

	public QaReportResponse acceptExternalReport(QaReportRequest req) {
		if (Boolean.TRUE.equals(req.passed())) {
			QaSentinelNarrative.banner(log);
			QaSentinelNarrative.line(log, "External report: pass · " + req.issueKey());
			return new QaReportResponse("OK", "No Jira filing on pass.", "");
		}
		QaSentinelNarrative.banner(log);
		QaSentinelNarrative.line(log, "External report: fail · " + req.issueKey() + " — filing Jira…");
		String hint = req.screenshotHint() == null ? "" : req.screenshotHint();
		String body = "[QA Sentinel] Automated run reported FAIL. " + (req.message() == null ? "" : req.message())
				+ (hint.isEmpty() ? "" : "\nScreenshot: " + hint);
		String key;
		try {
			key = jiraService.fileBug(req.projectKey(), req.issueKey(), req.traceId(), body, hint);
		}
		catch (JiraConfigurationException e) {
			log.error("Jira filing failed: {}", e.getMessage());
			return new QaReportResponse("ERROR", e.getMessage(), "");
		}
		catch (IllegalStateException e) {
			log.error("Jira filing failed: {}", e.getMessage(), e);
			return new QaReportResponse("ERROR", safeMessage(e), "");
		}
		catch (Exception e) {
			log.error("Jira filing failed: {}", e.getMessage(), e);
			return new QaReportResponse("ERROR", "Jira filing failed: " + safeMessage(e), "");
		}
		QaSentinelNarrative.line(log, "— Jira ticket created: " + key);
		return new QaReportResponse("BUG_FILED", "Created Jira issue for tracking.", key);
	}

	static String deriveProjectKey(String issueKey) {
		if (issueKey == null || issueKey.isBlank()) {
			return "UNKNOWN";
		}
		String k = issueKey.trim().toUpperCase(Locale.ROOT);
		int dash = k.lastIndexOf('-');
		if (dash <= 0 || dash >= k.length() - 1) {
			return k;
		}
		return k.substring(0, dash);
	}

	private String normalizeUrl(String url) {
		if (url == null || url.isBlank()) {
			return QaFlowProperties.DEFAULT_DEMO_TARGET_URL;
		}
		return url.trim();
	}

	private static String safeMessage(Throwable e) {
		String m = e.getMessage();
		return m == null ? e.getClass().getSimpleName() : m;
	}

	private static String summarizeForLog(String reason) {
		if (reason == null) {
			return "";
		}
		String oneLine = reason.replace('\r', ' ').replace('\n', ' ').trim();
		return oneLine.length() > 240 ? oneLine.substring(0, 237) + "..." : oneLine;
	}
}
