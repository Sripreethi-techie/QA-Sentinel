package org.vcl.qasentinel.qa.model;

import java.util.List;

/** Demo-friendly summary of {@link QaResult} for {@code POST /api/qa/run/{issueKey}}. */
public record QaRunResponse(String status, List<TestStep> stepsExecuted, boolean bugCreated) {

	public static QaRunResponse from(QaResult result) {
		boolean bug = result.jiraBugKey() != null && !result.jiraBugKey().isBlank();
		return new QaRunResponse(result.status(), result.stepsExecuted(), bug);
	}
}
