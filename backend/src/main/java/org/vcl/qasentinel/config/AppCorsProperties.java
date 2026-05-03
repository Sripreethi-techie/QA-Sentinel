package org.vcl.qasentinel.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppCorsProperties {

	private String allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";
}
