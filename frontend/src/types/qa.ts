export type QaStatus = "PASS" | "FAIL" | "ERROR";

export interface TestStep {
  stepNumber: number;
  action: string;
  description: string;
  target?: string | null;
  value?: string | null;
  expected?: string | null;
}

export interface QaResult {
  status: string;
  stepsExecuted: TestStep[];
  failureReason: string;
  screenshotPath: string;
  traceId: string;
  jiraBugKey: string;
  failurePageUrl: string;
  failedStep: TestStep | null;
}

export interface QaBatchItem {
  issueKey: string;
  status: string;
  jiraBugKey: string;
  message: string;
}

export interface QaBatchResult {
  projectKey: string;
  items: QaBatchItem[];
}

export interface JiraIssueRow {
  key: string;
  summary: string;
  status: string;
  assignee: string;
  updated: string;
  /** Dashboard: derived from linked QA-filed bugs (pass = 0, fail = ≥1). */
  qaResult?: "pass" | "fail" | QaStatus | "—";
  bugCount?: number;
  bugLink?: string;
}

export interface SentinelRun {
  id: string;
  issueKey: string;
  startedAt: string;
  finishedAt?: string;
  status: QaStatus | "RUNNING" | "PENDING";
  stepsExecuted: TestStep[];
  failureReason: string;
  screenshotPath: string;
  jiraBugKey: string;
  traceId: string;
  failedStep: TestStep | null;
  logs: string[];
}

export interface BugReportItem {
  id: string;
  title: string;
  status: string;
  linkedIssue: string;
  screenshotHint: string;
  createdAt: string;
}

/** GET /api/v1/bugs — server-backed bug list from QA run history */
export interface ApiBugReport {
  id: string;
  title: string;
  jiraKey: string;
  jiraBrowseUrl: string;
  linkedIssueKey: string;
  runStatus: string;
  traceId: string;
  failureReason: string;
  screenshotPath: string;
  screenshotUrl: string | null;
  failedStepSummary: string;
  failedStep?: TestStep | null;
  recordedAt: string;
}

/** GET /api/v1/health/integrations */
export interface IntegrationHealthPayload {
  jiraConfigured: boolean;
  jiraReachable: boolean;
  groqConfigured: boolean;
  playwrightRunnerReady: boolean;
  lastStoryFetchError: string;
  detail: string;
}
