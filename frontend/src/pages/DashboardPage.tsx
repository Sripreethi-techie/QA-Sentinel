import { Activity, Bug, ListChecks, Timer } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { KpiCard } from "../components/KpiCard";
import { StatusBadge } from "../components/StatusBadge";
import { useSentinel } from "../context/SentinelContext";
import type { JiraIssueRow } from "../types/qa";

/** Matches {@link org.vcl.qasentinel.jira.JiraIssueView} JSON from Spring. */
interface JiraStoryApi {
  key: string;
  summary: string;
  statusName?: string;
  assigneeName?: string;
  updated?: string;
}

interface JiraStoriesPayload {
  items?: JiraStoryApi[];
  error?: string;
}

function mapStoryToRow(i: JiraStoryApi): JiraIssueRow {
  const updatedRaw = i.updated ?? "";
  const updatedShort = updatedRaw.includes("T") ? updatedRaw.slice(0, 10) : updatedRaw.slice(0, 10);
  return {
    key: i.key,
    summary: i.summary ?? "",
    status: (i.statusName ?? "—") || "—",
    assignee: (i.assigneeName ?? "—") || "—",
    updated: updatedShort || "—",
  };
}

export function DashboardPage() {
  const { metrics, issueQaMap, issueBugLink, runs, running, projectKey } = useSentinel();
  const [baseRows, setBaseRows] = useState<JiraIssueRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const loadStories = useCallback(async () => {
    const pk = projectKey.trim();
    if (!pk) {
      setBaseRows([]);
      setLoadError("Select a project in the header.");
      setLoading(false);
      return;
    }
    setLoading(true);
    setLoadError(null);
    try {
      const r = await fetch(`/api/v1/jira/projects/${encodeURIComponent(pk)}/stories`, {
        headers: { Accept: "application/json" },
      });
      if (!r.ok) {
        setLoadError(`Could not reach the QA API (HTTP ${r.status}). Is the backend running on port 9096?`);
        setBaseRows([]);
        return;
      }
      const data = (await r.json()) as JiraStoriesPayload | JiraStoryApi[];
      if (Array.isArray(data)) {
        setLoadError(null);
        setBaseRows(data.map(mapStoryToRow));
        return;
      }
      const items = (data.items ?? []).map(mapStoryToRow);
      setBaseRows(items);
      if (data.error && data.error.trim()) {
        setLoadError(data.error.trim());
      } else {
        setLoadError(null);
      }
    } catch {
      setLoadError("Network error while loading Jira stories.");
      setBaseRows([]);
    } finally {
      setLoading(false);
    }
  }, [projectKey]);

  useEffect(() => {
    void loadStories();
  }, [loadStories]);

  const tableRows = useMemo(() => {
    return baseRows.map((r) => ({
      ...r,
      qaResult: issueQaMap[r.key] ?? ("—" as const),
      bugLink: issueBugLink[r.key] ?? "",
    }));
  }, [baseRows, issueQaMap, issueBugLink]);

  const lastStatus = runs[0]?.status === "RUNNING" ? "RUNNING" : metrics.lastRunStatus;

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <div>
        <h1 className="text-xl font-semibold tracking-tight text-slate-900 dark:text-white">
          Dashboard
        </h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Autonomous QA Sentinel — coverage, failures, and Jira-linked outcomes.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard
          title="Total tests run"
          value={metrics.totalTests}
          hint="Recorded in this workspace"
          icon={ListChecks}
          accent="blue"
        />
        <KpiCard
          title="Failures"
          value={metrics.failures}
          hint="FAIL + ERROR outcomes"
          icon={Activity}
          accent="rose"
        />
        <KpiCard
          title="Bugs created"
          value={metrics.bugsCreated}
          hint="Jira bugs filed from failures"
          icon={Bug}
          accent="violet"
        />
        <KpiCard
          title="Last run status"
          value={lastStatus === "—" ? "—" : running ? "…" : String(lastStatus)}
          hint={runs[0]?.issueKey ? `Issue ${runs[0].issueKey}` : "No runs yet"}
          icon={Timer}
          accent="emerald"
        />
      </div>

      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-card dark:border-slate-800 dark:bg-slate-900/80 dark:shadow-card-dark">
        <div className="border-b border-slate-200 px-4 py-3 dark:border-slate-800">
          <h2 className="text-sm font-semibold text-slate-900 dark:text-white">User stories</h2>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Live Jira issues for project <span className="font-mono text-slate-600 dark:text-slate-300">{projectKey}</span>{" "}
            (issue type and filters match <span className="font-mono">qa.flow.batch-*</span> in the backend). QA result
            columns reflect Sentinel runs in this browser.
          </p>
        </div>
        {loadError ? (
          <div className="border-b border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-100">
            {loadError}
          </div>
        ) : null}
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50/80 text-xs font-medium uppercase tracking-wide text-slate-500 dark:border-slate-800 dark:bg-slate-950/80 dark:text-slate-400">
                <th className="px-4 py-3">Jira issue</th>
                <th className="px-4 py-3">Summary</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">QA result</th>
                <th className="px-4 py-3">Bug लिंक</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                    Loading stories from Jira…
                  </td>
                </tr>
              ) : tableRows.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                    No Jira issues found for this project (after story-type filter and fallback). Check the project key,
                    Jira credentials, and{" "}
                    <span className="font-mono">qa.flow.batch-story-issue-type-name</span> (e.g. Story or User Story) or{" "}
                    <span className="font-mono">qa.flow.batch-jql-suffix</span> in the backend.
                  </td>
                </tr>
              ) : (
                tableRows.map((row) => (
                  <tr
                    key={row.key}
                    className="bg-white hover:bg-slate-50/80 dark:bg-transparent dark:hover:bg-slate-800/40"
                  >
                    <td className="px-4 py-3 font-mono text-xs font-medium text-blue-600 dark:text-blue-400">
                      {row.key}
                    </td>
                    <td className="max-w-xs truncate px-4 py-3 text-slate-700 dark:text-slate-200">
                      {row.summary}
                    </td>
                    <td className="px-4 py-3">
                      <span className="rounded-md bg-slate-100 px-2 py-0.5 text-xs text-slate-700 dark:bg-slate-800 dark:text-slate-300">
                        {row.status}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      {row.qaResult === "—" ? (
                        <StatusBadge label="—" />
                      ) : (
                        <StatusBadge label={row.qaResult} />
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {row.bugLink ? (
                        <a
                          href={`#bug-${row.bugLink}`}
                          className="font-mono text-xs text-blue-600 underline-offset-2 hover:underline dark:text-blue-400"
                        >
                          {row.bugLink}
                        </a>
                      ) : (
                        <span className="text-slate-400">—</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
