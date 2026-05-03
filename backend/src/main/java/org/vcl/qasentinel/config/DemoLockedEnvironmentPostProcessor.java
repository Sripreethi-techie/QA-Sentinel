package org.vcl.qasentinel.config;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Maps {@code DEMO_LOCKED_MODE=true} to {@code qa.flow.demo-locked-mode=true} so operators can use the
 * env var name from the demo runbook without Spring's {@code QA_FLOW_*} prefix.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DemoLockedEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final String SOURCE_NAME = "demoLockedModeAlias";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		String raw = environment.getProperty("DEMO_LOCKED_MODE");
		if (raw == null || raw.isBlank()) {
			return;
		}
		String t = raw.trim();
		boolean on = "true".equalsIgnoreCase(t) || "1".equals(t) || "yes".equalsIgnoreCase(t);
		if (!on) {
			return;
		}
		if (environment.getPropertySources().contains(SOURCE_NAME)) {
			return;
		}
		environment.getPropertySources()
				.addFirst(new MapPropertySource(SOURCE_NAME, Map.of("qa.flow.demo-locked-mode", "true")));
	}
}
