package org.vcl.qasentinel.ai;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.vcl.qasentinel.ai.model.QaRunSnapshot;
import org.vcl.qasentinel.config.QaSentinelProperties;
import org.vcl.qasentinel.qa.model.QaResult;

@Service
@RequiredArgsConstructor
@Slf4j
public class QaRunHistoryStore {

	private static final int MAX = 100;

	private final Deque<QaRunSnapshot> runs = new ArrayDeque<>();

	private final QaSentinelProperties persistenceProperties;
	private final ObjectMapper objectMapper;

	@PostConstruct
	void loadFromDisk() {
		if (!persistenceProperties.isPersistenceEnabled()) {
			return;
		}
		Path path = historyPath();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
			List<QaRunSnapshot> tail = new ArrayList<>(Math.min(MAX, lines.size()));
			int start = Math.max(0, lines.size() - MAX);
			for (int i = start; i < lines.size(); i++) {
				String line = lines.get(i);
				if (line == null || line.isBlank()) {
					continue;
				}
				try {
					tail.add(objectMapper.readValue(line, QaRunSnapshot.class));
				}
				catch (Exception e) {
					log.debug("Skip bad history line: {}", e.getMessage());
				}
			}
			synchronized (runs) {
				runs.clear();
				for (int i = tail.size() - 1; i >= 0; i--) {
					runs.addFirst(tail.get(i));
				}
			}
			log.info("Restored {} QA run snapshot(s) from {}", tail.size(), path);
		}
		catch (IOException e) {
			log.warn("Could not read run history file {}: {}", path, e.getMessage());
		}
	}

	public synchronized void record(String issueKey, QaResult result) {
		runs.addFirst(QaRunSnapshot.from(issueKey, result));
		while (runs.size() > MAX) {
			runs.removeLast();
		}
		if (persistenceProperties.isPersistenceEnabled()) {
			appendSnapshotToDisk(runs.peekFirst());
		}
	}

	public synchronized List<QaRunSnapshot> recent(int limit) {
		int n = Math.max(0, limit);
		List<QaRunSnapshot> out = new ArrayList<>(Math.min(n, runs.size()));
		int i = 0;
		for (QaRunSnapshot s : runs) {
			out.add(s);
			if (++i >= n) {
				break;
			}
		}
		return out;
	}

	/** Most recent run that produced this Jira bug key (deque is newest-first). */
	public synchronized Optional<QaRunSnapshot> findLatestWithJiraBugKey(String jiraBugKey) {
		if (jiraBugKey == null || jiraBugKey.isBlank()) {
			return Optional.empty();
		}
		for (QaRunSnapshot s : runs) {
			if (jiraBugKey.equals(s.jiraBugKey())) {
				return Optional.of(s);
			}
		}
		return Optional.empty();
	}

	private void appendSnapshotToDisk(QaRunSnapshot snapshot) {
		if (snapshot == null) {
			return;
		}
		Path path = historyPath();
		try {
			Files.createDirectories(path.getParent());
			String line = objectMapper.writeValueAsString(snapshot);
			try (BufferedWriter w = Files.newBufferedWriter(
					path,
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND)) {
				w.write(line);
				w.newLine();
			}
		}
		catch (IOException e) {
			log.warn("Could not append run history to {}: {}", path, e.getMessage());
		}
	}

	private Path historyPath() {
		String p = persistenceProperties.getRunHistoryFile();
		if (p != null && !p.isBlank()) {
			return Path.of(p.trim()).toAbsolutePath().normalize();
		}
		return Path.of(System.getProperty("user.home"), ".qa-sentinel", "run-history.jsonl").toAbsolutePath().normalize();
	}

	/** For tests: clear memory (does not delete disk file). */
	public synchronized void clearForTests() {
		runs.clear();
	}
}
