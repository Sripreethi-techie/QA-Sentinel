package org.vcl.qasentinel.loan.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;

import org.vcl.qasentinel.loan.dto.LoanApplyRequest;
import org.vcl.qasentinel.loan.dto.LoanApplyResponse;
import org.vcl.qasentinel.loan.dto.LoanListResponse;
import org.vcl.qasentinel.loan.dto.LoanListResponse.LoanListRow;
import org.vcl.qasentinel.loan.model.LoanApplication;

/**
 * Demo loan store with intentional API bugs matching the former Express demo.
 */
@Service
public class LoanService {

	private final List<LoanApplication> loans = new CopyOnWriteArrayList<>();

	/**
	 * BUG #1: Always returns success even when fields are empty; still appends a row.
	 */
	public LoanApplyResponse apply(LoanApplyRequest body) {
		String name = body != null && body.name() != null ? body.name().trim() : "";
		String email = body != null && body.email() != null ? body.email().trim() : "";
		String loanAmount = body != null && body.loanAmount() != null ? body.loanAmount().trim() : "";
		String id = "loan-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xfffff));
		loans.add(new LoanApplication(id, name, email, loanAmount));
		return LoanApplyResponse.ok(id, "Application received");
	}

	/**
	 * BUG #2: Swapped name/email and garbled loanAmount (append {@code 00}).
	 */
	public LoanListResponse list() {
		List<LoanListRow> wrong = new ArrayList<>();
		for (LoanApplication l : loans) {
			String nm = l.email() == null || l.email().isBlank() ? "(unknown)" : l.email();
			String em = l.name() == null || l.name().isBlank() ? "(unknown)" : l.name();
			String amt = l.loanAmount() == null || l.loanAmount().isBlank() ? "0" : l.loanAmount() + "00";
			wrong.add(new LoanListRow(l.id(), nm, em, amt));
		}
		return new LoanListResponse(wrong);
	}

	/** Ground truth for AI agent context (not exposed on buggy list API). */
	public List<LoanApplication> allStored() {
		return List.copyOf(loans);
	}
}
