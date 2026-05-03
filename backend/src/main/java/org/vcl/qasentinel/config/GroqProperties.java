package org.vcl.qasentinel.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "groq")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroqProperties {

	/** Groq API key; leave blank to disable LLM step planning (zero-scripting requires a key). */
	private String apiKey = "";

	private String model = "llama-3.3-70b-versatile";

	/** OpenAI-compatible base URL (no trailing slash). */
	private String baseUrl = "https://api.groq.com/openai/v1";

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}
}
