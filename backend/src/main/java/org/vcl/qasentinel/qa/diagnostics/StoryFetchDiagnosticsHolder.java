package org.vcl.qasentinel.qa.diagnostics;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * Last dashboard / Jira story-list outcome for integration health UI.
 */
@Component
public class StoryFetchDiagnosticsHolder {

	private final AtomicReference<String> lastStoryFetchError = new AtomicReference<>("");

	public void recordSuccess() {
		lastStoryFetchError.set("");
	}

	public void recordError(String message) {
		String m = message == null ? "" : message.trim();
		lastStoryFetchError.set(m.isEmpty() ? "(unknown error)" : m);
	}

	public String getLastStoryFetchError() {
		String s = lastStoryFetchError.get();
		return s == null ? "" : s;
	}
}
