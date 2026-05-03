package org.vcl.qasentinel.loan.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoanApplyRequest(String name, String email, String loanAmount) {
}
