package org.vcl.qasentinel.loan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoanApplyResponse(boolean success, String id, String message) {

	public static LoanApplyResponse ok(String id, String message) {
		return new LoanApplyResponse(true, id, message);
	}
}
