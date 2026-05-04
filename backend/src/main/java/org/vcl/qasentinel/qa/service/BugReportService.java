package org.vcl.qasentinel.qa.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.vcl.qasentinel.ai.QaRunHistoryStore;
import org.vcl.qasentinel.ai.model.QaRunSnapshot;
import org.vcl.qasentinel.config.JiraProperties;
import org.vcl.qasentinel.config.PlaywrightProperties;
import org.vcl.qasentinel.qa.model.BugReportListItem;

@Service
@RequiredArgsConstructor
public class BugReportService {

	private final QaRunHistoryStore runHistory;
	private final JiraProperties jiraProperties;
	private final PlaywrightProperties playwrightProperties;

	public List<BugReportListItem> listBugReports() {
		Set<String> seenKeys = new HashSet<>();
		List<BugReportListItem> out = new ArrayList<>();
		for (QaRunSnapshot s : runHistory.recent(100)) {
			if (s.jiraBugKey() == null || s.jiraBugKey().isBlank()) {
				continue;
			}
			String k = s.jiraBugKey().trim();
			if (!seenKeys.add(k)) {
				continue;
			}
			out.add(toItem(s));
		}
		return out;
	}

	public Optional<Path> resolveScreenshotFile(String jiraBugKey) {
		if (jiraBugKey == null || jiraBugKey.isBlank()) {
			return Optional.empty();
		}
		Optional<QaRunSnapshot> snap = runHistory.findLatestWithJiraBugKey(jiraBugKey.trim());
		if (snap.isEmpty()) {
			return Optional.empty();
		}
		String rel = snap.get().screenshotPath();
		if (rel == null || rel.isBlank()) {
			return Optional.empty();
		}
		Path root = Path.of(playwrightProperties.getWorkingDir()).toAbsolutePath().normalize();
		return PlaywrightArtifactPathResolver.resolveExisting(root, rel);
	}

	private BugReportListItem toItem(QaRunSnapshot s) {
		String key = s.jiraBugKey().trim();
		String encoded = URLEncoder.encode(key, StandardCharsets.UTF_8);
		String shotPath = s.screenshotPath() == null ? "" : s.screenshotPath().trim();
		Path root = Path.of(playwrightProperties.getWorkingDir()).toAbsolutePath().normalize();
		String shotUrl = shotPath.isEmpty() || PlaywrightArtifactPathResolver.resolveExisting(root, shotPath).isEmpty()
				? null
				: "/api/v1/bugs/" + encoded + "/screenshot";
		String reason = s.failureReason() == null ? "" : s.failureReason();
		String title = buildTitle(s.issueKey(), key, reason);
		return new BugReportListItem(
				"bug-" + key,
				title,
				key,
				buildJiraBrowseUrl(key),
				s.issueKey() == null ? "" : s.issueKey(),
				s.status() == null ? "" : s.status(),
				s.traceId() == null ? "" : s.traceId(),
				reason,
				shotPath,
				shotUrl,
				s.failedStepSummary() == null ? "" : s.failedStepSummary(),
				s.failedStep(),
				s.at() == null ? "" : s.at().toString());
	}

	private static String buildTitle(String issueKey, String jiraKey, String failureReason) {
		String iss = issueKey == null || issueKey.isBlank() ? "unknown issue" : issueKey;
		if (failureReason != null && !failureReason.isBlank()) {
			String one = failureReason.replace('\r', ' ').replace('\n', ' ').trim();
			String shortMsg = one.length() > 90 ? one.substring(0, 87) + "…" : one;
			return "QA failure — " + iss + " · " + shortMsg;
		}
		return "QA failure — " + iss + " · " + jiraKey;
	}

	private String buildJiraBrowseUrl(String jiraIssueKey) {
		String base = jiraProperties.getApiBaseUrl();
		if (base == null || base.isBlank()) {
			return "https://www.atlassian.com/software/jira?utm=demo&issue=" + urlSafeKey(jiraIssueKey);
		}
		String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		return trimmed + "/browse/" + jiraIssueKey;
	}

	private static String urlSafeKey(String jiraIssueKey) {
		return URLEncoder.encode(jiraIssueKey, StandardCharsets.UTF_8);
	}
}
