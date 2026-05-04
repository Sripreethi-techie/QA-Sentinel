import type { ApiBugReport, IntegrationHealthPayload, QaBatchResult, QaResult } from "../types/qa";

const API = "/api";

async function readApiErrorMessage(res: Response): Promise<string> {
  const text = await res.text();
  const trimmed = text.trim();
  if (!trimmed) {
    return `HTTP ${res.status}`;
  }
  try {
    const j = JSON.parse(trimmed) as { error?: string; detail?: string; message?: string };
    const e = j.error ?? j.detail ?? j.message;
    if (e != null && String(e).trim()) {
      return String(e).trim();
    }
  } catch {
    /* not JSON */
  }
  return trimmed.length > 1200 ? `${trimmed.slice(0, 1197)}…` : trimmed;
}

export async function fetchHealth(): Promise<{ status: string; service: string }> {
  const r = await fetch(`${API}/v1/qa/health`);
  if (!r.ok) {
    throw new Error(await readApiErrorMessage(r));
  }
  return r.json();
}

export async function fetchIntegrationHealth(): Promise<IntegrationHealthPayload> {
  const r = await fetch(`${API}/v1/health/integrations`, { headers: { Accept: "application/json" } });
  if (!r.ok) {
    throw new Error(await readApiErrorMessage(r));
  }
  return r.json();
}

export async function runQaFlow(issueKey: string): Promise<QaResult> {
  const r = await fetch(`${API}/v1/qa/flow/${encodeURIComponent(issueKey)}`, {
    method: "POST",
    headers: { Accept: "application/json" },
  });
  if (!r.ok) {
    throw new Error(await readApiErrorMessage(r));
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
    throw new Error(await readApiErrorMessage(r));
  }
  return r.json();
}

/** GET /api/v1/jira/projects/{key}/stories — same JQL as batch QA. */
export interface JiraStoryListItem {
  key: string;
  summary: string;
  statusName?: string;
}

export async function fetchJiraStories(
  projectKey: string,
): Promise<{ items: JiraStoryListItem[]; error?: string }> {
  const r = await fetch(`${API}/v1/jira/projects/${encodeURIComponent(projectKey)}/stories`, {
    headers: { Accept: "application/json" },
  });
  if (!r.ok) {
    throw new Error(await readApiErrorMessage(r));
  }
  const data = (await r.json()) as { items?: JiraStoryListItem[]; error?: string } | JiraStoryListItem[];
  if (Array.isArray(data)) {
    return { items: data };
  }
  return { items: data.items ?? [], error: data.error };
}

export async function fetchBugReports(): Promise<ApiBugReport[]> {
  const r = await fetch(`${API}/v1/bugs`, { headers: { Accept: "application/json" } });
  if (!r.ok) {
    throw new Error(await readApiErrorMessage(r));
  }
  return r.json();
}
