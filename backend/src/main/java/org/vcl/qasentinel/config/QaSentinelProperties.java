package org.vcl.qasentinel.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local persistence, bug idempotency, and optional API key for {@code /api/**}.
 */
@ConfigurationProperties(prefix = "qa.sentinel")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QaSentinelProperties {

	/** When false, run history stays in-memory only (no disk I/O). */
	private boolean persistenceEnabled = true;

	/** When false, every failure may create a new Jira bug (no dedupe by failure fingerprint). */
	private boolean bugIdempotencyEnabled = true;

	/**
	 * Append-only JSONL of {@link org.vcl.qasentinel.ai.model.QaRunSnapshot} per line.
	 * Blank = {@code ${user.home}/.qa-sentinel/run-history.jsonl}.
	 */
	private String runHistoryFile = "";

	/**
	 * JSON file mapping idempotency keys to Jira issue keys.
	 * Blank = {@code ${user.home}/.qa-sentinel/bug-idempotency.json}.
	 */
	private String bugIdempotencyFile = "";

	/**
	 * When set, {@code /api/**} requires header {@link QaSentinelApiKeyAuthFilter#HEADER_NAME} (except
	 * {@code /api/v1/health/**}).
	 */
	private String apiKey = "";
}
