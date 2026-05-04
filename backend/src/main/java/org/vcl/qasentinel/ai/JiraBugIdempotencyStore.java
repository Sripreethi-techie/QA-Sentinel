package org.vcl.qasentinel.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import org.vcl.qasentinel.config.QaSentinelProperties;
import org.vcl.qasentinel.qa.model.TestStep;
import org.vcl.qasentinel.qa.service.PlaywrightService;

/**
 * Persists a map of failure fingerprints to Jira bug keys so batch re-runs do not file duplicates.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JiraBugIdempotencyStore {

	private static final int MAX_ENTRIES = 2000;

	private final QaSentinelProperties persistenceProperties;
	private final ObjectMapper objectMapper;

	private final Map<String, String> keyToJira = new LinkedHashMap<>();

	@PostConstruct
	void load() {
		if (!persistenceProperties.isBugIdempotencyEnabled()) {
			return;
		}
		Path path = idempotencyPath();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			if (json.isBlank()) {
				return;
			}
			Map<String, String> loaded =
					objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
			synchronized (keyToJira) {
				keyToJira.clear();
				for (Map.Entry<String, String> e : loaded.entrySet()) {
					if (e.getKey() != null && e.getValue() != null && !e.getKey().isBlank() && !e.getValue().isBlank()) {
						keyToJira.put(e.getKey().trim(), e.getValue().trim());
					}
				}
				trimToCap();
			}
			log.info("Loaded {} Jira bug idempotency entries from {}", keyToJira.size(), path);
		}
		catch (Exception e) {
			log.warn("Could not load bug idempotency file {}: {}", path, e.getMessage());
		}
	}

	public Optional<String> findExistingBugKey(String idempotencyKey) {
		if (!persistenceProperties.isBugIdempotencyEnabled() || idempotencyKey == null || idempotencyKey.isBlank()) {
			return Optional.empty();
		}
		synchronized (keyToJira) {
			String v = keyToJira.get(idempotencyKey.trim());
			return v == null || v.isBlank() ? Optional.empty() : Optional.of(v.trim());
		}
	}

	public void remember(String idempotencyKey, String jiraBugKey) {
		if (!persistenceProperties.isBugIdempotencyEnabled()
				|| idempotencyKey == null
				|| idempotencyKey.isBlank()
				|| jiraBugKey == null
				|| jiraBugKey.isBlank()) {
			return;
		}
		synchronized (keyToJira) {
			keyToJira.put(idempotencyKey.trim(), jiraBugKey.trim());
			trimToCap();
			saveLocked();
		}
	}

	/** Stable key for the same story + trace + failure text (+ failed step index when known). */
	public static String computeIdempotencyKey(
			String issueKey,
			String traceId,
			PlaywrightService.RunOutcome outcome,
			TestStep failedStep) {
		String ik = issueKey == null ? "" : issueKey.trim().toUpperCase(Locale.ROOT);
		String tid = traceId == null ? "" : traceId.trim();
		String msg = "";
		String exact = "";
		if (outcome != null) {
			msg = outcome.message() == null ? "" : outcome.message().trim();
			exact = outcome.exactError() == null ? "" : outcome.exactError().trim();
		}
		if (msg.length() > 400) {
			msg = msg.substring(0, 400);
		}
		if (exact.length() > 400) {
			exact = exact.substring(0, 400);
		}
		int stepNo = failedStep != null ? failedStep.stepNumber() : -1;
		String raw = ik + "\u0001" + tid + "\u0001" + stepNo + "\u0001" + msg + "\u0001" + exact;
		return "v1-" + sha256Hex(raw);
	}

	private static String sha256Hex(String raw) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (Exception e) {
			return Integer.toHexString(raw.hashCode());
		}
	}

	private void trimToCap() {
		while (keyToJira.size() > MAX_ENTRIES) {
			var it = keyToJira.keySet().iterator();
			if (!it.hasNext()) {
				break;
			}
			it.next();
			it.remove();
		}
	}

	private void saveLocked() {
		Path path = idempotencyPath();
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), keyToJira);
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (IOException atomicFailed) {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e) {
			log.warn("Could not save bug idempotency file {}: {}", path, e.getMessage());
		}
	}

	private Path idempotencyPath() {
		String p = persistenceProperties.getBugIdempotencyFile();
		if (p != null && !p.isBlank()) {
			return Path.of(p.trim()).toAbsolutePath().normalize();
		}
		return Path.of(System.getProperty("user.home"), ".qa-sentinel", "bug-idempotency.json").toAbsolutePath().normalize();
	}
}
