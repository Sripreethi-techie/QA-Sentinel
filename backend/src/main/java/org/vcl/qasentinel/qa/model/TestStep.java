package org.vcl.qasentinel.qa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Test step from Groq (structured JSON or legacy numbered list) for Playwright.
 * Actions: {@code navigate}, {@code click}, {@code type}, {@code verify} (FE) and
 * {@code api_get}, {@code api_post}, {@code api_put}, {@code api_patch}, {@code api_delete} (BE HTTP via runner).
 * <ul>
 *   <li>{@code target}: human label, selector hint, or page name (click/type/verify); URL for navigate when
 *       {@code value} is empty.</li>
 *   <li>{@code value}: URL for navigate when applicable; text to type for {@code type}.</li>
 *   <li>{@code expected}: intended outcome for documentation and verify assertions (Playwright prefers this over
 *       parsing {@code description}).</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TestStep(
		int stepNumber,
		String action,
		String description,
		String target,
		String value,
		String expected) {

	/** Legacy steps without structured fields. */
	public TestStep(int stepNumber, String action, String description) {
		this(stepNumber, action, description, null, null, null);
	}

	public TestStep(int stepNumber, String action, String description, String target, String value) {
		this(stepNumber, action, description, target, value, null);
	}
}
