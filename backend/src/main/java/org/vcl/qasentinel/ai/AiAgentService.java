package org.vcl.qasentinel.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.vcl.qasentinel.ai.model.AiAskResponse;
import org.vcl.qasentinel.ai.model.QaRunSnapshot;
import org.vcl.qasentinel.config.AiAgentProperties;
import org.vcl.qasentinel.jira.JiraConfigurationException;
import org.vcl.qasentinel.jira.JiraIssueView;
import org.vcl.qasentinel.jira.JiraSearchClient;
import org.vcl.qasentinel.loan.model.LoanApplication;
import org.vcl.qasentinel.loan.service.LoanService;
import org.vcl.qasentinel.qa.service.GroqReasoningService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAgentService {

	private static final String SYSTEM_PROMPT =
			"You are an AI QA assistant for Autonomous QA Sentinel. "
					+ "Answer using ONLY the facts in the user message sections (Jira Issues, QA Results, Bugs, Loan data). "
					+ "If something is not in the data, say you do not have that information. "
					+ "Be concise, professional, and actionable.";

	private final JiraSearchClient jiraSearchClient;
	private final QaRunHistoryStore runHistory;
	private final GroqReasoningService groqReasoningService;
	private final AiAgentProperties aiAgentProperties;
	private final LoanService loanService;

	public AiAskResponse ask(String question) {
		String q = question == null ? "" : question.trim();
		List<QaRunSnapshot> snapshots = runHistory.recent(aiAgentProperties.getHistoryLimit());
		Set<String> projects = new LinkedHashSet<>(aiAgentProperties.contextProjectKeys());
		for (QaRunSnapshot s : snapshots) {
			projects.add(deriveProjectFromIssue(s.issueKey()));
		}

		Map<String, JiraIssueView> issuesByKey = new LinkedHashMap<>();
		try {
			for (String pk : projects) {
				if (pk == null || pk.isBlank() || "UNKNOWN".equals(pk)) {
					continue;
				}
				for (JiraIssueView v : jiraSearchClient.searchByProject(pk)) {
					if (v.key() != null && !issuesByKey.containsKey(v.key())) {
						issuesByKey.put(v.key(), v);
					}
				}
			}
		}
		catch (JiraConfigurationException e) {
			return new AiAskResponse(e.getMessage(), false);
		}
		catch (IllegalStateException e) {
			log.warn("AI agent: Jira search failed: {}", e.getMessage());
			return new AiAskResponse("Jira data unavailable: " + e.getMessage(), false);
		}

		String issuesBlock = formatIssues(issuesByKey.values().stream().toList());
		String resultsBlock = formatRuns(snapshots);
		String bugsBlock = formatBugs(snapshots);
		String loansBlock = formatLoans(loanService.allStored());

		int cap = Math.max(1000, aiAgentProperties.getMaxContextChars());
		issuesBlock = truncate("issues", issuesBlock, cap);
		resultsBlock = truncate("results", resultsBlock, cap);
		bugsBlock = truncate("bugs", bugsBlock, cap);
		loansBlock = truncate("loans", loansBlock, cap);

		String userBlock =
				"You are an AI QA assistant.\nAnswer based on the following system data:\n\nJira Issues:\n"
						+ issuesBlock
						+ "\n\nQA Results:\n"
						+ resultsBlock
						+ "\n\nBugs:\n"
						+ bugsBlock
						+ "\n\nLoan data (in-memory store, ground truth):\n"
						+ loansBlock
						+ "\n\nUser Question:\n"
						+ q;

		String fromLlm = groqReasoningService.completeChat(SYSTEM_PROMPT, userBlock);
		if (fromLlm != null && !fromLlm.isBlank()) {
			return new AiAskResponse(fromLlm.trim(), true);
		}

		String fallback = buildOfflineAnswer(q, snapshots, issuesByKey.size());
		return new AiAskResponse(fallback, false);
	}

	private static String truncate(String label, String text, int maxChars) {
		if (text == null || text.length() <= maxChars) {
			return text == null ? "" : text;
		}
		return text.substring(0, maxChars)
				+ "\n… (truncated "
				+ label
				+ " for token limits; "
				+ (text.length() - maxChars)
				+ " chars omitted)";
	}

	private static String formatIssues(List<JiraIssueView> issues) {
		if (issues.isEmpty()) {
			return "(no Jira issues in context — project may have no results)";
		}
		return issues.stream()
				.map(
						v -> "- "
								+ v.key()
								+ " | "
								+ (v.summary() == null ? "" : v.summary())
								+ " | status="
								+ (v.statusName() == null ? "" : v.statusName())
								+ " | assignee="
								+ (v.assigneeName() == null ? "" : v.assigneeName()))
				.collect(Collectors.joining("\n"));
	}

	private static String formatRuns(List<QaRunSnapshot> snapshots) {
		if (snapshots.isEmpty()) {
			return "(none — no QA runs recorded in this JVM yet; trigger POST /api/v1/qa/flow/{issueKey})";
		}
		List<String> lines = new ArrayList<>();
		for (QaRunSnapshot s : snapshots) {
			StringBuilder sb = new StringBuilder();
			sb.append("- issue=").append(s.issueKey());
			sb.append(" | status=").append(s.status());
			sb.append(" | at=").append(s.at().toString());
			sb.append(" | trace=").append(s.traceId());
			sb.append(" | steps=").append(s.stepCount());
			if (!s.failureReason().isEmpty()) {
				sb.append(" | failure=").append(s.failureReason().replace('\n', ' ').trim());
			}
			if (!s.failedStepSummary().isEmpty()) {
				sb.append(" | failedStep=").append(s.failedStepSummary());
			}
			if (!s.jiraBugKey().isEmpty()) {
				sb.append(" | jiraBug=").append(s.jiraBugKey());
			}
			if (!s.screenshotPath().isEmpty()) {
				sb.append(" | screenshot=").append(s.screenshotPath());
			}
			lines.add(sb.toString());
		}
		return String.join("\n", lines);
	}

	private static String formatLoans(List<LoanApplication> loans) {
		if (loans.isEmpty()) {
			return "(none — no loan applications submitted yet)";
		}
		return loans.stream()
				.map(
						l -> "- id="
								+ l.id()
								+ " | name="
								+ (l.name() == null ? "" : l.name())
								+ " | email="
								+ (l.email() == null ? "" : l.email())
								+ " | loanAmount="
								+ (l.loanAmount() == null ? "" : l.loanAmount()))
				.collect(Collectors.joining("\n"));
	}

	private static String formatBugs(List<QaRunSnapshot> snapshots) {
		List<String> lines = new ArrayList<>();
		for (QaRunSnapshot s : snapshots) {
			if (s.jiraBugKey() == null || s.jiraBugKey().isBlank()) {
				continue;
			}
			lines.add(
					"- bugKey="
							+ s.jiraBugKey()
							+ " | linkedIssue="
							+ s.issueKey()
							+ " | runStatus="
							+ s.status()
							+ " | at="
							+ s.at());
		}
		if (lines.isEmpty()) {
			return "(none — no bug keys recorded on recent runs)";
		}
		return String.join("\n", lines);
	}

	private static String deriveProjectFromIssue(String issueKey) {
		if (issueKey == null || issueKey.isBlank()) {
			return "UNKNOWN";
		}
		String k = issueKey.trim().toUpperCase(Locale.ROOT);
		int dash = k.lastIndexOf('-');
		if (dash <= 0 || dash >= k.length() - 1) {
			return k;
		}
		return k.substring(0, dash);
	}

	private static String buildOfflineAnswer(
			String question, List<QaRunSnapshot> snapshots, int issueCount) {
		String ql = question.toLowerCase(Locale.ROOT);
		QaRunSnapshot last = snapshots.isEmpty() ? null : snapshots.getFirst();
		StringBuilder sb = new StringBuilder();
		sb.append("Groq returned no answer (API key missing, error, or empty completion). ");
		sb.append("Here is a quick local summary from recorded runs in this server:\n\n");
		if (last == null) {
			sb.append("- No QA runs are stored yet in this JVM session.\n");
		}
		else {
			sb.append("- Last run: issue ")
					.append(last.issueKey())
					.append(", status ")
					.append(last.status())
					.append(".\n");
			if (("FAIL".equalsIgnoreCase(last.status()) || "ERROR".equalsIgnoreCase(last.status()))
					&& (ql.contains("fail") || ql.contains("why") || ql.contains("error"))) {
				if (!last.failureReason().isEmpty()) {
					sb.append("- Failure detail: ").append(last.failureReason()).append("\n");
				}
				if (!last.failedStepSummary().isEmpty()) {
					sb.append("- Failed step: ").append(last.failedStepSummary()).append("\n");
				}
			}
			if (!last.jiraBugKey().isEmpty()) {
				sb.append("- Jira bug key from that run: ").append(last.jiraBugKey()).append("\n");
			}
		}
		sb.append("- Jira issues loaded into context: ").append(issueCount).append(" keys.\n");
		sb.append("\nSet `groq.api-key` (and optional `groq.model`) for full LLM answers.");
		return sb.toString();
	}
}
