package org.vcl.qasentinel.jira;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.vcl.qasentinel.config.JiraProperties;
import org.vcl.qasentinel.logging.QaSentinelNarrative;

@Service
@RequiredArgsConstructor
@Slf4j
public class JiraBugService {

	private static final int SUMMARY_MAX_LEN = 200;

	private final JiraProperties jiraProperties;
	private final ObjectMapper objectMapper;

	/**
	 * Creates a Jira issue of type {@link JiraProperties#getBugIssueTypeName()} with plain-text description (ADF),
	 * verifies the created issue type, uploads all provided attachment files that exist on disk, and links the bug to
	 * {@code relatedStoryKey} using {@link JiraProperties#getBugLinkTypeName()}. When {@link JiraProperties#isBugParentStoryEnabled()}
	 * is true, also sets {@code parent} to the story so the bug can appear nested under it in supported Jira views.
	 *
	 * @throws JiraConfigurationException if Jira is not enabled or credentials are missing
	 * @throws IllegalStateException if the story key is blank, create/verify/link/attach fails, or issuetype does not match
	 * @param assigneeAccountId Jira Cloud {@code accountId}, or null/blank to leave unassigned
	 */
	public String createQaSentinelBug(
			String projectKey,
			String summary,
			String descriptionPlain,
			String relatedStoryKey,
			List<Path> attachmentPaths,
			String assigneeAccountId) {
		requireLiveJira();
		if (relatedStoryKey == null || relatedStoryKey.isBlank()) {
			throw new IllegalStateException("Original Jira story key is required to file QA Sentinel bugs.");
		}
		String base = jiraProperties.getApiBaseUrl().trim().replaceAll("/+$", "");
		String sum = summary == null ? "" : summary.trim();
		if (sum.isEmpty()) {
			sum = "QA Sentinel bug";
		}
		if (sum.length() > SUMMARY_MAX_LEN) {
			sum = sum.substring(0, SUMMARY_MAX_LEN - 3) + "...";
		}
		String desc = descriptionPlain == null || descriptionPlain.isBlank()
				? "QA Sentinel reported an event. See trace and logs in the QA service."
				: descriptionPlain;

		String issueType = jiraProperties.getBugIssueTypeName() == null || jiraProperties.getBugIssueTypeName().isBlank()
				? "Bug"
				: jiraProperties.getBugIssueTypeName().trim();

		RestClient client = jiraRestClient(base);
		try {
			boolean wantParent = jiraProperties.isBugParentStoryEnabled();
			String responseBody = tryCreateIssueReturnBody(client, projectKey, sum, desc, issueType, relatedStoryKey, assigneeAccountId, wantParent);
			if (wantParent) {
				QaSentinelNarrative.line(log, "→ Jira parent set to story " + relatedStoryKey.trim() + " (jira.bug-parent-story-enabled=true)");
			}
			String key = objectMapper.readTree(responseBody).path("key").asText("");
			if (key.isEmpty()) {
				throw new IllegalStateException("Jira create returned empty issue key");
			}
			QaSentinelNarrative.line(log, "→ Jira " + issueType + " created: " + key);

			verifyCreatedIssueType(client, key, issueType);
			uploadAttachmentsMandatory(client, key, attachmentPaths);
			linkBugToStoryMandatory(client, key, relatedStoryKey.trim());

			return key;
		}
		catch (RestClientResponseException first) {
			if (jiraProperties.isBugParentStoryEnabled()
					&& first.getStatusCode().value() == 400
					&& isParentHierarchyRejected(first.getResponseBodyAsString())) {
				log.warn(
						"Jira rejected parent field for story {} ({}). Retrying create without parent; link-only via {}.",
						relatedStoryKey.trim(),
						abbreviateJiraError(first.getResponseBodyAsString()),
						jiraProperties.getBugLinkTypeName());
				QaSentinelNarrative.line(
						log,
						"→ Jira parent rejected by hierarchy; retrying bug create link-only (set jira.bug-parent-story-enabled=false to skip parent on first try)");
				try {
					String responseBody =
							tryCreateIssueReturnBody(client, projectKey, sum, desc, issueType, relatedStoryKey, assigneeAccountId, false);
					String key = objectMapper.readTree(responseBody).path("key").asText("");
					if (key.isEmpty()) {
						throw new IllegalStateException("Jira create returned empty issue key");
					}
					QaSentinelNarrative.line(log, "→ Jira " + issueType + " created: " + key);
					verifyCreatedIssueType(client, key, issueType);
					uploadAttachmentsMandatory(client, key, attachmentPaths);
					linkBugToStoryMandatory(client, key, relatedStoryKey.trim());
					return key;
				}
				catch (Exception e) {
					throw new IllegalStateException("Jira bug create failed after parent fallback: " + e.getMessage(), e);
				}
			}
			throw new IllegalStateException(
					"Jira bug create failed: HTTP "
							+ first.getStatusCode().value()
							+ " "
							+ abbreviateJiraError(first.getResponseBodyAsString()),
					first);
		}
		catch (RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalStateException("Jira bug create failed: " + e.getMessage(), e);
		}
	}

