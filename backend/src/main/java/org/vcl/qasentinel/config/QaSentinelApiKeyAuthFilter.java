package org.vcl.qasentinel.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * When {@code qa.sentinel.api-key} is non-blank, requires matching {@code X-QA-Sentinel-Api-Key} on {@code /api/**}
 * (except integration health used for probes).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class QaSentinelApiKeyAuthFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-QA-Sentinel-Api-Key";

	private final QaSentinelProperties sentinelProperties;

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain)
			throws ServletException, IOException {
		String key = sentinelProperties.getApiKey();
		if (key == null || key.isBlank()) {
			filterChain.doFilter(request, response);
			return;
		}
		String path = request.getRequestURI();
		if (path == null || !path.startsWith("/api/")) {
			filterChain.doFilter(request, response);
			return;
		}
		if (path.startsWith("/api/v1/health/")) {
			filterChain.doFilter(request, response);
			return;
		}
		String provided = request.getHeader(HEADER_NAME);
		if (key.equals(provided)) {
			filterChain.doFilter(request, response);
			return;
		}
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(
				"{\"error\":\"Unauthorized\",\"detail\":\"Send header "
						+ HEADER_NAME
						+ " matching qa.sentinel.api-key (see server configuration).\"}");
	}
}
