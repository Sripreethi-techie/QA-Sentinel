package org.vcl.qasentinel.config;

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

	public boolean isRealConnectionConfigured() {
		return baseUrl != null
				&& !baseUrl.isBlank()
				&& email != null
				&& !email.isBlank()
				&& apiToken != null
				&& !apiToken.isBlank();
	}
}
