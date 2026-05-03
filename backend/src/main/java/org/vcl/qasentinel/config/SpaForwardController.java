package org.vcl.qasentinel.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Vite SPA when {@code ../frontend/dist} is copied into the classpath (see {@code copyFrontend} in
 * {@code build.gradle}). REST {@code /api/**} is unaffected.
 */
@Controller
public class SpaForwardController {

	@GetMapping(value = { "/", "/runs", "/runs/**", "/bugs", "/bugs/**", "/agent", "/agent/**", "/loan", "/loan/**" })
	public String spa() {
		return "forward:/index.html";
	}
}
