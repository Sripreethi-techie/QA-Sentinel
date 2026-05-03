package org.vcl.qasentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.vcl.qasentinel.config.AiAgentProperties;
import org.vcl.qasentinel.config.AppCorsProperties;
import org.vcl.qasentinel.config.GroqProperties;
import org.vcl.qasentinel.config.JiraProperties;
import org.vcl.qasentinel.config.PlaywrightProperties;
import org.vcl.qasentinel.config.QaFlowProperties;

@SpringBootApplication
@EnableConfigurationProperties({
		AiAgentProperties.class,
		JiraProperties.class,
		AppCorsProperties.class,
		GroqProperties.class,
		PlaywrightProperties.class,
		QaFlowProperties.class,
})
public class QaSentinelApplication {

	public static void main(String[] args) {
		SpringApplication.run(QaSentinelApplication.class, args);
	}
}
