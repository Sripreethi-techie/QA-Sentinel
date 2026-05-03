package org.vcl.qasentinel.logging;

import org.slf4j.Logger;

/**
 * Human-readable, demo-friendly log lines. Always uses {@code INFO} so they appear on the default
 * console appender.
 */
public final class QaSentinelNarrative {

	public static final String BANNER = "[QA SENTINEL]";

	private QaSentinelNarrative() {
	}

	public static void banner(Logger log) {
		log.info(BANNER);
	}

	public static void line(Logger log, String message) {
		log.info("{}", message);
	}

	public static void agentAnalyzingRequirement(Logger log) {
		line(log, "Agent analyzing requirement...");
	}

	public static void agentGeneratingStepsViaAi(Logger log) {
		line(log, "Generating steps via AI...");
	}

	public static void agentExecutingStep(Logger log, int stepNumber, int totalSteps) {
		line(log, "Executing step " + stepNumber + " of " + totalSteps + "...");
	}

	public static void agentFailureCreatingBug(Logger log) {
		line(log, "Failure detected → creating bug");
	}
}
