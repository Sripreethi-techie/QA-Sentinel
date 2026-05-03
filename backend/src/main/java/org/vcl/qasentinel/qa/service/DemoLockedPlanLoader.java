package org.vcl.qasentinel.qa.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import org.vcl.qasentinel.config.QaFlowProperties;
import org.vcl.qasentinel.qa.model.TestStep;

/**
 * Loads a pre-validated JSON step plan (same shape as Groq emits) for {@link QaFlowProperties#isDemoLockedMode()}.
 */
@Component
@RequiredArgsConstructor
public class DemoLockedPlanLoader {

	private final QaFlowProperties qaFlowProperties;
	private final ResourceLoader resourceLoader;
	private final ObjectMapper objectMapper;

	public List<TestStep> loadCachedSteps() {
		String rel = qaFlowProperties.getDemoLockedPlanResource() == null
				? ""
				: qaFlowProperties.getDemoLockedPlanResource().trim();
		if (rel.isEmpty()) {
			throw new IllegalStateException("qa.flow.demo-locked-plan-resource is empty");
		}
		String path = rel.startsWith("/") ? rel.substring(1) : rel;
		Resource resource = resourceLoader.getResource("classpath:" + path);
		if (!resource.exists()) {
			throw new IllegalStateException("Demo locked plan not found on classpath: " + path);
		}
		try (InputStream in = resource.getInputStream()) {
			List<TestStep> steps = objectMapper.readValue(in, new TypeReference<List<TestStep>>() {});
			if (steps == null || steps.isEmpty()) {
				throw new IllegalStateException("Demo locked plan has no steps: " + path);
			}
			return renumber(new ArrayList<>(steps));
		}
		catch (IllegalStateException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalStateException("Could not read demo locked plan " + path + ": " + e.getMessage(), e);
		}
	}

	private static List<TestStep> renumber(List<TestStep> steps) {
		List<TestStep> out = new ArrayList<>();
		int n = 1;
		for (TestStep s : steps) {
			out.add(new TestStep(n++, s.action(), s.description(), s.target(), s.value(), s.expected()));
		}
		return out;
	}
}
