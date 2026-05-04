package org.vcl.qasentinel.qa.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.vcl.qasentinel.config.GroqProperties;
import org.vcl.qasentinel.qa.model.TestStep;
import org.vcl.qasentinel.util.HttpRetry;

/**
 * Calls Groq's OpenAI-compatible Chat Completions API via {@link RestClient} to turn Jira title +
 * description into structured test steps. Zero-scripting: no synthetic or heuristic steps when Groq
 * is missing or output cannot be parsed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroqReasoningService {

	private static final int MAX_STEPS = 24;

	private static final Pattern NUMBERED_LINE = Pattern.compile("^\\s*(\\d+)\\.\\s*(.+)$");

	private final GroqProperties groqProperties;
	private final ObjectMapper objectMapper;

	/**
	 * Builds test steps from Jira summary, description, and acceptance criteria using Groq only.
	 *
	 * @throws AiReasoningUnavailableException if Groq is not configured, the API fails, or no steps could be parsed
	 */
	public List<TestStep> planSteps(
			String issueTitle,
			String issueDescription,
			String acceptanceCriteria,
			String issueKey,
			String envBaseUrl) {
		log.info("Zero-scripting mode: all steps derived from AI");
		String title = issueTitle == null ? "" : issueTitle.trim();
		String desc = issueDescription == null ? "" : issueDescription.trim();
		String ac = acceptanceCriteria == null ? "" : acceptanceCriteria.trim();
		if (!ac.isEmpty()) {
			log.info("[Jira → Groq] Issue {} — acceptance criteria passed to model ({} chars)", issueKey, ac.length());
		}
		else {
			log.info("[Jira → Groq] Issue {} — acceptance criteria: (none)", issueKey);
		}
		String requirementBlock = buildRequirementBlock(title, desc, ac, issueKey);

		if (!groqProperties.isConfigured()) {
			log.warn("Zero-scripting mode: Groq API key not set — {}", AiReasoningUnavailableException.MESSAGE);
			throw new AiReasoningUnavailableException();
		}
		try {
			String userPrompt = buildPlanUserPrompt(requirementBlock, envBaseUrl);
			String content = callGroqChat(userPrompt, issueKey);
			log.info("[Groq] raw LLM output for issue {}:\n{}", issueKey, content == null ? "" : content);
			List<TestStep> parsed = parseStepsFromLlmContent(content);
			if (parsed.isEmpty()) {
				log.warn("Zero-scripting mode: parsed zero steps from LLM output — {}", AiReasoningUnavailableException.MESSAGE);
				throw new AiReasoningUnavailableException("could not parse steps from model output");
			}
			return capSteps(renumberSequentially(parsed), MAX_STEPS);
		}
		catch (AiReasoningUnavailableException ex) {
			throw ex;
		}
		catch (Exception ex) {
			log.warn("Groq planning failed: {} — {}", ex.getMessage(), AiReasoningUnavailableException.MESSAGE);
			throw new AiReasoningUnavailableException(ex);
		}
	}

	/**
	 * One-shot retry: asks Groq for a single replacement step after a Playwright failure.
	 *
	 * @return a replacement step from Groq, or empty if Groq is unavailable or output is not parseable (no heuristic steps)
	 */
	public Optional<TestStep> suggestAlternativeStep(
			TestStep failedStep,
			String failureMessage,
			String issueKey,
			String envBaseUrl) {
		if (failedStep == null) {
			return Optional.empty();
		}
		int num = failedStep.stepNumber();
		if (!groqProperties.isConfigured()) {
			log.warn("Zero-scripting mode: recovery skipped — {} (Groq not configured)", AiReasoningUnavailableException.MESSAGE);
			return Optional.empty();
		}
		try {
			log.info("Suggest next best action — Groq recovery call (issue {} step {})", issueKey, num);
			String failedJson = objectMapper.writeValueAsString(failedStep);
			String err = failureMessage == null ? "" : failureMessage.trim();
			if (err.length() > 4000) {
				err = err.substring(0, 3997) + "...";
			}
			String userPrompt =
					"Step failed:\n"
							+ failedJson
							+ "\n\nError:\n"
							+ err
							+ "\n\nSuggest next best action.\n\n"
							+ "Reply with ONLY a JSON array containing exactly ONE step object. "
							+ "Schema: stepNumber (optional), action (navigate|click|type|verify|api_get|api_post|api_put|api_patch|api_delete), "
							+ "target, value, expected — same rules as test planning. "
							+ "Base URL for navigate: "
							+ (envBaseUrl == null ? "" : envBaseUrl.trim())
							+ "\nSuggest one practical alternative that might succeed where the original failed.";

			String content = callGroqChat(userPrompt, issueKey);
			log.info("[Groq] suggest alternative raw output for issue {} step {}:\n{}", issueKey, num, content == null ? "" : content);
			List<TestStep> parsed = parseStepsFromLlmContent(content);
			if (!parsed.isEmpty()) {
				TestStep s = parsed.get(0);
				return Optional.of(
						new TestStep(
								num,
								s.action(),
								s.description(),
								s.target(),
								s.value(),
								s.expected()));
			}
			log.warn("Zero-scripting mode: suggest alternative parsed zero steps — {}", AiReasoningUnavailableException.MESSAGE);
		}
		catch (Exception ex) {
			log.warn("Groq suggest alternative failed: {} — {}", ex.getMessage(), AiReasoningUnavailableException.MESSAGE);
		}
		return Optional.empty();
	}

	/**
	 * Parses Groq output: prefers a JSON array of structured steps; falls back to numbered lines (still model text).
	 * Package-private for unit tests.
	 */
	List<TestStep> parseStepsFromLlmContent(String rawContent) {
		if (rawContent == null || rawContent.isBlank()) {
			return List.of();
		}
		String text = stripMarkdownFence(rawContent.trim());
		String jsonSlice = extractFirstJsonArray(text);
		if (jsonSlice != null) {
			try {
				List<TestStep> fromJson = parseStructuredJsonSteps(jsonSlice);
				if (!fromJson.isEmpty()) {
					return fromJson;
				}
			}
			catch (Exception e) {
				log.warn("Structured JSON step parse failed, falling back to numbered list: {}", e.getMessage());
			}
		}
		return parseNumberedStepsFromLlmContent(text);
	}

	/**
	 * Parses a numbered list from model output (one step per line: {@code N. ...}).
	 * Package-private for unit tests.
	 */
	List<TestStep> parseNumberedStepsFromLlmContent(String rawContent) {
		if (rawContent == null || rawContent.isBlank()) {
			return List.of();
		}
		String text = stripMarkdownFence(rawContent.trim());
		List<TestStep> out = new ArrayList<>();
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			Matcher m = NUMBERED_LINE.matcher(trimmed);
			if (!m.matches()) {
				continue;
			}
			int num = Integer.parseInt(m.group(1));
			String body = m.group(2).trim();
			if (body.isEmpty()) {
				continue;
			}
			String action = actionFromLeadingVerb(body);
			out.add(new TestStep(num, action, body));
			if (out.size() >= MAX_STEPS) {
				break;
			}
		}
		return renumberSequentially(out);
	}

	static String extractFirstJsonArray(String text) {
		if (text == null) {
			return null;
		}
		int start = text.indexOf('[');
		if (start < 0) {
			return null;
		}
		int depth = 0;
		for (int i = start; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '[') {
				depth++;
			}
			else if (c == ']') {
				depth--;
				if (depth == 0) {
					return text.substring(start, i + 1);
				}
			}
		}
		return null;
	}

	List<TestStep> parseStructuredJsonSteps(String jsonArray) throws com.fasterxml.jackson.core.JsonProcessingException {
		JsonNode root = objectMapper.readTree(jsonArray);
		if (!root.isArray()) {
			return List.of();
		}
		List<TestStep> out = new ArrayList<>();
		for (JsonNode n : root) {
			if (n == null || !n.isObject()) {
				continue;
			}
			int num = n.path("stepNumber").asInt(0);
			String action = n.path("action").asText("").trim().toLowerCase(Locale.ROOT);
			if (action.isEmpty()) {
				continue;
			}
			String target = textOrNull(n.path("target"));
			String value = textOrNull(n.path("value"));
			String expected = textOrNull(n.path("expected"));
			String desc = synthesizedDescription(action, target, value, expected);
			out.add(new TestStep(num, action, desc, target, value, expected));
			if (out.size() >= MAX_STEPS) {
				break;
			}
		}
		return renumberSequentially(out);
	}

	static String textOrNull(JsonNode n) {
		if (n == null || n.isNull() || n.isMissingNode()) {
			return null;
		}
		String t = n.asText("").trim();
		return t.isEmpty() ? null : t;
	}

	static String synthesizedDescription(String action, String target, String value, String expected) {
		StringBuilder sb = new StringBuilder();
		if (action != null && !action.isBlank()) {
			sb.append(action.trim());
		}
		if (target != null && !target.isBlank()) {
			sb.append(sb.length() > 0 ? " " : "").append(target.trim());
		}
		if (value != null && !value.isBlank()) {
			sb.append(sb.length() > 0 ? " " : "").append('[').append(value.trim()).append(']');
		}
		if (expected != null && !expected.isBlank()) {
			sb.append(sb.length() > 0 ? " — " : "").append("expect: ").append(expected.trim());
		}
		return sb.toString().trim();
	}

	private static String buildRequirementBlock(
			String summary,
			String description,
			String acceptanceCriteria,
			String issueKey) {
		StringBuilder sb = new StringBuilder();
		if (issueKey != null && !issueKey.isBlank()) {
			sb.append("Issue key: ").append(issueKey).append("\n\n");
		}
		sb.append("Requirement:\n");
		sb.append("Title: ").append(summary == null || summary.isEmpty() ? "(none)" : summary).append("\n\n");
		sb.append("Description:\n");
		if (description == null || description.isEmpty()) {
			sb.append("(none)\n");
		}
		else {
			sb.append(description).append("\n");
		}
		sb.append("\nAcceptance Criteria:\n");
		if (acceptanceCriteria == null || acceptanceCriteria.isEmpty()) {
			sb.append("(none)\n");
		}
		else {
			sb.append(acceptanceCriteria).append("\n");
		}
		return sb.toString();
	}

	private static String buildPlanUserPrompt(String jiraRequirementBlock, String envBaseUrl) {
		String baseUrlHint = envBaseUrl == null ? "" : envBaseUrl.trim();
		return "Convert the requirement into structured JSON test steps for BOTH UI (frontend) and HTTP API (backend) where relevant.\n\n"
				+ "Format (return ONLY a JSON array, no markdown fences, no commentary):\n"
				+ "[\n"
				+ "  {\n"
				+ "    \"stepNumber\": 1,\n"
				+ "    \"action\": \"navigate\",\n"
				+ "    \"target\": \"login page\",\n"
				+ "    \"value\": \"URL if applicable\",\n"
				+ "    \"expected\": \"what should happen\"\n"
				+ "  }\n"
				+ "]\n\n"
				+ "Rules:\n"
				+ "- \"action\" must be exactly one of: navigate, click, type, verify, api_get, api_post, api_put, api_patch, api_delete (lowercase).\n"
				+ "- For navigate: put the full URL in \"value\" when you have it; \"target\" is a short label (e.g. loan page).\n"
				+ "  Base URL for this app: " + baseUrlHint + "\n"
				+ "- For type: \"value\" is the text to enter; \"target\" is the field label or accessible name (e.g. Username).\n"
				+ "- For click: \"target\" is the button/link name or role label to click.\n"
				+ "- For verify: \"expected\" is the visible text or outcome to assert; \"target\" may be a selector or label hint.\n"
				+ "- For api_*: \"target\" MUST be the path only starting with / (e.g. /api/loan/list). Base host is implied from the app URL above.\n"
				+ "  For api_post/api_put/api_patch: \"value\" is a JSON string body when needed.\n"
				+ "  For api_*: \"expected\" is HTTP status (e.g. 200) or 2xx, or a substring that must appear in the response body.\n"
				+ "- Include a mix of FE (navigate/click/type/verify) and BE (api_*) steps when the story implies APIs or data contracts.\n"
				+ "- Include \"expected\" on every step where it clarifies acceptance.\n"
				+ "- Keep steps minimal and ordered.\n\n"
				+ jiraRequirementBlock;
	}

	/**
	 * Minimal Groq chat completion (single user message). Shared by planning and failure retry.
	 */
	private String callGroqChat(String userPrompt, String issueKey)
			throws com.fasterxml.jackson.core.JsonProcessingException {
		String base = groqProperties.getBaseUrl().trim().replaceAll("/+$", "");
		Map<String, Object> userMsg = Map.of("role", "user", "content", userPrompt);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", groqProperties.getModel());
		body.put("temperature", 0.2);
		body.put("messages", List.of(userMsg));

		String jsonPayload = objectMapper.writeValueAsString(body);
		log.debug("[Groq] POST {}/chat/completions model={} issue={}", base, groqProperties.getModel(), issueKey);

		String response = postGroqWithRetries(base, jsonPayload, issueKey);

		if (response == null || response.isBlank()) {
			throw new IllegalArgumentException("empty Groq response");
		}
		var tree = objectMapper.readTree(response);
		return tree.path("choices").path(0).path("message").path("content").asText("");
	}

	private String postGroqWithRetries(String base, String jsonPayload, String logIssueKey) {
		RestClientException last = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				RestClient client = RestClient.builder()
						.baseUrl(base)
						.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + groqProperties.getApiKey().trim())
						.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
						.build();
				return client.post()
						.uri("/chat/completions")
						.contentType(MediaType.APPLICATION_JSON)
						.body(jsonPayload)
						.retrieve()
						.body(String.class);
			}
			catch (RestClientException ex) {
				last = ex;
				if (!HttpRetry.isRetryable(ex) || attempt == 2) {
					throw ex;
				}
				log.warn("Groq HTTP retry {} for issue {}: {}", attempt + 1, logIssueKey, ex.getMessage());
				HttpRetry.sleepBackoffMs(attempt);
			}
		}
		throw last != null ? last : new IllegalStateException("Groq request failed after retries");
	}

	private static String stripMarkdownFence(String s) {
		if (s.startsWith("```")) {
			int start = s.indexOf('\n');
			if (start < 0) {
				return s;
			}
			s = s.substring(start + 1);
			int end = s.lastIndexOf("```");
			if (end > 0) {
				s = s.substring(0, end);
			}
		}
		return s.trim();
	}

	/** If the line starts with navigate|click|type|verify, use it; else infer from wording. */
	static String actionFromLeadingVerb(String line) {
		String trimmed = line.trim();
		int space = trimmed.indexOf(' ');
		String first = space < 0 ? trimmed : trimmed.substring(0, space);
		first = first.toLowerCase(Locale.ROOT).replaceAll("[,:.;]+$", "");
		if (first.equals("navigate")
				|| first.equals("click")
				|| first.equals("type")
				|| first.equals("verify")
				|| first.equals("api_get")
				|| first.equals("api_post")
				|| first.equals("api_put")
				|| first.equals("api_patch")
				|| first.equals("api_delete")) {
			return first;
		}
		return inferAction(trimmed);
	}

	/** Map natural language to one of: navigate, click, type, verify, api_*. */
	static String inferAction(String line) {
		String lower = line.toLowerCase(Locale.ROOT);
		if (lower.matches("(?i)^(api_get|api_post|api_put|api_patch|api_delete)\\b.*")) {
			int sp = lower.indexOf(' ');
			return sp > 0 ? lower.substring(0, sp) : lower;
		}
		if (lower.matches("(?i)^(get|post|put|patch|delete)\\s+/api\\b.*")) {
			String[] parts = lower.split("\\s+", 3);
			return "api_" + parts[0];
		}
		if (lower.contains("http://") || lower.contains("https://")) {
			if (lower.matches("(?i)^(navigate|open|visit|go to|browse|load|nav)\\b.*")
					|| lower.matches("(?i)^\\d+\\.\\s*(navigate|open|visit|go to|browse|load).*")) {
				return "navigate";
			}
			if (lower.matches("(?i).*(open|visit|navigate|go to|load)\\s+(the\\s+)?(url|page|site|app).*")) {
				return "navigate";
			}
		}
		if (lower.matches("(?i)^(navigate|open|visit|go to|browse|load|nav)\\b.*")) {
			return "navigate";
		}
		if (lower.matches("(?i)^(verify|assert|check|ensure|validate|confirm)\\b.*")) {
			return "verify";
		}
		if (lower.matches("(?i)^(type|enter|fill|input|set)\\b.*")) {
			return "type";
		}
		if (lower.matches("(?i)^(click|tap|press)\\b.*")) {
			return "click";
		}
		if (lower.contains("verify ") || lower.contains("assert ") || lower.contains("check that")) {
			return "verify";
		}
		if (lower.contains("click ") || lower.contains("tap ")) {
			return "click";
		}
		if (lower.contains("type ") || lower.contains("enter ") || lower.contains("fill ")) {
			return "type";
		}
		return "verify";
	}

	private static List<TestStep> renumberSequentially(List<TestStep> steps) {
		List<TestStep> out = new ArrayList<>();
		int n = 1;
		for (TestStep s : steps) {
			out.add(new TestStep(n++, s.action(), s.description(), s.target(), s.value(), s.expected()));
		}
		return out;
	}

	private static List<TestStep> capSteps(List<TestStep> steps, int max) {
		if (steps.size() <= max) {
			return steps;
		}
		return new ArrayList<>(steps.subList(0, max));
	}

	/**
	 * OpenAI-compatible chat completion with system + user messages (QA Sentinel AI agent).
	 * Returns empty string when Groq is not configured or the call fails.
	 */
	public String completeChat(String systemPrompt, String userPrompt) {
		if (!groqProperties.isConfigured()) {
			return "";
		}
		String sys = systemPrompt == null ? "" : systemPrompt.trim();
		String usr = userPrompt == null ? "" : userPrompt.trim();
		if (usr.isEmpty()) {
			return "";
		}
		try {
			return callGroqChatCompletion(sys, usr, "agent");
		}
		catch (Exception ex) {
			log.warn("Groq agent chat failed: {}", ex.getMessage());
			return "";
		}
	}

	private String callGroqChatCompletion(String systemContent, String userContent, String logLabel)
			throws com.fasterxml.jackson.core.JsonProcessingException {
		String base = groqProperties.getBaseUrl().trim().replaceAll("/+$", "");
		List<Map<String, Object>> messages = new ArrayList<>();
		if (!systemContent.isEmpty()) {
			messages.add(Map.of("role", "system", "content", systemContent));
		}
		messages.add(Map.of("role", "user", "content", userContent));

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", groqProperties.getModel());
		body.put("temperature", 0.35);
		body.put("max_tokens", 1024);
		body.put("messages", messages);

		String jsonPayload = objectMapper.writeValueAsString(body);
		log.debug("[Groq] POST {}/chat/completions model={} label={}", base, groqProperties.getModel(), logLabel);

		String response = postGroqWithRetries(base, jsonPayload, logLabel);

		if (response == null || response.isBlank()) {
			throw new IllegalArgumentException("empty Groq response");
		}
		var tree = objectMapper.readTree(response);
		return tree.path("choices").path(0).path("message").path("content").asText("");
	}
}
