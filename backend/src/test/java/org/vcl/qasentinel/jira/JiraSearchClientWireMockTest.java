package org.vcl.qasentinel.jira;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import org.vcl.qasentinel.config.JiraProperties;

class JiraSearchClientWireMockTest {

	@RegisterExtension
	static final WireMockExtension wm =
			WireMockExtension.newInstance().options(WireMockConfiguration.wireMockConfig().dynamicPort()).build();

	@Test
	void searchByJqlUsesSearchJqlEndpoint() throws Exception {
		String base = wm.getRuntimeInfo().getHttpBaseUrl();
		wm.stubFor(
				get(urlPathEqualTo("/rest/api/3/search/jql"))
						.withQueryParam("jql", equalTo("project = DEMO ORDER BY updated DESC"))
						.willReturn(
								aResponse()
										.withStatus(200)
										.withHeader("Content-Type", "application/json")
										.withBody(
												"""
												{"issues":[{"key":"DEMO-1","fields":{"summary":"Hello","status":{"name":"To Do"},"updated":"2024-01-15T12:00:00.000+0000"}}]}
												""")));

		JiraProperties props = new JiraProperties();
		props.setEnabled(true);
		props.setBaseUrl(base);
		props.setEmail("user@example.com");
		props.setApiToken("token");
		props.setMaxResults(25);

		ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
		JiraSearchClient client = new JiraSearchClient(props, mapper);

		List<JiraIssueView> rows = client.searchByJql("project = DEMO ORDER BY updated DESC", 5);
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).key()).isEqualTo("DEMO-1");
		assertThat(rows.get(0).summary()).isEqualTo("Hello");
	}
}
