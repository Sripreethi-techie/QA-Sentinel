package org.vcl.qasentinel.qa.service;

import java.io.File;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import org.vcl.qasentinel.config.GroqProperties;
import org.vcl.qasentinel.config.JiraProperties;
import org.vcl.qasentinel.config.PlaywrightProperties;
import org.vcl.qasentinel.qa.diagnostics.StoryFetchDiagnosticsHolder;
import org.vcl.qasentinel.qa.model.IntegrationHealthResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationHealthService {

	private final JiraProperties jiraProperties;
	private final GroqProperties groqProperties;
	private final PlaywrightProperties playwrightProperties;
	private final StoryFetchDiagnosticsHolder storyFetchDiagnosticsHolder;

	public IntegrationHealthResponse snapshot() {
		boolean jiraConfigured = jiraProperties.isEnabled() && jiraProperties.isRealConnectionConfigured();
		boolean jiraReachable = false;
		StringBuilder detail = new StringBuilder();
		if (jiraConfigured) {
			try {
				String base = jiraProperties.getApiBaseUrl().trim().replaceAll("/+$", "");
				RestClient client = RestClient.builder()
						.baseUrl(base)
						.defaultHeader(HttpHeaders.ACCEPT, "application/json")
						.build();
				client.get()
						.uri("/rest/api/3/myself")
						.headers(h -> h.setBasicAuth(jiraProperties.getEmail(), jiraProperties.getApiToken()))
						.retrieve()
						.toBodilessEntity();
				jiraReachable = true;
			}
			catch (RestClientException ex) {
				detail.append("Jira: ").append(ex.getMessage()).append(". ");
				log.debug("Jira health ping failed: {}", ex.getMessage());
			}
		}

		boolean groqConfigured = groqProperties.isConfigured();
		boolean playwrightReady = isPlaywrightRunnerReady(detail);

		return new IntegrationHealthResponse(
				jiraConfigured,
				jiraReachable,
				groqConfigured,
				playwrightReady,
				storyFetchDiagnosticsHolder.getLastStoryFetchError(),
				detail.toString().trim());
	}

	private boolean isPlaywrightRunnerReady(StringBuilder detail) {
		File wd = new File(playwrightProperties.getWorkingDir()).getAbsoluteFile();
		if (!wd.isDirectory()) {
			detail.append("Playwright: missing directory ").append(wd.getAbsolutePath()).append(". ");
			return false;
		}
		File pkg = new File(wd, "package.json");
		if (!pkg.isFile()) {
			detail.append("Playwright: no package.json under ").append(wd.getName()).append(". ");
			return false;
		}
		return true;
	}
}