	private static boolean isParentHierarchyRejected(String responseBody) {
		if (responseBody == null || responseBody.isBlank()) {
			return false;
		}
		String b = responseBody.toLowerCase();
		return b.contains("parentid")
				&& (b.contains("hierarchy") || b.contains("appropriate") || b.contains("does not belong"));
	}

	private static String abbreviateJiraError(String body) {
		if (body == null) {
			return "";
		}
		String t = body.replace('\r', ' ').replace('\n', ' ').trim();
		return t.length() > 800 ? t.substring(0, 797) + "…" : t;
	}

	private String tryCreateIssueReturnBody(
			RestClient client,
			String projectKey,
			String sum,
			String desc,
			String issueType,
			String relatedStoryKey,
			String assigneeAccountId,
			boolean includeParent)
			throws Exception {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("project", Map.of("key", projectKey));
		fields.put("summary", sum);
		fields.put("issuetype", Map.of("name", issueType));
		fields.put("description", simpleAdfFromPlainText(desc));
		if (includeParent) {
			fields.put("parent", Map.of("key", relatedStoryKey.trim()));
		}
		if (assigneeAccountId != null && !assigneeAccountId.isBlank()) {
			fields.put("assignee", Map.of("id", assigneeAccountId.trim()));
		}
		Map<String, Object> payload = Map.of("fields", fields);
		String json = objectMapper.writeValueAsString(payload);
		return client.post()
				.uri("/rest/api/3/issue")
				.contentType(MediaType.APPLICATION_JSON)
				.body(json)
				.retrieve()
				.body(String.class);
	}

	/**
	 * Legacy / external CI body; summary references the related issue.
	 *
	 * @param attachmentPaths optional files to upload after create (e.g. failure screenshot); same as Playwright QA bugs
	 */
	public String createPocFinding(
			String projectKey,
			String relatedIssueKey,
			String traceId,
			String bodyText,
			List<Path> attachmentPaths,
			String assigneeAccountId) {
		String summary = "[POC QA] Validation failed — relates to %s".formatted(relatedIssueKey);
		String desc = (bodyText == null || bodyText.isBlank())
				? "QA Sentinel reported a failure. Attach screenshot from qa-runner/test-results/ if available."
				: bodyText;
		if (traceId != null && !traceId.isBlank()) {
			desc = desc + "\n\nTrace: " + traceId;
		}
		List<Path> attach = attachmentPaths == null ? List.of() : attachmentPaths;
		return createQaSentinelBug(projectKey, summary, desc, relatedIssueKey, attach, assigneeAccountId);
	}

	private void requireLiveJira() {
		if (!jiraProperties.isEnabled() || !jiraProperties.isRealConnectionConfigured()) {
			throw new JiraConfigurationException();
		}
	}

	private RestClient jiraRestClient(String base) {
		return RestClient.builder()
				.baseUrl(base)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeaders(h -> h.setBasicAuth(jiraProperties.getEmail(), jiraProperties.getApiToken()))
				.build();
	}

