package org.vcl.qasentinel.jira;

import java.time.Instant;

public record JiraIssueView(
		String key,
		String summary,
		String statusName,
		String assigneeName,
		Instant updated,
		/** Plain-text PRD snippet from classpath mock only (empty for live Jira list). */
		String descriptionPlain,
		/** Optional mock-only acceptance criteria plain text (classpath fixtures). */
		String acceptanceCriteriaPlain) {
}
