import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type Dispatch,
  type ReactNode,
  type SetStateAction,
} from "react";
import { runQaFlow, runQaFlowAllStories } from "../api/qaApi";
import type { BugReportItem, QaBatchItem, QaStatus, SentinelRun } from "../types/qa";

/** Header ticket control: run all stories in the Jira project (batch). */
export const TICKET_ALL = "__ALL__";

const STORAGE_RUNS = "qa-sentinel-runs-v1";
const STORAGE_BUGS = "qa-sentinel-bugs-v1";
const STORAGE_ISSUES = "qa-sentinel-issue-qa-v1";

function loadJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function id() {
  return `run-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

/** Clears persisted bug/session maps so the UI does not mix prior runs with the new agent execution. */
function resetAgentSessionCaches(
  setBugs: Dispatch<SetStateAction<BugReportItem[]>>,
  setIssueQaMap: Dispatch<SetStateAction<Record<string, QaStatus | "—">>>,
  setIssueBugLink: Dispatch<SetStateAction<Record<string, string>>>,
) {
  setBugs([]);
  localStorage.setItem(STORAGE_BUGS, JSON.stringify([]));
  setIssueQaMap({});
  localStorage.setItem(STORAGE_ISSUES, JSON.stringify({}));
  setIssueBugLink({});
  localStorage.setItem("qa-sentinel-issue-bug-v1", JSON.stringify({}));
}

export type HealthState = "unknown" | "healthy" | "degraded";

interface SentinelContextValue {
  projectKey: string;
  setProjectKey: (k: string) => void;
  issueKey: string;
  setIssueKey: (k: string) => void;
  /** Header: {@link TICKET_ALL} runs batch QA; otherwise a Jira story key for single-story QA. */
  ticketSelection: string;
  setTicketSelection: Dispatch<SetStateAction<string>>;
  runs: SentinelRun[];
  bugs: BugReportItem[];
  issueQaMap: Record<string, QaStatus | "—">;
  issueBugLink: Record<string, string>;
  health: HealthState;
  refreshHealth: () => Promise<void>;
  /** Single-story QA (optional override key for one-shot runs). */
  runQa: (issueKeyOverride?: string) => Promise<void>;
  /** Runs QA for every story in the current Jira project (batch endpoint). */
  runQaAllStories: () => Promise<void>;
  /** Uses {@link ticketSelection}: batch when {@link TICKET_ALL}, else single story. */
  runAgent: () => Promise<void>;
  running: boolean;
  lastError: string | null;
  metrics: {
    totalTests: number;
    failures: number;
    bugsCreated: number;
    lastRunStatus: QaStatus | "—" | "RUNNING";
  };
  /** Increments when an agent run finishes — subscribe to refetch Jira/bug API data. */
  agentDataEpoch: number;
}

const SentinelContext = createContext<SentinelContextValue | null>(null);

export function SentinelProvider({ children }: { children: ReactNode }) {
  const [projectKey, setProjectKey] = useState("SCRUM");
  const [issueKey, setIssueKey] = useState("SCRUM-1");
  const [ticketSelection, setTicketSelection] = useState(TICKET_ALL);
  const [runs, setRuns] = useState<SentinelRun[]>(() => loadJson(STORAGE_RUNS, []));
  const [bugs, setBugs] = useState<BugReportItem[]>(() => loadJson(STORAGE_BUGS, []));
  const [issueQaMap, setIssueQaMap] = useState<Record<string, QaStatus | "—">>(() =>
    loadJson(STORAGE_ISSUES, {}),
  );
  const [issueBugLink, setIssueBugLink] = useState<Record<string, string>>(() =>
    loadJson("qa-sentinel-issue-bug-v1", {}),
  );
  const [health, setHealth] = useState<HealthState>("unknown");
  const [running, setRunning] = useState(false);
  const [lastError, setLastError] = useState<string | null>(null);
  const [agentDataEpoch, setAgentDataEpoch] = useState(0);
  const refreshHealth = useCallback(async () => {
    try {
      const h = await fetch("/api/v1/qa/health");
      if (!h.ok) throw new Error(String(h.status));
      setHealth("healthy");
    } catch {
      setHealth("degraded");
    }
  }, []);

  useEffect(() => {
    void refreshHealth();
  }, [refreshHealth]);

  const appendLog = (runId: string, line: string) => {
    setRuns((prev) =>
      prev.map((r) => (r.id === runId ? { ...r, logs: [...r.logs, line] } : r)),
    );
  };

  const runQa = useCallback(async (issueKeyOverride?: string) => {
    const key = (issueKeyOverride ?? issueKey).trim() || `${projectKey}-1`;
    if (issueKeyOverride != null && issueKeyOverride.trim()) {
      setIssueKey(issueKeyOverride.trim());
    }
    setLastError(null);
    resetAgentSessionCaches(setBugs, setIssueQaMap, setIssueBugLink);
    setRunning(true);
    const runId = id();
    const startedAt = new Date().toISOString();
    const initial: SentinelRun = {
      id: runId,
      issueKey: key,
      startedAt,
      status: "RUNNING",
      stepsExecuted: [],
      failureReason: "",
      screenshotPath: "",
      jiraBugKey: "",
      traceId: "",
      failedStep: null,
      logs: [
        `[${new Date().toISOString()}] QA Sentinel — orchestration started for ${key}`,
        `[sentinel] Resolving PRD / test context…`,
      ],
    };
    setRuns(() => {
      const next = [initial];
      localStorage.setItem(STORAGE_RUNS, JSON.stringify(next));
      return next;
    });

    const timers: ReturnType<typeof setTimeout>[] = [];
    const schedule = (ms: number, fn: () => void) => {
      timers.push(setTimeout(fn, ms));
    };

    schedule(400, () => appendLog(runId, "[groq] Planning structured steps (or offline fallback)…"));
    schedule(900, () => appendLog(runId, "[playwright] Spawning runner in qa-runner…"));
    schedule(1400, () => appendLog(runId, "[playwright] Executing dynamic-test.spec.js…"));

    try {
      const result = await runQaFlow(key);
      appendLog(
        runId,
        `[sentinel] Completed with status=${result.status} trace=${result.traceId || "—"}`,
      );

      const finished: SentinelRun = {
        id: runId,
        issueKey: key,
        startedAt,
        finishedAt: new Date().toISOString(),
        status: result.status as QaStatus,
        stepsExecuted: result.stepsExecuted ?? [],
        failureReason: result.failureReason ?? "",
        screenshotPath: result.screenshotPath ?? "",
        jiraBugKey: result.jiraBugKey ?? "",
        traceId: result.traceId ?? "",
        failedStep: result.failedStep ?? null,
        logs: [],
      };

      setRuns((prev) => {
        const cur = prev.find((r) => r.id === runId);
        const merged = { ...finished, logs: cur?.logs ?? [] };
        if (result.failureReason) {
          merged.logs = [
            ...merged.logs,
            `[failure] ${result.failureReason.slice(0, 2000)}${result.failureReason.length > 2000 ? "…" : ""}`,
          ];
        }
        const next = [merged, ...prev.filter((r) => r.id !== runId)];
        localStorage.setItem(STORAGE_RUNS, JSON.stringify(next.slice(0, 50)));
        return next;
      });

      const upper = result.status.toUpperCase();
      const qa: QaStatus | "—" =
        upper === "PASS" || upper === "FAIL" || upper === "ERROR" ? (upper as QaStatus) : "—";
      setIssueQaMap((m) => {
        const next: Record<string, QaStatus | "—"> = { ...m, [key]: qa };
        localStorage.setItem(STORAGE_ISSUES, JSON.stringify(next));
        return next;
      });
      if (result.jiraBugKey) {
        setIssueBugLink((m) => {
          const next = { ...m, [key]: result.jiraBugKey };
          localStorage.setItem("qa-sentinel-issue-bug-v1", JSON.stringify(next));
          return next;
        });
        const bug: BugReportItem = {
          id: `bug-${result.jiraBugKey}`,
          title: `QA failure — ${key}`,
          status: "Open",
          linkedIssue: key,
          screenshotHint: result.screenshotPath || "",
          createdAt: new Date().toISOString(),
        };
        setBugs((prev) => {
          const next = [bug, ...prev.filter((b) => b.id !== bug.id)];
          localStorage.setItem(STORAGE_BUGS, JSON.stringify(next.slice(0, 100)));
          return next;
        });
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setLastError(msg);
      appendLog(runId, `[error] ${msg}`);
      setRuns((prev) => {
        const cur = prev.find((r) => r.id === runId);
        const failed: SentinelRun = {
          id: runId,
          issueKey: key,
          startedAt,
          finishedAt: new Date().toISOString(),
          status: "ERROR",
          stepsExecuted: cur?.stepsExecuted ?? [],
          failureReason: msg,
          screenshotPath: "",
          jiraBugKey: "",
          traceId: "",
          failedStep: null,
          logs: [...(cur?.logs ?? []), `[error] ${msg}`],
        };
        const next = [failed, ...prev.filter((r) => r.id !== runId)];
        localStorage.setItem(STORAGE_RUNS, JSON.stringify(next.slice(0, 50)));
        return next;
      });
    } finally {
      timers.forEach(clearTimeout);
      setRunning(false);
      void refreshHealth();
      setAgentDataEpoch((n) => n + 1);
    }
  }, [issueKey, projectKey, refreshHealth]);

  const runQaAllStories = useCallback(async () => {
    const pk = projectKey.trim();
    if (!pk) return;
    setLastError(null);
    resetAgentSessionCaches(setBugs, setIssueQaMap, setIssueBugLink);
    setRunning(true);
    const runId = id();
    const startedAt = new Date().toISOString();
    const label = `${pk}:BATCH`;
    const initial: SentinelRun = {
      id: runId,
      issueKey: label,
      startedAt,
      status: "RUNNING",
      stepsExecuted: [],
      failureReason: "",
      screenshotPath: "",
      jiraBugKey: "",
      traceId: "",
      failedStep: null,
      logs: [`[${startedAt}] Batch QA — all stories in project ${pk}`],
    };
    setRuns(() => {
      const next = [initial];
      localStorage.setItem(STORAGE_RUNS, JSON.stringify(next));
      return next;
    });

    const applyItem = (item: QaBatchItem) => {
      const k = item.issueKey?.trim();
      if (!k) return;
      const upper = (item.status || "").toUpperCase();
      const qa: QaStatus | "—" =
        upper === "PASS" || upper === "FAIL" || upper === "ERROR" ? (upper as QaStatus) : "—";
      setIssueQaMap((m) => {
        const next = { ...m, [k]: qa };
        localStorage.setItem(STORAGE_ISSUES, JSON.stringify(next));
        return next;
      });
      const bugKey = item.jiraBugKey?.trim();
      if (bugKey) {
        setIssueBugLink((m) => {
          const next = { ...m, [k]: bugKey };
          localStorage.setItem("qa-sentinel-issue-bug-v1", JSON.stringify(next));
          return next;
        });
        const bug: BugReportItem = {
          id: `bug-${bugKey}`,
          title: `QA failure — ${k}`,
          status: "Open",
          linkedIssue: k,
          screenshotHint: "",
          createdAt: new Date().toISOString(),
        };
        setBugs((prev) => {
          const next = [bug, ...prev.filter((b) => b.id !== bug.id)];
          localStorage.setItem(STORAGE_BUGS, JSON.stringify(next.slice(0, 100)));
          return next;
        });
      }
    };

    try {
      const batch = await runQaFlowAllStories(pk);
      appendLog(runId, `[sentinel] Batch returned ${batch.items?.length ?? 0} item(s)`);
      for (const item of batch.items ?? []) {
        applyItem(item);
        appendLog(runId, `  · ${item.issueKey}: ${item.status}${item.jiraBugKey ? ` → ${item.jiraBugKey}` : ""}`);
      }
      const items = batch.items ?? [];
      const overall: QaStatus =
        items.some((i) => i.status === "ERROR") ? "ERROR" : items.some((i) => i.status === "FAIL") ? "FAIL" : "PASS";
      const finished: SentinelRun = {
        id: runId,
        issueKey: label,
        startedAt,
        finishedAt: new Date().toISOString(),
        status: overall,
        stepsExecuted: [],
        failureReason: items.map((i) => `${i.issueKey}:${i.status}`).join("; ") || "empty batch",
        screenshotPath: "",
        jiraBugKey: "",
        traceId: "",
        failedStep: null,
        logs: [],
      };
      setRuns((prev) => {
        const cur = prev.find((r) => r.id === runId);
        const merged = { ...finished, logs: cur?.logs ?? [] };
        const next = [merged, ...prev.filter((r) => r.id !== runId)];
        localStorage.setItem(STORAGE_RUNS, JSON.stringify(next.slice(0, 50)));
        return next;
      });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setLastError(msg);
      appendLog(runId, `[error] ${msg}`);
      setRuns((prev) => {
        const cur = prev.find((r) => r.id === runId);
        const failed: SentinelRun = {
          id: runId,
          issueKey: label,
          startedAt,
          finishedAt: new Date().toISOString(),
          status: "ERROR",
          stepsExecuted: cur?.stepsExecuted ?? [],
          failureReason: msg,
          screenshotPath: "",
          jiraBugKey: "",
          traceId: "",
          failedStep: null,
          logs: [...(cur?.logs ?? []), `[error] ${msg}`],
        };
        const next = [failed, ...prev.filter((r) => r.id !== runId)];
        localStorage.setItem(STORAGE_RUNS, JSON.stringify(next.slice(0, 50)));
        return next;
      });
    } finally {
      setRunning(false);
      void refreshHealth();
      setAgentDataEpoch((n) => n + 1);
    }
  }, [projectKey, refreshHealth]);

  const runAgent = useCallback(async () => {
    if (ticketSelection === TICKET_ALL) {
      await runQaAllStories();
    } else {
      await runQa(ticketSelection);
    }
  }, [ticketSelection, runQa, runQaAllStories]);

  const metrics = useMemo(() => {
    const done = runs.filter((r) => r.status !== "RUNNING" && r.status !== "PENDING");
    const failures = done.filter((r) => r.status === "FAIL" || r.status === "ERROR").length;
    const last = done[0];
    const bugKeys = new Set<string>();
    for (const r of runs) {
      const k = r.jiraBugKey?.trim();
      if (k) bugKeys.add(k);
    }
    return {
      totalTests: done.length,
      failures,
      bugsCreated: bugKeys.size,
      lastRunStatus: last
        ? (last.status as QaStatus | "RUNNING")
        : ("—" as const),
    };
  }, [runs]);

  const value: SentinelContextValue = {
    projectKey,
    setProjectKey,
    issueKey,
    setIssueKey,
    ticketSelection,
    setTicketSelection,
    runs,
    bugs,
    issueQaMap,
    issueBugLink,
    health,
    refreshHealth,
    runQa,
    runQaAllStories,
    runAgent,
    running,
    lastError,
    metrics,
    agentDataEpoch,
  };

  return <SentinelContext.Provider value={value}>{children}</SentinelContext.Provider>;
}

export function useSentinel() {
  const ctx = useContext(SentinelContext);
  if (!ctx) throw new Error("useSentinel must be used within SentinelProvider");
  return ctx;
}
