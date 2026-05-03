package org.vcl.qasentinel.loan.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Public list shape (intentionally wrong rows vs stored {@link org.vcl.qasentinel.loan.model.LoanApplication}).
 */
public record LoanListResponse(@JsonProperty("applications") List<LoanListRow> applications) {

	public record LoanListRow(String id, String name, String email, String loanAmount) {
	}
}
