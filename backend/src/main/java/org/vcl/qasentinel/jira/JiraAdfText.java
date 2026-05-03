package org.vcl.qasentinel.jira;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Minimal Atlassian Document Format (ADF) to plain text for Groq prompts.
 */
final class JiraAdfText {

	private JiraAdfText() {
	}

	static String toPlainText(JsonNode adfRoot) {
		if (adfRoot == null || adfRoot.isNull() || !adfRoot.isObject()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		appendContent(sb, adfRoot.path("content"));
		return sb.toString().trim();
	}

	private static void appendContent(StringBuilder sb, JsonNode content) {
		if (!content.isArray()) {
			return;
		}
		for (JsonNode node : content) {
			String type = node.path("type").asText("");
			switch (type) {
				case "text" -> {
					String t = node.path("text").asText("");
					if (!t.isEmpty()) {
						if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
							sb.append(' ');
						}
						sb.append(t);
					}
				}
				case "hardBreak" -> sb.append('\n');
				case "paragraph", "heading", "blockquote", "listItem", "tableCell", "doc" ->
						appendContent(sb, node.path("content"));
				case "bulletList", "orderedList" -> {
					appendContent(sb, node.path("content"));
					sb.append('\n');
				}
				default -> appendContent(sb, node.path("content"));
			}
		}
	}
}
