package org.vcl.qasentinel.qa.model;

/**
 * One story's outcome within {@link QaBatchResult}.
 */
public record QaBatchItem(String issueKey, String status, String jiraBugKey, String message) {

	public QaBatchItem {
		if (issueKey == null) {
			issueKey = "";
		}
		if (status == null) {
			status = "";
		}
		if (jiraBugKey == null) {
			jiraBugKey = "";
		}
		if (message == null) {
			message = "";
		}
	}
}
