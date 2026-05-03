package org.vcl.qasentinel.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qa.agent")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAgentProperties {

	/** Comma-separated Jira project keys used to load recent issues into the agent context. */
	private String contextProjects = "DEMO";

	/** Max QA runs to include in the agent context. */
	private int historyLimit = 25;

	/** Max characters per context section (issues / results / bugs) to stay within model limits. */
	private int maxContextChars = 8000;

	public List<String> contextProjectKeys() {
		return Arrays.stream(contextProjects.split("\\s*,\\s*"))
				.map(s -> s.trim().toUpperCase(Locale.ROOT))
				.filter(s -> !s.isEmpty())
				.distinct()
				.collect(Collectors.toList());
	}
}
