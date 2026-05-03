package org.vcl.qasentinel.qa.model;

import java.util.List;

/** Aggregated outcome of {@code runQaFlowAllStoriesInProject}. */
public record QaBatchResult(String projectKey, List<QaBatchItem> items) {

	public QaBatchResult {
		if (projectKey == null) {
			projectKey = "";
		}
		if (items == null) {
			items = List.of();
		}
		else {
			items = List.copyOf(items);
		}
	}
}
