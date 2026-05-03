package org.vcl.qasentinel.qa.service;

/**
 * Thrown when Groq is not configured or step planning cannot produce steps from model output.
 */
public class AiReasoningUnavailableException extends RuntimeException {

	public static final String MESSAGE = "AI reasoning unavailable";

	public AiReasoningUnavailableException() {
		super(MESSAGE);
	}

	public AiReasoningUnavailableException(String detail) {
		super(detail == null || detail.isBlank() ? MESSAGE : MESSAGE + ": " + detail);
	}

	public AiReasoningUnavailableException(Throwable cause) {
		super(MESSAGE, cause);
	}
}
