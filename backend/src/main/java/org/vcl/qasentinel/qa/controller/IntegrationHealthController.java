package org.vcl.qasentinel.qa.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.vcl.qasentinel.qa.model.IntegrationHealthResponse;
import org.vcl.qasentinel.qa.service.IntegrationHealthService;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class IntegrationHealthController {

	private final IntegrationHealthService integrationHealthService;

	@GetMapping("/integrations")
	public IntegrationHealthResponse integrations() {
		return integrationHealthService.snapshot();
	}
}
