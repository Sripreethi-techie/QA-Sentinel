package org.vcl.qasentinel.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Defaults for {@code runQaFlow(issueKey)} when no env URL is supplied in the request body. */
@ConfigurationProperties(prefix = "qa.flow")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QaFlowProperties {

	/** Public Heroku login demo — stable, no auth, suitable for zero-setup E2E. */
	public static final String DEFAULT_DEMO_TARGET_URL = "https://the-internet.herokuapp.com/login";

	/** Base URL of the deployment under test (e.g. QA site). */
	private String defaultEnvBaseUrl = DEFAULT_DEMO_TARGET_URL;

	/**
	 * When true (e.g. env {@code QA_FLOW_DEMO_LOCKED_MODE=true}): deterministic demo — optional fixed Jira story key,
	 * classpath cached Groq-shaped steps for Playwright (live Jira required for all flows).
	 */
	private boolean demoLockedMode = false;

	/**
	 * When {@link #demoLockedMode} is true and this is non-blank, all QA entry points use this issue key instead of
	 * the requested key (must exist in your Jira project).
	 */
	private String demoLockedIssueKey = "";

	/** Classpath resource (no {@code classpath:} prefix), JSON array of test steps (same schema as Groq output). */
	private String demoLockedPlanResource = "demo-locked/demo-plan.json";

	/**
	 * Jira issue type for story lists and batch QA — must match Jira exactly. Classic Scrum templates often use
	 * {@code Story}; some boards use {@code User Story}. Misconfiguration is mitigated by a project-only JQL fallback.
	 */
	private String batchStoryIssueTypeName = "Story";

	/** Max stories per batch run (capped by {@code jira.max-results}). */
	private int batchMaxStories = 20;

	/** Extra JQL fragment appended after issuetype clause (e.g. {@code AND statusCategory != Done}). */
	private String batchJqlSuffix = "";

	/**
	 * Max concurrent {@link #runQaFlow(String)} executions in batch mode. Values greater than 1 ignore
	 * {@link #batchDelayMsBetweenStories} (delay is only applied when concurrency is 1).
	 */
	private int batchConcurrency = 1;

	/** Milliseconds to wait before each story when {@link #batchConcurrency} is 1 (rate limiting). */
	private int batchDelayMsBetweenStories = 0;

	/** When true, batch lists stories but does not run Playwright or file bugs. */
	private boolean batchDryRun = false;

	/**
	 * When false, target env URLs that resolve to private RFC1918 hosts are rejected (localhost and 127.0.0.1 still
	 * allowed for local dev).
	 */
	private boolean allowPrivateEnvUrls = true;
}
