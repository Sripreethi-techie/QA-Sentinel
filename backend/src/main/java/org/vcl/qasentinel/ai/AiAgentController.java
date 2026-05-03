package org.vcl.qasentinel.ai;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.vcl.qasentinel.ai.model.AiAskRequest;
import org.vcl.qasentinel.ai.model.AiAskResponse;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAgentController {

	private final AiAgentService aiAgentService;

	/** Groq-powered answers over recent Jira issues, QA run history, and recorded bug keys. */
	@PostMapping("/ask")
	public AiAskResponse ask(@Valid @RequestBody AiAskRequest request) {
		return aiAgentService.ask(request.question());
	}
}
