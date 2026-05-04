package org.vcl.qasentinel.qa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.vcl.qasentinel.ai.JiraBugIdempotencyStore;
import org.vcl.qasentinel.ai.QaRunHistoryStore;
import org.vcl.qasentinel.config.QaFlowProperties;
import org.vcl.qasentinel.jira.JiraIssuePrd;
import org.vcl.qasentinel.qa.model.QaBatchResult;
import org.vcl.qasentinel.qa.model.QaReportRequest;
import org.vcl.qasentinel.qa.model.QaReportResponse;
import org.vcl.qasentinel.qa.model.QaRequest;
import org.vcl.qasentinel.qa.model.QaResult;
import org.vcl.qasentinel.qa.model.TestStep;

@ExtendWith(MockitoExtension.class)
class QaOrchestratorServiceTest {

	@Mock
	private JiraService jiraService;
	@Mock
	private GroqReasoningService groqReasoningService;
	@Mock
	private PlaywrightService playwrightService;
	@Mock
	private QaFlowProperties qaFlowProperties;
	@Mock
	private QaRunHistoryStore runHistory;
	@Mock
	private JiraBugIdempotencyStore jiraBugIdempotencyStore;
	@Mock
	private DemoLockedPlanLoader demoLockedPlanLoader;

	@BeforeEach
	void stubIdempotency() {
		lenient().when(jiraBugIdempotencyStore.findExistingBugKey(anyString())).thenReturn(Optional.empty());
		lenient().when(qaFlowProperties.isAllowPrivateEnvUrls()).thenReturn(true);
	}

	@InjectMocks
	private QaOrchestratorService orchestrator;

	@Test
	void deriveProjectKeyFromIssue() {
		assertThat(QaOrchestratorService.deriveProjectKey("DEMO-101")).isEqualTo("DEMO");
		assertThat(QaOrchestratorService.deriveProjectKey("X-1")).isEqualTo("X");
	}

