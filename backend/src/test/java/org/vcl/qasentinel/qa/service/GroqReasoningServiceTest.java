package org.vcl.qasentinel.qa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.vcl.qasentinel.config.GroqProperties;
import org.vcl.qasentinel.qa.model.TestStep;

class GroqReasoningServiceTest {

	private final ObjectMapper mapper = new ObjectMapper();
	private final GroqReasoningService service = new GroqReasoningService(new GroqProperties(), mapper);

	@Test
	void parsesNumberedListWithLeadingVerbs() {
		String raw = """
				1. navigate Open https://a.test/login
				2. verify Page shows welcome text
				3. click Submit button
				""";
		List<TestStep> steps = service.parseNumberedStepsFromLlmContent(raw);
		assertThat(steps).hasSize(3);
		assertThat(steps.get(0).stepNumber()).isEqualTo(1);
		assertThat(steps.get(0).action()).isEqualTo("navigate");
		assertThat(steps.get(1).action()).isEqualTo("verify");
		assertThat(steps.get(2).action()).isEqualTo("click");
	}

	@Test
	void actionFromLeadingVerbRecognizesKeywords() {
		assertThat(GroqReasoningService.actionFromLeadingVerb("navigate to https://x")).isEqualTo("navigate");
		assertThat(GroqReasoningService.actionFromLeadingVerb("click the Login link")).isEqualTo("click");
		assertThat(GroqReasoningService.actionFromLeadingVerb("type admin into username")).isEqualTo("type");
		assertThat(GroqReasoningService.actionFromLeadingVerb("verify dashboard")).isEqualTo("verify");
	}

	@Test
	void infersNavigateWhenUrlPresent() {
		assertThat(GroqReasoningService.inferAction("Open https://app.example/home")).isEqualTo("navigate");
	}

	@Test
	void parsesStructuredJsonSteps() throws JsonProcessingException {
		String raw =
				"""
				Here is the plan:
				[
				  {"stepNumber":1,"action":"navigate","target":"app","value":"https://a.test/","expected":"loads"},
				  {"stepNumber":2,"action":"type","target":"Username","value":"u1","expected":"field filled"},
				  {"stepNumber":3,"action":"verify","target":"","value":"","expected":"Welcome"}
				]
				""";
		List<TestStep> steps = service.parseStepsFromLlmContent(raw);
		assertThat(steps).hasSize(3);
		assertThat(steps.get(0).action()).isEqualTo("navigate");
		assertThat(steps.get(0).value()).isEqualTo("https://a.test/");
		assertThat(steps.get(0).expected()).isEqualTo("loads");
		assertThat(steps.get(1).target()).isEqualTo("Username");
		assertThat(steps.get(1).value()).isEqualTo("u1");
		assertThat(steps.get(2).expected()).isEqualTo("Welcome");
	}

	@Test
	void extractFirstJsonArraySkipsPreamble() {
		String s = "Some text [{\"a\":1}]";
		assertThat(GroqReasoningService.extractFirstJsonArray(s)).isEqualTo("[{\"a\":1}]");
	}

	@Test
	void synthesizedDescriptionJoinsFields() {
		assertThat(GroqReasoningService.synthesizedDescription("click", "OK", null, "dialog closes"))
				.contains("click")
				.contains("OK")
				.contains("expect: dialog closes");
	}

	@Test
	void planStepsWithoutGroqThrows() {
		assertThatThrownBy(() -> service.planSteps("T", "D", "", "DEMO-1", "https://x.test/"))
				.isInstanceOf(AiReasoningUnavailableException.class)
				.hasMessageContaining(AiReasoningUnavailableException.MESSAGE);
	}

	@Test
	void suggestAlternativeStepEmptyWhenGroqOffline() {
		TestStep failed = new TestStep(2, "click", "click Missing", "Missing", null, null);
		Optional<TestStep> alt = service.suggestAlternativeStep(failed, "timeout", "DEMO-1", "https://x.test/");
		assertThat(alt).isEmpty();
	}
}
