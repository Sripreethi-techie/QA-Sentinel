package org.vcl.qasentinel.config;

import java.io.File;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import org.vcl.qasentinel.jira.JiraConfigurationException;
import org.vcl.qasentinel.logging.QaSentinelNarrative;

/**
 * One-time console story after the app is ready: what is wired for real vs demo fallback.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationStartupReporter implements ApplicationRunner {

	private final JiraProperties jiraProperties;
	private final GroqProperties groqProperties;
	private final QaFlowProperties qaFlowProperties;
	private final PlaywrightProperties playwrightProperties;

	@Override
	public void run(ApplicationArguments args) {
		QaSentinelNarrative.banner(log);
		QaSentinelNarrative.line(log, "Startup — integration check");

		QaSentinelNarrative.line(log, jiraLine());
		QaSentinelNarrative.line(log, groqLine());
		QaSentinelNarrative.line(log, playwrightLine());
		if (qaFlowProperties.isDemoLockedMode()) {
			QaSentinelNarrative.line(
					log,
					"Demo locked mode: ON — deterministic cached plan, optional fixed issue key, live Jira required for bugs (no synthetic keys)");
		}
	}

	private String jiraLine() {
		if (!jiraProperties.isEnabled() || !jiraProperties.isRealConnectionConfigured()) {
			return "Jira: not configured — " + JiraConfigurationException.MESSAGE;
		}
		return "Jira: Done — live API configured (bug type: "
				+ (jiraProperties.getBugIssueTypeName() == null || jiraProperties.getBugIssueTypeName().isBlank()
						? "Bug"
						: jiraProperties.getBugIssueTypeName().trim())
				+ ", link: "
				+ (jiraProperties.getBugLinkTypeName() == null || jiraProperties.getBugLinkTypeName().isBlank()
						? "Relates"
						: jiraProperties.getBugLinkTypeName().trim())
				+ ")";
	}

	private String groqLine() {
		if (!groqProperties.isConfigured()) {
			return "Groq: not configured — set groq.api-key for AI-derived test steps (zero-scripting)";
		}
		return "Groq: Done — API key set (model " + groqProperties.getModel() + ")";
	}

	private String playwrightLine() {
		File wd = new File(playwrightProperties.getWorkingDir()).getAbsoluteFile();
		if (!wd.isDirectory()) {
			return "Playwright: not ready — directory missing: " + wd.getAbsolutePath();
		}
		File pkg = new File(wd, "package.json");
		if (!pkg.isFile()) {
			return "Playwright: incomplete — no package.json under " + wd.getName() + " (run npm install in qa-runner)";
		}
		return "Playwright: Done — runner at " + wd.getName()
				+ " (auto npm install + Chromium on first run if node_modules missing)";
	}
}
