package org.vcl.qasentinel.jira;

/**
 * Summary, description, and optional Acceptance Criteria (plain text) for LLM test planning.
 */
public record JiraIssuePrd(
		String issueKey,
		String summary,
		String descriptionPlain,
		/** Plain text from configured custom field(s); empty when not set or absent. */
		String acceptanceCriteriaPlain) {

	public String combinedPrdText() {
		String d = descriptionPlain == null || descriptionPlain.isBlank() ? "" : descriptionPlain.trim();
		String s = summary == null ? "" : summary.trim();
		String ac = acceptanceCriteriaPlain == null || acceptanceCriteriaPlain.isBlank() ? "" : acceptanceCriteriaPlain.trim();
		StringBuilder sb = new StringBuilder();
		if (!s.isEmpty()) {
			sb.append("Summary: ").append(s);
		}
		if (!d.isEmpty()) {
			if (!sb.isEmpty()) {
				sb.append("\n\n");
			}
			sb.append("Description:\n").append(d);
		}
		if (!ac.isEmpty()) {
			if (!sb.isEmpty()) {
				sb.append("\n\n");
			}
			sb.append("Acceptance Criteria:\n").append(ac);
		}
		return sb.toString();
	}
}
