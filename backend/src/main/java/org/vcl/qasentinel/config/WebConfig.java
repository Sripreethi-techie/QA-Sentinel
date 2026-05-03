package org.vcl.qasentinel.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final AppCorsProperties corsProperties;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		String[] origins = corsProperties.getAllowedOrigins().split("\\s*,\\s*");
		registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
	}
}
