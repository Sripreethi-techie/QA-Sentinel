package org.vcl.qasentinel.jira;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.vcl.qasentinel.config.JiraProperties;

/**
 * Resolves {@link JiraProperties#getDefaultBugAssigneeAccountId()} or looks up
 * {@link JiraProperties#getDefaultBugAssigneeDisplayName()} via Jira user search (cached).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JiraAssigneeResolver {

	private final JiraProperties jiraProperties;
	private final ObjectMapper objectMapper;

	private final ConcurrentHashMap<String, Optional<String>> cache = new ConcurrentHashMap<>();

	/**
	 * @return Jira Cloud {@code accountId} for assignee field, or empty if not configured / not found
	 */
	public Optional<String> resolveDefaultBugAssigneeAccountId() {
		String direct = jiraProperties.getDefaultBugAssigneeAccountId();
		if (direct != null && !direct.isBlank()) {
			return Optional.of(direct.trim());
		}
		String display = jiraProperties.getDefaultBugAssigneeDisplayName();
		if (display == null || display.isBlank()) {
			return Optional.empty();
		}
		String key = display.trim().toLowerCase(Locale.ROOT);
		return cache.computeIfAbsent(key, k -> searchAccountIdByDisplayName(display.trim()));
	}

	private Optional<String> searchAccountIdByDisplayName(String wantedDisplayName) {
		if (!jiraProperties.isEnabled() || !jiraProperties.isRealConnectionConfigured()) {
			return Optional.empty();
		}
		String base = jiraProperties.getBaseUrl().trim().replaceAll("/+$", "");
		RestClient client = RestClient.builder()
				.baseUrl(base)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeaders(h -> h.setBasicAuth(jiraProperties.getEmail(), jiraProperties.getApiToken()))
				.build();
		try {
			String body = client.get()
					.uri(uriBuilder -> uriBuilder.path("/rest/api/3/user/search")
							.queryParam("query", wantedDisplayName)
							.queryParam("maxResults", 10)
							.build())
					.retrieve()
					.body(String.class);
			if (body == null || body.isBlank()) {
				return Optional.empty();
			}
			JsonNode root = objectMapper.readTree(body);
			JsonNode users = root.path("users");
			if (!users.isArray() || users.isEmpty()) {
				if (root.isArray() && !root.isEmpty()) {
					users = root;
				}
				else {
					log.warn("Jira user search returned no users for query: {}", wantedDisplayName);
					return Optional.empty();
				}
			}
			String wantedNorm = wantedDisplayName.toLowerCase(Locale.ROOT);
			for (JsonNode u : users) {
				String dn = userDisplayName(u);
				if (dn.toLowerCase(Locale.ROOT).equals(wantedNorm)) {
					String id = userAccountId(u);
					if (!id.isEmpty()) {
						log.info("Jira default bug assignee resolved: {} → accountId {}", wantedDisplayName, id);
						return Optional.of(id);
					}
				}
			}
			JsonNode first = users.get(0);
			String id = userAccountId(first);
			if (!id.isEmpty()) {
				log.warn(
						"Jira user search: no exact displayName match for '{}'; using first hit: {}",
						wantedDisplayName,
						userDisplayName(first));
				return Optional.of(id);
			}
		}
		catch (Exception e) {
			log.warn("Jira user search failed for '{}': {}", wantedDisplayName, e.getMessage());
		}
		return Optional.empty();
	}

	private static String userAccountId(JsonNode u) {
		if (u == null || u.isNull()) {
			return "";
		}
		String id = u.path("accountId").asText("").trim();
		if (!id.isEmpty()) {
			return id;
		}
		return u.path("user").path("accountId").asText("").trim();
	}

	private static String userDisplayName(JsonNode u) {
		if (u == null || u.isNull()) {
			return "";
		}
		String dn = u.path("displayName").asText("").trim();
		if (!dn.isEmpty()) {
			return dn;
		}
		return u.path("user").path("displayName").asText("").trim();
	}
}