	private void verifyCreatedIssueType(RestClient client, String issueKey, String expectedTypeName) {
		try {
			String body = client.get()
					.uri("/rest/api/3/issue/{issueKey}?fields=issuetype", issueKey)
					.retrieve()
					.body(String.class);
			JsonNode root = objectMapper.readTree(body);
			String name = root.path("fields").path("issuetype").path("name").asText("").trim();
			if (name.isEmpty()) {
				throw new IllegalStateException("Jira issue " + issueKey + " has no issuetype in API response");
			}
			if (!name.equalsIgnoreCase(expectedTypeName.trim())) {
				throw new IllegalStateException(
						"Jira issue "
								+ issueKey
								+ " issuetype is '"
								+ name
								+ "' but QA Sentinel requires '"
								+ expectedTypeName
								+ "' (set jira.bug-issue-type-name to match your project).");
			}
			QaSentinelNarrative.line(log, "→ Verified issuetype for " + issueKey + ": " + name);
		}
		catch (IllegalStateException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalStateException("Could not verify Jira issuetype for " + issueKey + ": " + e.getMessage(), e);
		}
	}

	private void uploadAttachmentsMandatory(RestClient client, String issueKey, List<Path> attachmentPaths) {
		if (attachmentPaths == null || attachmentPaths.isEmpty()) {
			return;
		}
		List<Path> existing = attachmentPaths.stream()
				.filter(Objects::nonNull)
				.filter(Files::isRegularFile)
				.distinct()
				.toList();
		if (existing.isEmpty()) {
			boolean hadPaths = attachmentPaths.stream().anyMatch(Objects::nonNull);
			if (hadPaths) {
				log.warn(
						"Jira {}: attachment upload skipped — none of the candidate file(s) exist on disk: {}",
						issueKey,
						attachmentPaths);
			}
			return;
		}
		try {
			MultipartBodyBuilder builder = new MultipartBodyBuilder();
			for (Path path : existing) {
				String filename = path.getFileName().toString();
				builder.part("file", new FileSystemResource(path.toFile()), MediaType.APPLICATION_OCTET_STREAM)
						.filename(filename);
			}
			client.post()
					.uri("/rest/api/3/issue/{issueKey}/attachments", issueKey)
					.header("X-Atlassian-Token", "no-check")
					.body(builder.build())
					.retrieve()
					.toBodilessEntity();
			QaSentinelNarrative.line(
					log,
					"→ Attached " + existing.size() + " file(s) to " + issueKey + " (screenshot / trace / logs)");
		}
		catch (Exception e) {
			throw new IllegalStateException("Jira attachment upload failed for " + issueKey + ": " + e.getMessage(), e);
		}
	}

	private void linkBugToStoryMandatory(RestClient client, String newBugKey, String storyKey) {
		String linkType = jiraProperties.getBugLinkTypeName() == null || jiraProperties.getBugLinkTypeName().isBlank()
				? "Relates"
				: jiraProperties.getBugLinkTypeName().trim();
		Map<String, Object> body = Map.of(
				"type", Map.of("name", linkType),
				"inwardIssue", Map.of("key", newBugKey.trim()),
				"outwardIssue", Map.of("key", storyKey.trim()));
		try {
			String json = objectMapper.writeValueAsString(body);
			client.post()
					.uri("/rest/api/3/issueLink")
					.contentType(MediaType.APPLICATION_JSON)
					.body(json)
					.retrieve()
					.toBodilessEntity();
			QaSentinelNarrative.line(log, "→ Linked " + newBugKey + " to story " + storyKey + " (" + linkType + ")");
		}
		catch (Exception e) {
			throw new IllegalStateException(
					"Jira issue link failed ("
							+ newBugKey
							+ " → "
							+ storyKey
							+ ", type "
							+ linkType
							+ "): "
							+ e.getMessage(),
					e);
		}
	}

	/** Minimal ADF: split on blank lines into paragraphs for readability in Jira. */
	private static Map<String, Object> simpleAdfFromPlainText(String text) {
		String safe = text.replace("\r\n", "\n");
		String[] blocks = safe.split("\n{2,}", -1);
		List<Map<String, Object>> content = new ArrayList<>();
		for (String block : blocks) {
			String line = block.replace('\n', ' ').trim();
			if (line.isEmpty()) {
				continue;
			}
			content.add(Map.of(
					"type", "paragraph",
					"content", List.of(Map.of("type", "text", "text", line))));
		}
		if (content.isEmpty()) {
			content.add(Map.of(
					"type", "paragraph",
					"content", List.of(Map.of("type", "text", "text", "(empty)"))));
		}
		return Map.of("type", "doc", "version", 1, "content", content);
	}
}
