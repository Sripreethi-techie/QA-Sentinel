import { Activity, Bug, ListChecks, Timer } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchBugReports } from "../api/qaApi";
import { KpiCard } from "../components/KpiCard";
import { StatusBadge } from "../components/StatusBadge";
import { useSentinel } from "../context/SentinelContext";
import type { ApiBugReport, BugReportItem, JiraIssueRow } from "../types/qa";

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

/** Distinct Jira bug keys linked to a story (local session + server run history). */
function distinctBugCountForStory(storyKey: string, localBugs: BugReportItem[], apiBugs: ApiBugReport[]): number {
  const jiraKeys = new Set<string>();
  for (const b of localBugs) {
    if (b.linkedIssue !== storyKey) continue;
    const m = /^bug-(.+)$/.exec(b.id);
    jiraKeys.add(m ? m[1].trim() : b.id.trim());
  }
  for (const b of apiBugs) {
    if (b.linkedIssueKey !== storyKey) continue;
    const k = b.jiraKey?.trim();
    if (k) jiraKeys.add(k);
  }
  return jiraKeys.size;
}

export function DashboardPage() {
  const { metrics, bugs: localBugs, runs, running, projectKey, agentDataEpoch } = useSentinel();
  const [baseRows, setBaseRows] = useState<JiraIssueRow[]>([]);
  const [apiBugs, setApiBugs] = useState<ApiBugReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const loadStories = useCallback(async () => {
    const pk = projectKey.trim();
    if (!pk) {
      setBaseRows([]);
      setApiBugs([]);
      setLoadError("Select a project in the header.");
      setLoading(false);
      return;
    }
    setLoading(true);
    setLoadError(null);
    try {
      const [storiesRes, bugsData] = await Promise.all([
        fetch(`/api/v1/jira/projects/${encodeURIComponent(pk)}/stories`, {
          headers: { Accept: "application/json" },
        }),
        fetchBugReports().catch(() => [] as ApiBugReport[]),
      ]);
      setApiBugs(Array.isArray(bugsData) ? bugsData : []);
      if (!storiesRes.ok) {
        setLoadError(`Could not reach the QA API (HTTP ${storiesRes.status}). Is the backend running on port 9096?`);
        setBaseRows([]);
        return;
      }
      const data = (await storiesRes.json()) as JiraStoriesPayload | JiraStoryApi[];
      if (Array.isArray(data)) {
        setLoadError(null);
        setBaseRows(data.map(mapStoryToRow));
        return;
      }
      const items = (data.items ?? []).map(mapStoryToRow);
      setBaseRows(items);
      const serverErr = typeof data === "object" && !Array.isArray(data) && data.error?.trim() ? data.error.trim() : "";
      if (items.length === 0) {
        setLoadError(
          serverErr ||
            "No stories were returned. Check qa.flow.batch-story-issue-type-name / batch JQL, or GET /api/v1/jira/projects/{key}/issue-types for valid issue type names.",
        );
      } else {
        setLoadError(serverErr || null);
      }
    } catch {
      setLoadError("Network error while loading Jira stories.");
      setBaseRows([]);
      setApiBugs([]);
    } finally {
      setLoading(false);
    }
  }, [projectKey, agentDataEpoch]);

  useEffect(() => {
    void loadStories();
  }, [loadStories]);

  const tableRows = useMemo(() => {
    return baseRows.map((r) => {
      const bugCount = distinctBugCountForStory(r.key, localBugs, apiBugs);
      const qaLabel = bugCount >= 1 ? "fail" : "pass";
      return { ...r, bugCount, qaResult: qaLabel };
    });
  }, [baseRows, localBugs, apiBugs]);

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
          hint={runs[0]?.issueKey ? `Ticket ${runs[0].issueKey}` : "No runs yet"}
          icon={Timer}
          accent="emerald"
        />
      </div>

      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-card dark:border-slate-800 dark:bg-slate-900/80 dark:shadow-card-dark">
        <div className="border-b border-slate-200 px-4 py-3 dark:border-slate-800">
          <h2 className="text-sm font-semibold text-slate-900 dark:text-white">User stories</h2>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Live Jira issues for project <span className="font-mono text-slate-600 dark:text-slate-300">{projectKey}</span>{" "}
            (issue type and filters match <span className="font-mono">qa.flow.batch-*</span> in the backend).{" "}
            <span className="font-medium text-slate-600 dark:text-slate-300">QA result</span> is{" "}
            <span className="font-mono">pass</span> when no bugs are linked to the story, <span className="font-mono">fail</span>{" "}
            when one or more QA-filed bugs are linked. Bug counts merge this browser session with the server bug list. A story
            only gets bugs after Playwright QA fails for that issue; use header <span className="font-semibold">Run Agent</span> to
            execute QA for every story in the project (failures create
            Jira bugs with screenshots when the runner saves them).
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
                <th className="px-4 py-3">Jira ID</th>
                <th className="px-4 py-3">Summary</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">QA result</th>
                <th className="px-4 py-3">Bugs</th>
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
                    <div className="mx-auto max-w-xl space-y-2 text-sm">
                      {loadError ? (
                        <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-left text-amber-950 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-100">
                          {loadError}
                        </p>
                      ) : null}
                      <p>
                        No Jira issues found for this project (after story-type filter and fallback). Check the project key,
                        Jira credentials, and{" "}
                        <span className="font-mono">qa.flow.batch-story-issue-type-name</span> or{" "}
                        <span className="font-mono">qa.flow.batch-jql-suffix</span> in the backend.
                      </p>
                    </div>
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
                      <StatusBadge label={row.qaResult} />
                    </td>
                    <td className="px-4 py-3 tabular-nums text-slate-700 dark:text-slate-200">
                      {row.bugCount ?? 0}
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