	@Test
	void runStopsWithAiReasoningUnavailableWhenPlanningFails() throws Exception {
		when(jiraService.fetchPrdForIssue("DEMO-1")).thenReturn(new JiraIssuePrd("DEMO-1", "S", "D", ""));
		doThrow(new AiReasoningUnavailableException())
				.when(groqReasoningService)
				.planSteps(anyString(), anyString(), anyString(), anyString(), anyString());

		QaResult r = orchestrator.run(new QaRequest("DEMO", "DEMO-1", "https://x", null));
		assertThat(r.status()).isEqualTo("ERROR");
		assertThat(r.failureReason()).isEqualTo(AiReasoningUnavailableException.MESSAGE);
		verify(playwrightService, never()).run(anyList(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void runPassesWithoutJiraBug() throws Exception {
		when(jiraService.fetchPrdForIssue("DEMO-1")).thenReturn(new JiraIssuePrd("DEMO-1", "S", "D", ""));
		when(groqReasoningService.planSteps(anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(List.of(new TestStep(1, "navigate", "Open https://x")));
		when(playwrightService.run(anyList(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(new PlaywrightService.RunOutcome(true, "ok", ""));

		QaResult r = orchestrator.run(new QaRequest("DEMO", "DEMO-1", "https://x", null));
		assertThat(r.passed()).isTrue();
		assertThat(r.status()).isEqualTo("PASS");
		assertThat(r.jiraBugKey()).isEmpty();
		assertThat(r.failureReason()).isEmpty();
		verify(groqReasoningService, never()).suggestAlternativeStep(any(), any(), anyString(), anyString());
	}

	@Test
	void runFailureFilesBug() throws Exception {
		when(jiraService.fetchPrdForIssue("DEMO-1")).thenReturn(new JiraIssuePrd("DEMO-1", "S", "", ""));
		when(groqReasoningService.planSteps(anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(List.of(new TestStep(1, "navigate", "Open https://x")));
		when(playwrightService.run(anyList(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(
						new PlaywrightService.RunOutcome(false, "boom", "qa-runner/test-results/x.png"),
						new PlaywrightService.RunOutcome(false, "still bad", "qa-runner/test-results/x.png"),
						new PlaywrightService.RunOutcome(false, "still bad", "qa-runner/test-results/x.png"));
		when(groqReasoningService.suggestAlternativeStep(any(), any(), anyString(), anyString()))
				.thenReturn(Optional.of(new TestStep(1, "verify", "verify body", "body", null, "visible")));
		when(jiraService.fileQaSentinelPlaywrightBug(
						anyString(),
						anyString(),
						anyString(),
						anyList(),
						anyString(),
						anyString(),
						anyString(),
						any(),
						anyString(),
						anyString(),
						anyString(),
						anyString()))
				.thenReturn("DEMO-999");

		QaResult r = orchestrator.run(new QaRequest("DEMO", "DEMO-1", "https://x", null));
		assertThat(r.passed()).isFalse();
		assertThat(r.status()).isEqualTo("FAIL");
		assertThat(r.jiraBugKey()).isEqualTo("DEMO-999");
		assertThat(r.failureReason()).contains("still bad");
		assertThat(r.screenshotPath()).contains("qa-runner");
		verify(jiraService).fileQaSentinelPlaywrightBug(
				anyString(),
				anyString(),
				anyString(),
				anyList(),
				anyString(),
				anyString(),
				anyString(),
				any(),
				anyString(),
				anyString(),
				anyString(),
				anyString());
		verify(groqReasoningService, times(2)).suggestAlternativeStep(any(), any(), anyString(), anyString());
	}

	@Test
	void runQaFlowUsesDefaultEnvAndDerivedProject() throws Exception {
		when(qaFlowProperties.getDefaultEnvBaseUrl()).thenReturn("https://qa.example");
		when(jiraService.fetchPrdForIssue("DEMO-55")).thenReturn(new JiraIssuePrd("DEMO-55", "T", "Body", ""));
		when(groqReasoningService.planSteps(anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(List.of(new TestStep(1, "navigate", "Open https://qa.example")));
		when(playwrightService.run(anyList(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(new PlaywrightService.RunOutcome(true, "ok", ""));

		QaResult r = orchestrator.runQaFlow("DEMO-55");
		assertThat(r.status()).isEqualTo("PASS");
		verify(playwrightService).run(anyList(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void runQaFlowBlankIssueKeyReturnsError() {
		QaResult r = orchestrator.runQaFlow("  ");
		assertThat(r.status()).isEqualTo("ERROR");
		assertThat(r.failureReason()).contains("required");
	}

	@Test
	void externalReportPassSkipsJira() {
		QaReportResponse resp = orchestrator.acceptExternalReport(
				new QaReportRequest("DEMO", "DEMO-1", "t1", true, "ok", null));
		assertThat(resp.status()).isEqualTo("OK");
	}

	@Test
	void runnerCallbackPassDoesNotFileJira() {
		QaReportResponse resp = orchestrator.acceptRunnerCallback(
				new QaReportRequest("DEMO", "DEMO-1", "t1", true, "runner ok", null));
		assertThat(resp.status()).isEqualTo("OK");
	}

	@Test
	void runnerCallbackFailLogsOnly() {
		QaReportResponse resp = orchestrator.acceptRunnerCallback(
				new QaReportRequest("DEMO", "DEMO-1", "t1", false, "runner fail", "qa-runner/test-results/x.png"));
		assertThat(resp.status()).isEqualTo("FAIL_LOGGED");
	}

	@Test
	void demoLockedUsesCachedStepsWhenGroqFailsAndFilesBug() throws Exception {
		when(qaFlowProperties.isDemoLockedMode()).thenReturn(true);
		when(qaFlowProperties.getDemoLockedIssueKey()).thenReturn("");
		when(qaFlowProperties.getDefaultEnvBaseUrl()).thenReturn("https://app.example/loan");
		when(jiraService.fetchPrdForIssue("DEMO-1")).thenReturn(new JiraIssuePrd("DEMO-1", "S", "D", ""));
		List<TestStep> cached = List.of(
				new TestStep(1, "navigate", "nav", "", "", ""),
				new TestStep(2, "verify", "v", "[data-missing]", "", "visible"));
		when(demoLockedPlanLoader.loadCachedSteps()).thenReturn(cached);
		doThrow(new AiReasoningUnavailableException()).when(groqReasoningService).planSteps(anyString(), anyString(), anyString(), anyString(), anyString());
		when(playwrightService.run(anyList(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(new PlaywrightService.RunOutcome(false, "verify failed", "qa-runner/test-results/x.png"));
		when(jiraService.fileQaSentinelPlaywrightBug(
						anyString(),
						anyString(),
						anyString(),
						anyList(),
						anyString(),
						anyString(),
						anyString(),
						any(),
						anyString(),
						anyString(),
						anyString(),
						anyString()))
				.thenReturn("PROJ-999");

		QaResult r = orchestrator.runQaFlow("DEMO-1");
		assertThat(r.status()).isEqualTo("FAIL");
		assertThat(r.jiraBugKey()).isEqualTo("PROJ-999");
		verify(playwrightService).run(anyList(), anyString(), anyString(), anyString(), anyString());
		verify(groqReasoningService, times(1)).planSteps(anyString(), anyString(), anyString(), anyString(), anyString());
		verify(groqReasoningService, never()).suggestAlternativeStep(any(), any(), anyString(), anyString());
	}

	@Test
	void demoLockedOverridesIssueKeyWhenConfigured() throws Exception {
		when(qaFlowProperties.isDemoLockedMode()).thenReturn(true);
		when(qaFlowProperties.getDemoLockedIssueKey()).thenReturn("REAL-42");
		when(qaFlowProperties.getDefaultEnvBaseUrl()).thenReturn("https://app.example/loan");
		when(jiraService.fetchPrdForIssue("REAL-42")).thenReturn(new JiraIssuePrd("REAL-42", "T", "B", ""));
		when(demoLockedPlanLoader.loadCachedSteps())
				.thenReturn(List.of(new TestStep(1, "navigate", "n", "", "", "")));
		when(groqReasoningService.planSteps(anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(List.of(new TestStep(1, "navigate", "from groq", "", "", "")));
		when(playwrightService.run(anyList(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(new PlaywrightService.RunOutcome(false, "fail", ""));
		when(jiraService.fileQaSentinelPlaywrightBug(
						anyString(),
						anyString(),
						anyString(),
						anyList(),
						anyString(),
						anyString(),
						anyString(),
						any(),
						anyString(),
						anyString(),
						anyString(),
						anyString()))
				.thenReturn("X-1");

		QaResult r = orchestrator.runQaFlow("DEMO-1");
		assertThat(r.status()).isEqualTo("FAIL");
		verify(jiraService).fetchPrdForIssue("REAL-42");
	}

	@Test
	void runAllStoriesBlankProject() {
		QaBatchResult b = orchestrator.runQaFlowAllStoriesInProject("  ");
		assertThat(b.items()).hasSize(1);
		assertThat(b.items().get(0).status()).isEqualTo("ERROR");
	}

	@Test
	void runAllStoriesBlockedWhenDemoLocked() {
		when(qaFlowProperties.isDemoLockedMode()).thenReturn(true);
		QaBatchResult b = orchestrator.runQaFlowAllStoriesInProject("SCRUM");
		assertThat(b.items()).hasSize(1);
		assertThat(b.items().get(0).message()).containsIgnoringCase("demo");
	}
}
