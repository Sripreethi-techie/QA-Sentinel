package org.vcl.qasentinel.qa.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaywrightArtifactPathResolverTest {

	@Test
	void resolvesLegacyRepoRelativeHintUnderRunnerNamedQaRunner(@TempDir Path base) throws Exception {
		Path qaRunner = base.resolve("qa-runner");
		Path testResults = Files.createDirectories(qaRunner.resolve("test-results"));
		Path png = testResults.resolve("dynamic-failure.png");
		Files.writeString(png, "x");
		Path root = qaRunner.toAbsolutePath().normalize();

		assertThat(PlaywrightArtifactPathResolver.resolveExisting(root, "qa-runner/test-results/dynamic-failure.png"))
				.contains(png.toAbsolutePath().normalize());
		assertThat(PlaywrightArtifactPathResolver.resolveExisting(root, "test-results/dynamic-failure.png"))
				.contains(png.toAbsolutePath().normalize());
	}

	@Test
	void stripDuplicateRunnerPrefix_onlyWhenRunnerDirIsQaRunner() {
		Path root = Path.of("/x", "qa-runner").normalize();
		assertThat(PlaywrightArtifactPathResolver.stripDuplicateRunnerPrefix("qa-runner/test-results/a.png", root))
				.isEqualTo("test-results/a.png");
		Path other = Path.of("/x", "my-runner").normalize();
		assertThat(PlaywrightArtifactPathResolver.stripDuplicateRunnerPrefix("qa-runner/test-results/a.png", other))
				.isEqualTo("test-results/a.png");
	}
}
