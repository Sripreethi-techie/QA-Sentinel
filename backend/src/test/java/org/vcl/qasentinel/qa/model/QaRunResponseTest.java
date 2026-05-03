package org.vcl.qasentinel.qa.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class QaRunResponseTest {

	@Test
	void fromMapsBugCreatedWhenJiraKeyPresent() {
		QaRunResponse r = QaRunResponse.from(
				new QaResult("FAIL", List.of(new TestStep(1, "navigate", "x")), "boom", "", "t1", "PROJ-99"));
		assertThat(r.status()).isEqualTo("FAIL");
		assertThat(r.stepsExecuted()).hasSize(1);
		assertThat(r.bugCreated()).isTrue();
	}

	@Test
	void fromMapsBugCreatedFalseWhenEmptyKey() {
		QaRunResponse r = QaRunResponse.from(new QaResult("PASS", List.of(), "", "", "t1", ""));
		assertThat(r.bugCreated()).isFalse();
	}
}
