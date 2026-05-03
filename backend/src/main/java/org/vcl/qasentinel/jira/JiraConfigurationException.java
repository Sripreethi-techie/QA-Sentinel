package org.vcl.qasentinel.jira;

/**
 * Raised when Jira base URL, credentials, or enabled flag are not set for operations that require live Jira.
 */
public final class JiraConfigurationException extends RuntimeException {

	public static final String MESSAGE = "Jira must be configured for QA Sentinel";

	public JiraConfigurationException() {
		super(MESSAGE);
	}
}
