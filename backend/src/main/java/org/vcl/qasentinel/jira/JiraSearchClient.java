package org.vcl.qasentinel.jira;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.vcl.qasentinel.config.JiraProperties;
import org.vcl.qasentinel.util.HttpRetry;

@Component
@RequiredArgsConstructor
@Slf4j
public class JiraSearchClient {

	/** Replaces removed {@code GET /rest/api/3/search} (see Atlassian CHANGE-2046). */
	private static final String SEARCH_JQL_PATH = "/rest/api/3/search/jql";

	private final JiraProperties jiraProperties;
	private final ObjectMapper objectMapper;

	public List<JiraIssueView> searchByProject(String projectKey) {
		requireLiveJira();
		String jql = "project = " + projectKey + " ORDER BY updated DESC";
		try {
			return fetchFromJira(jql, jiraProperties.getMaxResults());
		}
		catch (RestClientException ex) {
			throw new IllegalStateException("Jira search failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Full issue rows (summary, status, assignee, updated) for arbitrary JQL; {@code maxResults} capped by {@link JiraProperties#getMaxResults()}.
	 */
	public List<JiraIssueView> searchByJql(String jql, int maxResults) {
		requireLiveJira();
		if (jql == null || jql.isBlank()) {
			return List.of();
		}
		int cap = Math.max(1, Math.min(maxResults, jiraProperties.getMaxResults()));
		try {
			return fetchFromJira(jql, cap);
		}
		catch (RestClientException ex) {
			throw new IllegalStateException("Jira search failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Returns issue keys for arbitrary JQL (keys only), capped by {@code maxResults} and {@link JiraProperties#getMaxResults()}.
	 */
	public List<String> searchIssueKeys(String jql, int maxResults) {
		requireLiveJira();
		if (jql == null || jql.isBlank()) {
			return List.of();
		}
		int cap = Math.max(1, Math.min(maxResults, jiraProperties.getMaxResults()));
		try {
			log.info("Jira JQL Query: {}", jql);
			String body = executeSearchJql(jql, cap, List.of("key"));
			if (body == null || body.isBlank()) {
				log.info("Jira issues fetched: {}", 0);
				return List.of();
			}
			JsonNode issues = objectMapper.readTree(body).path("issues");
			if (!issues.isArray()) {
				log.info("Jira issues fetched: {}", 0);
				return List.of();
			}
			List<String> keys = new ArrayList<>();
			for (JsonNode issue : issues) {
				String k = issue.path("key").asText("").trim();
				if (!k.isEmpty()) {
					keys.add(k);
				}
			}
			log.info("Jira issues fetched: {}", keys.size());
			return Collections.unmodifiableList(keys);
		}
		catch (RestClientException ex) {
			throw new IllegalStateException("Jira search failed: " + ex.getMessage(), ex);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Jira search JSON parse failed: " + e.getMessage(), e);
		}
	}

	/**
	 * Fetches summary, description, and optional Acceptance Criteria custom field(s) from live Jira only.
	 */
	public JiraIssuePrd fetchIssuePrd(String issueKey) {
		if (issueKey == null || issueKey.isBlank()) {
			return new JiraIssuePrd("", "", "", "");
		}
		String key = issueKey.trim().toUpperCase(Locale.ROOT);
		requireLiveJira();
		try {
			return fetchIssuePrdFromJira(key);
		}
		catch (RestClientException ex) {
			throw new IllegalStateException("Jira issue fetch failed for " + key + ": " + ex.getMessage(), ex);
		}
	}

	private void requireLiveJira() {
		if (!jiraProperties.isEnabled() || !jiraProperties.isRealConnectionConfigured()) {
			throw new JiraConfigurationException();
		}
	}

	private String issuePrdFieldsParam() {
		StringBuilder sb = new StringBuilder("summary,description");
		String csv = jiraProperties.getAcceptanceCriteriaFieldIds();
		if (csv == null || csv.isBlank()) {
			return sb.toString();
		}
		for (String raw : csv.split(",")) {
			String id = raw == null ? "" : raw.trim();
			if (!id.isEmpty()) {
				sb.append(',').append(id);
			}
		}
		return sb.toString();
	}

	private JiraIssuePrd fetchIssuePrdFromJira(String issueKey) {
		String base = jiraProperties.getApiBaseUrl().trim().replaceAll("/+$", "");
		RestClient client = RestClient.builder()
				.baseUrl(base)
				.defaultHeader(HttpHeaders.ACCEPT, "application/json")
				.build();
		String fields = issuePrdFieldsParam();
		RestClientException last = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				String body = client.get()
						.uri(uriBuilder -> uriBuilder
								.path("/rest/api/3/issue/{key}")
								.queryParam("fields", fields)
								.build(issueKey))
						.headers(h -> h.setBasicAuth(jiraProperties.getEmail(), jiraProperties.getApiToken()))
						.retrieve()
						.body(String.class);
				if (body == null || body.isBlank()) {
					throw new IllegalStateException("Jira returned empty body for issue " + issueKey);
				}
				JsonNode root = objectMapper.readTree(body);
				JsonNode fieldsNode = root.path("fields");
				String summary = fieldsNode.path("summary").asText("");
				String descPlain = JiraAdfText.toPlainText(fieldsNode.path("description"));
				String acPlain = extractAcceptanceCriteriaPlain(fieldsNode);
				return new JiraIssuePrd(issueKey, summary, descPlain, acPlain);
			}
			catch (JsonProcessingException e) {
				throw new IllegalStateException("Jira issue JSON parse failed for " + issueKey + ": " + e.getMessage(), e);
			}
			catch (RestClientException ex) {
				last = ex;
				if (!HttpRetry.isRetryable(ex) || attempt == 2) {
					throw ex;
				}
				HttpRetry.sleepBackoffMs(attempt);
			}
		}
		throw last != null ? last : new IllegalStateException("Jira issue fetch failed for " + issueKey);
	}

	private String extractAcceptanceCriteriaPlain(JsonNode fields) {
		String csv = jiraProperties.getAcceptanceCriteriaFieldIds();
		if (csv == null || csv.isBlank()) {
			return "";
		}
		List<String> parts = new ArrayList<>();
		for (String raw : csv.split(",")) {
			String id = raw == null ? "" : raw.trim();
			if (id.isEmpty()) {
				continue;
			}
			String plain = nodeToPlain(fields.path(id));
			if (!plain.isBlank()) {
				parts.add(plain.trim());
			}
		}
		return String.join("\n\n---\n\n", parts);
	}

	/**
	 * Normalizes Jira custom field payloads (string, ADF doc, paragraph blocks, arrays, {@code value} wrappers) to plain text.
	 */
	private String nodeToPlain(JsonNode n) {
		if (n == null || n.isNull() || n.isMissingNode()) {
			return "";
		}
		if (n.isTextual()) {
			return n.asText("").trim();
		}
		if (n.isNumber() || n.isBoolean()) {
			return n.asText();
		}
		if (n.isArray()) {
			List<String> bits = new ArrayList<>();
			for (JsonNode el : n) {
				String p = nodeToPlain(el);
				if (!p.isBlank()) {
					bits.add(p.trim());
				}
			}
			return String.join("\n", bits);
		}
		if (n.isObject()) {
			if ("doc".equals(n.path("type").asText(""))) {
				return JiraAdfText.toPlainText(n);
			}
			JsonNode value = n.get("value");
			if (value != null && !value.isNull()) {
				if (value.isTextual()) {
					return value.asText("").trim();
				}
				String nested = nodeToPlain(value);
				if (!nested.isBlank()) {
					return nested;
				}
			}
			if (n.has("content") && n.get("content").isArray()) {
				ObjectNode wrapDoc = objectMapper.createObjectNode();
				wrapDoc.put("type", "doc");
				wrapDoc.put("version", 1);
				ArrayNode content = wrapDoc.putArray("content");
				content.add(n);
				return JiraAdfText.toPlainText(wrapDoc);
			}
		}
		return "";
	}

	private List<JiraIssueView> fetchFromJira(String jql, int maxResults) {
		log.info("Jira JQL Query: {}", jql);
		String body = executeSearchJql(
				jql,
				maxResults,
				List.of("summary", "status", "assignee", "updated"));

		if (body == null || body.isBlank()) {
			log.info("Jira issues fetched: {}", 0);
			return List.of();
		}
		try {
			List<JiraIssueView> parsed = parseIssueArray(objectMapper.readTree(body).path("issues"));
			log.info("Jira issues fetched: {}", parsed.size());
			return parsed;
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Jira search response could not be parsed: " + e.getMessage(), e);
		}
	}

	private List<JiraIssueView> parseIssueArray(JsonNode issuesNode) {
		List<JiraIssueView> out = new ArrayList<>();
		if (!issuesNode.isArray()) {
			return out;
		}
		for (JsonNode issue : issuesNode) {
			String key = issue.path("key").asText("");
			JsonNode fields = issue.path("fields");
			String summary = fields.path("summary").asText("");
			String status = fields.path("status").path("name").asText("Unknown");
			String assignee = "Unassigned";
			if (fields.path("assignee").isObject() && !fields.path("assignee").isNull()) {
				assignee = fields.path("assignee").path("displayName").asText("Unassigned");
			}
			Instant updated = parseInstant(fields.path("updated").asText(null));
			if (updated == null) {
				updated = Instant.now();
			}
			String descriptionPlain = fields.path("descriptionPlain").asText("");
			String acceptanceCriteriaPlain = fields.path("acceptanceCriteriaPlain").asText("");
			out.add(new JiraIssueView(key, summary, status, assignee, updated, descriptionPlain, acceptanceCriteriaPlain));
		}
		return out;
	}

	private static Instant parseInstant(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(raw).toInstant();
		}
		catch (DateTimeParseException e) {
			try {
				if (raw.endsWith("+0000")) {
					return OffsetDateTime.parse(raw.substring(0, raw.length() - 5) + "Z").toInstant();
				}
			}
			catch (Exception ignored) {
				// fall through
			}
			return null;
		}
	}

	/**
	 * Jira Cloud enhanced JQL search (GET). Response still includes an {@code issues} array; pagination uses
	 * {@code nextPageToken} when needed (we only request the first page here).
	 */
	public List<String> listIssueTypeNamesForProject(String projectKey) {
		requireLiveJira();
		String pk = projectKey == null ? "" : projectKey.trim().toUpperCase(Locale.ROOT);
		if (pk.isBlank()) {
			return List.of();
		}
		String base = jiraProperties.getApiBaseUrl().trim().replaceAll("/+$", "");
		RestClient client = RestClient.builder()
				.baseUrl(base)
				.defaultHeader(HttpHeaders.ACCEPT, "application/json")
				.build();
		try {
			String projectBody = withRestRetries(
					() -> client.get()
							.uri("/rest/api/3/project/{key}", pk)
							.headers(h -> h.setBasicAuth(jiraProperties.getEmail(), jiraProperties.getApiToken()))
							.retrieve()
							.body(String.class));
			if (projectBody == null || projectBody.isBlank()) {
				throw new IllegalStateException("Jira returned empty project body for " + pk);
			}
			String projectId = objectMapper.readTree(projectBody).path("id").asText("").trim();
			if (projectId.isEmpty()) {
				throw new IllegalStateException("Jira project response missing id for " + pk);
			}
			String typesBody = withRestRetries(
					() -> client.get()
							.uri(uriBuilder -> uriBuilder
									.path("/rest/api/3/issuetype/project")
									.queryParam("projectId", projectId)
									.build())
							.headers(h -> h.setBasicAuth(jiraProperties.getEmail(), jiraProperties.getApiToken()))
							.retrieve()
							.body(String.class));
			if (typesBody == null || typesBody.isBlank()) {
				return List.of();
			}
			JsonNode arr = objectMapper.readTree(typesBody);
			if (!arr.isArray()) {
				return List.of();
			}
			List<String> names = new ArrayList<>();
			for (JsonNode n : arr) {
				String name = n.path("name").asText("").trim();
				if (!name.isEmpty()) {
					names.add(name);
				}
			}
			return Collections.unmodifiableList(names);
		}
		catch (RestClientException ex) {
			throw new IllegalStateException("Jira issue types list failed: " + ex.getMessage(), ex);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Jira issue types JSON parse failed: " + e.getMessage(), e);
		}
	}

	private static String withRestRetries(RestSupplier supplier) {
		RestClientException last = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				return supplier.get();
			}
			catch (RestClientException ex) {
				last = ex;
				if (!HttpRetry.isRetryable(ex) || attempt == 2) {
					throw ex;
				}
				HttpRetry.sleepBackoffMs(attempt);
			}
		}
		throw last != null ? last : new IllegalStateException("Jira request failed after retries");
	}

	@FunctionalInterface
	private interface RestSupplier {
		String get() throws RestClientException;
	}

	private String executeSearchJql(String jql, int maxResults, List<String> fields) {
		String base = jiraProperties.getApiBaseUrl().trim().replaceAll("/+$", "");
		RestClient client = RestClient.builder()
				.baseUrl(base)
				.defaultHeader(HttpHeaders.ACCEPT, "application/json")
				.build();
		RestClientException last = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				return client.get()
						.uri(uriBuilder -> buildSearchJqlUri(uriBuilder, jql, maxResults, fields))
						.headers(h -> h.setBasicAuth(jiraProperties.getEmail(), jiraProperties.getApiToken()))
						.retrieve()
						.body(String.class);
			}
			catch (RestClientException ex) {
				last = ex;
				if (!HttpRetry.isRetryable(ex) || attempt == 2) {
					throw ex;
				}
				HttpRetry.sleepBackoffMs(attempt);
			}
		}
		throw last != null ? last : new IllegalStateException("Jira search failed after retries");
	}

	private static URI buildSearchJqlUri(UriBuilder uriBuilder, String jql, int maxResults, List<String> fields) {
		UriBuilder b = uriBuilder.path(SEARCH_JQL_PATH).queryParam("jql", jql).queryParam("maxResults", maxResults);
		for (String f : fields) {
			if (f != null && !f.isBlank()) {
				b = b.queryParam("fields", f.trim());
			}
		}
		return b.build();
	}
}
