package org.vcl.qasentinel.loan.model;

/**
 * In-memory loan row (truthful storage before intentional list API bugs).
 */
public record LoanApplication(String id, String name, String email, String loanAmount) {
}
