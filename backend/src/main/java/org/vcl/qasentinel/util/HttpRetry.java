package org.vcl.qasentinel.util;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Small backoff helper for Jira / Groq HTTP clients (429, 5xx, timeouts).
 */
public final class HttpRetry {

	private HttpRetry() {}

	public static boolean isRetryable(Throwable t) {
		if (t == null) {
			return false;
		}
		if (t instanceof RestClientResponseException r) {
			int s = r.getStatusCode().value();
			return s == 429 || s == 502 || s == 503 || s == 504;
		}
		if (t instanceof ResourceAccessException) {
			return true;
		}
		return isRetryable(t.getCause());
	}

	public static void sleepBackoffMs(int attemptZeroBased) {
		long ms = 300L * (1L << Math.min(attemptZeroBased, 4));
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}
}
