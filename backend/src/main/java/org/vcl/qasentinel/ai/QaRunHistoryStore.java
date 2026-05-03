package org.vcl.qasentinel.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import org.vcl.qasentinel.ai.model.QaRunSnapshot;
import org.vcl.qasentinel.qa.model.QaResult;

@Service
public class QaRunHistoryStore {

	private static final int MAX = 100;

	private final Deque<QaRunSnapshot> runs = new ArrayDeque<>();

	public synchronized void record(String issueKey, QaResult result) {
		runs.addFirst(QaRunSnapshot.from(issueKey, result));
		while (runs.size() > MAX) {
			runs.removeLast();
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
}
