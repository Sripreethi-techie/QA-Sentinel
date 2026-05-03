package org.vcl.qasentinel.qa.service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.vcl.qasentinel.config.PlaywrightProperties;
import org.vcl.qasentinel.logging.QaSentinelNarrative;

import org.vcl.qasentinel.qa.model.TestStep;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaywrightService {

	private static final String FAILURE_EVIDENCE_FILE = "dynamic-failure-evidence.json";

	/**
	 * @param failurePageUrl        URL of the page when the runner failed (from evidence file; may be empty)
	 * @param failedStep            Structured failing step from the runner (may be null)
	 * @param exactError            Runner-captured error message when available (may be empty)
	 * @param tracePathHint         Relative path hint for Playwright trace.zip (Jira attachment resolution)
	 * @param consoleLogPathHint    Relative path hint for browser console log file (optional)
	 * @param failureTimestampUtc   ISO-8601 instant from runner evidence when available (may be empty)
	 */
	public record RunOutcome(
			boolean passed,
			String message,
			String screenshotPath,
			String failurePageUrl,
			TestStep failedStep,
			String exactError,
			String tracePathHint,
			String consoleLogPathHint,
			String failureTimestampUtc) {

		public RunOutcome {
			if (message == null) {
				message = "";
			}
			if (screenshotPath == null) {
				screenshotPath = "";
			}
			if (failurePageUrl == null) {
				failurePageUrl = "";
			}
			if (exactError == null) {
				exactError = "";
			}
			if (tracePathHint == null) {
				tracePathHint = "";
			}
			if (consoleLogPathHint == null) {
				consoleLogPathHint = "";
			}
			if (failureTimestampUtc == null) {
				failureTimestampUtc = "";
			}
		}

		public RunOutcome(boolean passed, String message, String screenshotPath) {
			this(passed, message, screenshotPath, "", null, "", "", "", "");
		}

		/** Orchestrator catch path when the runner process fails before evidence is written. */
		public static RunOutcome runnerHostError(String message) {
			String m = message == null ? "" : message;
			return new RunOutcome(false, m, "", "", null, m, "", "", Instant.now().toString());
		}
	}

	private final PlaywrightProperties playwrightProperties;
	private final ObjectMapper objectMapper;

	/**
	 * Runs {@code npx playwright test} in {@link PlaywrightProperties#getWorkingDir()} against the
	 * configured spec (default {@code tests/dynamic-test.spec.js}). Steps are passed via
	 * {@code QA_STEPS_JSON} or {@code QA_STEPS_FILE} depending on {@code qa.playwright.steps-delivery}.
	 */
	public RunOutcome run(
			List<TestStep> steps,
			String envBaseUrl,
			String traceId,
			String projectKey,
			String issueKey)
			throws Exception {
		if (steps == null || steps.isEmpty()) {
			return RunOutcome.runnerHostError("No test steps to run.");
		}
		File workDir = new File(playwrightProperties.getWorkingDir()).getAbsoluteFile();
		if (!workDir.isDirectory()) {
			return RunOutcome.runnerHostError("Playwright workingDir is not a directory: " + workDir);
		}

		Path evidencePath = workDir.toPath().resolve("test-results").resolve(FAILURE_EVIDENCE_FILE);
		try {
			Files.deleteIfExists(evidencePath);
		}
		catch (Exception e) {
			log.debug("Could not clear prior failure evidence {}: {}", evidencePath, e.getMessage());
		}

		try {
			ensureRunnerDependencies(workDir);
		}
		catch (Exception e) {
			log.error("Playwright dependency bootstrap failed: {}", e.getMessage(), e);
			return RunOutcome.runnerHostError("Playwright dependency bootstrap failed: " + e.getMessage());
		}

		Path stepsFile = workDir.toPath().resolve("test-results").resolve("dynamic-steps.json");
		String delivery = playwrightProperties.getStepsDelivery() == null
				? "env"
				: playwrightProperties.getStepsDelivery().trim().toLowerCase();
		boolean useFile = "file".equals(delivery);

		List<String> command = buildCommand();
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(workDir);
		Map<String, String> env = pb.environment();
		env.put("TARGET_URL", envBaseUrl == null ? "" : envBaseUrl.trim());
		env.put("TRACE_ID", traceId == null ? "" : traceId);
		env.put("QA_PROJECT_KEY", projectKey == null ? "" : projectKey.trim());
		env.put("QA_ISSUE_KEY", issueKey == null ? "" : issueKey.trim());

		String reportBase = playwrightProperties.getReportBaseUrl() == null
				? ""
				: playwrightProperties.getReportBaseUrl().trim();
		if (!reportBase.isEmpty()) {
			String base = reportBase.replaceAll("/+$", "");
			env.put("QA_REPORT_URL", base + "/qa-report");
		}

		if (useFile) {
			Files.createDirectories(stepsFile.getParent());
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(stepsFile.toFile(), steps);
			env.put("QA_STEPS_FILE", stepsFile.toAbsolutePath().toString());
			env.remove("QA_STEPS_JSON");
			QaSentinelNarrative.line(log, "→ Wrote " + steps.size() + " step(s) to " + stepsFile.getFileName());
		}
		else {
			env.put("QA_STEPS_JSON", objectMapper.writeValueAsString(steps));
			env.remove("QA_STEPS_FILE");
		}

		pb.redirectErrorStream(true);

		QaSentinelNarrative.line(log, "→ Playwright runner starting (" + steps.size() + " steps, delivery=" + delivery + ")");
		try {
			Process p = pb.start();
			String output;
			try (var in = p.getInputStream()) {
				output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
			boolean finished = p.waitFor(playwrightProperties.getTimeoutSeconds(), TimeUnit.SECONDS);
			if (!finished) {
				p.destroyForcibly();
				return enrichFailureOutcome(
						workDir,
						new RunOutcome(
								false,
								"Playwright timed out after " + playwrightProperties.getTimeoutSeconds() + "s\n" + tail(output),
								defaultScreenshotPath(),
								"",
								null,
								"",
								"",
								"",
								""));
			}
			int code = p.exitValue();
			String shot = defaultScreenshotPath();
			if (code != 0) {
				log.warn("Playwright exit code {}: {}", code, tail(output));
				return enrichFailureOutcome(
						workDir,
						new RunOutcome(false, "Playwright failed (exit " + code + ")\n" + tail(output), shot, "", null, "", "", "", ""));
			}
			return new RunOutcome(true, tail(output), "");
		}
		finally {
			if (useFile) {
				try {
					Files.deleteIfExists(stepsFile);
				}
				catch (Exception e) {
					log.debug("Could not delete steps file {}: {}", stepsFile, e.getMessage());
				}
			}
		}
	}

	private List<String> buildCommand() {
		String spec = playwrightProperties.getTestArgs().trim();
		List<String> cmd = new ArrayList<>();
		if (isWindows()) {
			cmd.add("cmd.exe");
			cmd.add("/c");
			cmd.add("npx");
			cmd.add("playwright");
			cmd.add("test");
		}
		else {
			cmd.add("npx");
			cmd.add("playwright");
			cmd.add("test");
		}
		for (String part : spec.split("\\s+")) {
			if (!part.isBlank()) {
				cmd.add(part);
			}
		}
		return cmd;
	}

	private static final int NPM_INSTALL_TIMEOUT_MINUTES = 8;

	private void ensureRunnerDependencies(File workDir) throws Exception {
		Path playwrightModule = workDir.toPath().resolve("node_modules").resolve("@playwright").resolve("test");
		if (Files.isDirectory(playwrightModule)) {
			return;
		}
		QaSentinelNarrative.line(log, "→ Playwright runner: npm install (first run; browsers via postinstall)…");
		List<String> npmCmd = new ArrayList<>();
		if (isWindows()) {
			npmCmd.add("cmd.exe");
			npmCmd.add("/c");
			npmCmd.add("npm");
			npmCmd.add("install");
		}
		else {
			npmCmd.add("npm");
			npmCmd.add("install");
		}
		ProcessBuilder npmPb = new ProcessBuilder(npmCmd);
		npmPb.directory(workDir);
		npmPb.redirectErrorStream(true);
		Process npm = npmPb.start();
		String npmOut;
		try (var in = npm.getInputStream()) {
			npmOut = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		boolean npmDone = npm.waitFor(NPM_INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
		if (!npmDone) {
			npm.destroyForcibly();
			throw new IllegalStateException(
					"npm install timed out after " + NPM_INSTALL_TIMEOUT_MINUTES + "m\n" + tail(npmOut));
		}
		if (npm.exitValue() != 0) {
			throw new IllegalStateException("npm install failed (exit " + npm.exitValue() + ")\n" + tail(npmOut));
		}
		if (!Files.isDirectory(playwrightModule)) {
			throw new IllegalStateException(
					"npm install finished but @playwright/test is missing under " + workDir.getPath() + "/node_modules");
		}
		QaSentinelNarrative.line(log, "→ Playwright runner dependencies ready");
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	private static String tail(String s) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		int max = 8000;
		return s.length() <= max ? s : s.substring(s.length() - max);
	}

	private static String defaultScreenshotPath() {
		return "qa-runner/test-results/dynamic-failure.png";
	}

	private RunOutcome enrichFailureOutcome(File workDir, RunOutcome base) {
		ParsedEvidence ev = readFailureEvidence(workDir);
		String url = ev.pageUrl() != null && !ev.pageUrl().isBlank() ? ev.pageUrl() : "";
		TestStep step = ev.failedStep();
		Path shotUnderRunner = workDir.toPath().resolve("test-results").resolve("dynamic-failure.png");
		if (Files.isRegularFile(shotUnderRunner)) {
			QaSentinelNarrative.line(log, "Captured screenshot for failure analysis");
		}
		String screenshotOut = base.screenshotPath() == null ? "" : base.screenshotPath().trim();
		if (Files.isRegularFile(shotUnderRunner) && screenshotOut.isEmpty()) {
			screenshotOut = defaultScreenshotPath();
		}
		String exact = ev.errorMessage() != null && !ev.errorMessage().isBlank()
				? ev.errorMessage().trim()
				: (base.message() == null ? "" : base.message().trim());
		String traceHint = ev.traceZipRelative() == null ? "" : ev.traceZipRelative().trim();
		String consoleHint = ev.consoleLogRelative() == null ? "" : ev.consoleLogRelative().trim();
		String ts = ev.timestampUtc() == null ? "" : ev.timestampUtc().trim();
		return new RunOutcome(
				false,
				base.message(),
				screenshotOut,
				url,
				step,
				exact,
				traceHint,
				consoleHint,
				ts);
	}

	private ParsedEvidence readFailureEvidence(File workDir) {
		Path p = workDir.toPath().resolve("test-results").resolve(FAILURE_EVIDENCE_FILE);
		if (!Files.isRegularFile(p)) {
			return ParsedEvidence.EMPTY;
		}
		try {
			JsonNode root = objectMapper.readTree(p.toFile());
			String pageUrl = root.path("pageUrl").asText("").trim();
			TestStep step = null;
			JsonNode stepNode = root.path("failedStep");
			if (!stepNode.isMissingNode() && !stepNode.isNull() && stepNode.isObject()) {
				step = parseTestStepFromJson(stepNode);
			}
			String errorMessage = textOrEmpty(root.path("errorMessage"));
			String traceZipRelative = textOrNull(root.path("traceZipRelative"));
			String consoleLogRelative = textOrNull(root.path("consoleLogRelative"));
			String timestampUtc = textOrEmpty(root.path("timestampUtc"));
			return new ParsedEvidence(pageUrl, step, errorMessage, traceZipRelative, consoleLogRelative, timestampUtc);
		}
		catch (Exception e) {
			log.debug("Could not parse failure evidence {}: {}", p, e.getMessage());
			return ParsedEvidence.EMPTY;
		}
	}

	private record ParsedEvidence(
			String pageUrl,
			TestStep failedStep,
			String errorMessage,
			String traceZipRelative,
			String consoleLogRelative,
			String timestampUtc) {
		static final ParsedEvidence EMPTY = new ParsedEvidence("", null, "", null, null, "");
	}

	private TestStep parseTestStepFromJson(JsonNode n) {
		try {
			int num = n.path("stepNumber").asInt(0);
			String action = n.path("action").asText("").trim();
			String desc = textOrEmpty(n.path("description"));
			String target = textOrNull(n.path("target"));
			String value = textOrNull(n.path("value"));
			String expected = textOrNull(n.path("expected"));
			if (action.isEmpty()) {
				return null;
			}
			return new TestStep(num, action, desc, target, value, expected);
		}
		catch (Exception e) {
			log.debug("Could not map failedStep JSON: {}", e.getMessage());
			return null;
		}
	}

	private static String textOrEmpty(JsonNode n) {
		if (n == null || n.isNull() || n.isMissingNode()) {
			return "";
		}
		return n.asText("").trim();
	}

	private static String textOrNull(JsonNode n) {
		if (n == null || n.isNull() || n.isMissingNode()) {
			return null;
		}
		String t = n.asText("").trim();
		return t.isEmpty() ? null : t;
	}
}
