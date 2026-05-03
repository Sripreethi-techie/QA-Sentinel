package org.vcl.qasentinel.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qa.playwright")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaywrightProperties {

	/** Directory containing package.json and playwright.config.js (e.g. qa-runner). */
	private String workingDir = "qa-runner";

	/**
	 * Extra args after {@code npx playwright test}. Default spec path is appended by
	 * {@link org.vcl.qasentinel.qa.service.PlaywrightService}.
	 */
	private String testArgs = "tests/dynamic-test.spec.js";

	/** Max seconds to wait for the Playwright process. */
	private int timeoutSeconds = 300;

	/**
	 * How steps are passed to the Node runner: {@code env} sets {@code QA_STEPS_JSON}; {@code file}
	 * writes JSON under the working dir and sets {@code QA_STEPS_FILE} (better for large payloads on
	 * Windows).
	 */
	private String stepsDelivery = "env";

	/**
	 * Base URL for the runner to POST results (e.g. {@code http://localhost:9096}). Appends
	 * {@code /qa-report}. Leave blank to skip the callback from Java (runner still respects
	 * {@code QA_REPORT_URL} if set externally).
	 */
	private String reportBaseUrl = "http://localhost:9096";
}
