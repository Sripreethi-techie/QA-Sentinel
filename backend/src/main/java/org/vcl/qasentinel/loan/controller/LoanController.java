package org.vcl.qasentinel.loan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.vcl.qasentinel.loan.dto.LoanApplyRequest;
import org.vcl.qasentinel.loan.dto.LoanApplyResponse;
import org.vcl.qasentinel.loan.dto.LoanListResponse;
import org.vcl.qasentinel.loan.service.LoanService;

@RestController
@RequestMapping("/api/loan")
@RequiredArgsConstructor
public class LoanController {

	private final LoanService loanService;

	@PostMapping("/apply")
	public LoanApplyResponse apply(@RequestBody(required = false) LoanApplyRequest body) {
		return loanService.apply(body);
	}

	@GetMapping("/list")
	public LoanListResponse list() {
		return loanService.list();
	}
}
