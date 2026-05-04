package org.vcl.qasentinel.qa.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Playwright writes artifacts under {@code <workingDir>/test-results/…}. Some code paths historically stored
 * repo-relative hints like {@code qa-runner/test-results/…}, which must not be resolved as
 * {@code <workingDir>/qa-runner/test-results/…}.
 */
public final class PlaywrightArtifactPathResolver {

	private PlaywrightArtifactPathResolver() {}

	/**
	 * Resolves a screenshot or artifact path hint under the Playwright working directory.
	 *
	 * @param pathHint relative or absolute path as stored on the QA result / snapshot
	 * @param runnerRoot absolute normalized Playwright {@code workingDir}
	 * @return file path when it exists and stays under {@code runnerRoot} (or absolute existing file)
	 */
	public static Optional<Path> resolveExisting(Path runnerRoot, String pathHint) {
		if (pathHint == null || pathHint.isBlank()) {
			return Optional.empty();
		}
		String raw = pathHint.trim().replace('\\', '/');
		Path asInput = Paths.get(raw);
		if (asInput.isAbsolute()) {
			Path n = asInput.normalize();
			return Files.isRegularFile(n) ? Optional.of(n) : Optional.empty();
		}
		String relative = stripDuplicateRunnerPrefix(raw, runnerRoot);
		Path candidate = runnerRoot.resolve(relative).normalize();
		if (!candidate.startsWith(runnerRoot)) {
			return Optional.empty();
		}
		return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
	}

	static String stripDuplicateRunnerPrefix(String unixPath, Path runnerRoot) {
		String p = unixPath;
		String dirName = runnerRoot.getFileName() == null ? "" : runnerRoot.getFileName().toString();
		if ("qa-runner".equals(dirName) && p.startsWith("qa-runner/")) {
			return p.substring("qa-runner/".length());
		}
		int ix = p.indexOf("test-results/");
		if (ix > 0) {
			return p.substring(ix);
		}
		return p;
	}
}
