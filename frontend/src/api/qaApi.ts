import type { ApiBugReport, QaBatchResult, QaResult } from "../types/qa";

const API = "/api";

export async function fetchHealth(): Promise<{ status: string; service: string }> {
  const r = await fetch(`${API}/v1/qa/health`);
  if (!r.ok) throw new Error(`Health ${r.status}`);
  return r.json();
}

export async function runQaFlow(issueKey: string): Promise<QaResult> {
  const r = await fetch(`${API}/v1/qa/flow/${encodeURIComponent(issueKey)}`, {
    method: "POST",
    headers: { Accept: "application/json" },
  });
  if (!r.ok) {
    const t = await r.text();
    throw new Error(t || `Run failed (${r.status})`);
  }
  return r.json();
}

/** Runs QA for every story in the Jira project (server uses qa.flow.batch-* JQL). */
export async function runQaFlowAllStories(projectKey: string): Promise<QaBatchResult> {
  const r = await fetch(
    `${API}/v1/qa/flow/project/${encodeURIComponent(projectKey)}/stories/run`,
    {
      method: "POST",
      headers: { Accept: "application/json" },
    },
  );
  if (!r.ok) {
    const t = await r.text();
    throw new Error(t || `Batch run failed (${r.status})`);
  }
  return r.json();
}

export async function fetchBugReports(): Promise<ApiBugReport[]> {
  const r = await fetch(`${API}/v1/bugs`, { headers: { Accept: "application/json" } });
  if (!r.ok) {
    const t = await r.text();
    throw new Error(t || `Bug list failed (${r.status})`);
  }
  return r.json();
}
