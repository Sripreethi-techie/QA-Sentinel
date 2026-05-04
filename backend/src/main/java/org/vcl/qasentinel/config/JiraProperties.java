package org.vcl.qasentinel.config;

import java.net.URI;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jira")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JiraProperties {

	private boolean enabled = true;
	private String baseUrl = "";
	private String email = "";
	private String apiToken = "";
	private int maxResults = 25;
	private String connectTimeout = "10s";
	private String readTimeout = "30s";

	/**
	 * When true, sets Jira {@code parent} on the new bug to the user story key so the bug appears nested under that
	 * story in backlogs that support hierarchy (common on team-managed / Next-gen). If issue create fails with a
	 * parent validation error, set false and rely on {@link #bugLinkTypeName} only, or use issue type {@code Sub-task}
	 * with {@link #bugIssueTypeName} if your template requires it.
	 */
	private boolean bugParentStoryEnabled = false;

	/** Issue type name for automated QA bugs (must exist in the Jira project). */
	private String bugIssueTypeName = "Bug";

	/**
	 * Issue link type when linking the new bug to the original story (e.g. {@code Relates}, {@code Blocks}).
	 * Must exist in your Jira instance.
	 */
	private String bugLinkTypeName = "Relates";

	/**
	 * Comma-separated Jira custom field id(s) for Acceptance Criteria (e.g. {@code customfield_10046}).
	 * Values are requested on issue fetch and passed to Groq. Leave blank if AC lives only in the description.
	 */
	private String acceptanceCriteriaFieldIds = "";

	/**
	 * Optional: assign every QA-filed bug to this Jira Cloud {@code accountId} (from user profile / API).
	 * Overrides display-name search when set.
	 */
	private String defaultBugAssigneeAccountId = "";

	/**
	 * Optional: assignee display name for QA-filed bugs; resolved via {@code /rest/api/3/user/search} when
	 * {@link #defaultBugAssigneeAccountId} is blank (e.g. {@code Sripreethi Suresh}).
	 */
	private String defaultBugAssigneeDisplayName = "";

	/**
	 * Jira Cloud REST base: {@code https://&lt;site&gt;.atlassian.net} only. If {@link #baseUrl} is a board/backlog
	 * browser URL (path + query after the host), those are stripped so {@code /rest/api/3/...} requests return JSON
	 * instead of HTML.
	 */
	public String getApiBaseUrl() {
		if (baseUrl == null || baseUrl.isBlank()) {
			return "";
		}
		String u = baseUrl.trim();
		try {
			URI uri = URI.create(u);
			String scheme = uri.getScheme();
			String auth = uri.getRawAuthority();
			if (scheme != null && !scheme.isBlank() && auth != null && !auth.isBlank()) {
				return scheme + "://" + auth;
			}
		}
		catch (IllegalArgumentException ignored) {
			// fall through
		}
		return u.replaceAll("/+$", "");
	}

	public boolean isRealConnectionConfigured() {
		return baseUrl != null
				&& !baseUrl.isBlank()
				&& email != null
				&& !email.isBlank()
				&& apiToken != null
				&& !apiToken.isBlank();
	}
}
