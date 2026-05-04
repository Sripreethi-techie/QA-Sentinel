import { ExternalLink, ImageOff, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { fetchBugReports } from "../api/qaApi";
import type { ApiBugReport, TestStep } from "../types/qa";

function BugScreenshot({
  screenshotUrl,
  screenshotPath,
  alt,
}: {
  screenshotUrl: string | null;
  screenshotPath: string;
  alt: string;
}) {
  const [showImg, setShowImg] = useState(!!screenshotUrl);
  if (!screenshotUrl || !showImg) {
    return (
      <div className="flex h-full w-full flex-col items-center justify-center gap-2 p-3 text-center text-xs text-slate-500 dark:text-slate-400">
        <ImageOff className="h-8 w-8 opacity-60" strokeWidth={1.25} />
        <span>
          {screenshotPath
            ? `Screenshot not available on the server (path hint: ${screenshotPath})`
            : "No screenshot path"}
        </span>
      </div>
    );
  }
  return (
    <img
      src={screenshotUrl}
      alt={alt}
      className="h-full w-full object-contain object-center"
      onError={() => setShowImg(false)}
    />
  );
}

function formatFailedStep(step: TestStep | null | undefined): string {
  if (!step) return "—";
  const parts = [
    `#${step.stepNumber}`,
    step.action,
    step.description,
    step.target ? `target: ${step.target}` : "",
    step.value ? `value: ${step.value}` : "",
    step.expected ? `expected: ${step.expected}` : "",
  ].filter(Boolean);
  return parts.join(" · ");
}

export function BugReportsPage() {
  const [reports, setReports] = useState<ApiBugReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchBugReports();
      setReports(Array.isArray(data) ? data : []);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setReports([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-900 dark:text-white">Bug reports</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            Jira bugs filed from failed QA runs (loaded from the Sentinel API). Screenshots are served when the
            Playwright artifact still exists under <span className="font-mono">qa-runner</span>.
          </p>
        </div>
        <button
          type="button"
          onClick={() => void load()}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          Refresh
        </button>
      </div>

      {error ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100">
          Could not load bug reports: {error}
        </div>
      ) : null}

      {loading && reports.length === 0 ? (
        <p className="text-sm text-slate-500 dark:text-slate-400">Loading…</p>
      ) : null}

      <ul className="space-y-4">
        {!loading && reports.length === 0 && !error ? (
          <li className="rounded-xl border border-dashed border-slate-200 bg-white/50 p-10 text-center text-sm text-slate-500 dark:border-slate-800 dark:bg-slate-900/30 dark:text-slate-400">
            No bugs recorded on the server yet. Run{" "}
            <span className="font-mono text-slate-700 dark:text-slate-300">POST /api/v1/qa/flow/SCRUM-1</span> (or use
            Run Agent) to produce failures; bugs are filed as real Jira issues when credentials are configured.
          </li>
        ) : null}
        {reports.map((b) => (
          <li
            key={b.id}
            id={`bug-${b.jiraKey}`}
            className="flex flex-col gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-card sm:flex-row sm:items-stretch dark:border-slate-800 dark:bg-slate-900/80 dark:shadow-card-dark"
          >
            <div className="flex min-w-0 flex-1 flex-col justify-center">
              <div className="flex flex-wrap items-center gap-2">
                <span
                  className={`rounded-md px-2 py-0.5 text-xs font-semibold uppercase tracking-wide ${
                    b.runStatus === "PASS"
                      ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-200"
                      : "bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-200"
                  }`}
                >
                  Run {b.runStatus}
                </span>
                <span className="font-mono text-xs text-blue-600 dark:text-blue-400">{b.jiraKey}</span>
              </div>
              <h2 className="mt-2 text-base font-medium leading-snug text-slate-900 dark:text-white">{b.title}</h2>
              <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                Linked story / issue{" "}
                <span className="font-mono text-slate-700 dark:text-slate-300">{b.linkedIssueKey}</span>
                {b.traceId ? (
                  <>
                    {" "}
                    · trace <span className="font-mono text-xs text-slate-400">{b.traceId}</span>
                  </>
                ) : null}
              </p>
              <div className="mt-3 rounded-lg border border-slate-100 bg-slate-50/80 p-3 dark:border-slate-800 dark:bg-slate-950/60">
                <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Failed step
                </p>
                <p className="mt-1 font-mono text-xs leading-relaxed text-slate-800 dark:text-slate-200">
                  {b.failedStepSummary || formatFailedStep(b.failedStep)}
                </p>
                {b.failedStep ? (
                  <pre className="mt-2 max-h-32 overflow-auto rounded border border-slate-200 bg-white p-2 text-[10px] text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
                    {JSON.stringify(b.failedStep, null, 2)}
                  </pre>
                ) : null}
              </div>
              {b.failureReason ? (
                <p className="mt-2 line-clamp-4 text-xs text-slate-600 dark:text-slate-300">{b.failureReason}</p>
              ) : null}
              <p className="mt-2 text-[10px] text-slate-400">Recorded {b.recordedAt}</p>
            </div>
            <div className="flex w-full shrink-0 flex-col gap-3 sm:w-72">
              <div className="relative flex aspect-video w-full overflow-hidden rounded-lg border border-slate-200 bg-slate-100 dark:border-slate-700 dark:bg-slate-950">
                <BugScreenshot
                  screenshotUrl={b.screenshotUrl}
                  screenshotPath={b.screenshotPath}
                  alt={`Failure screenshot for ${b.jiraKey}`}
                />
              </div>
              <a
                href={b.jiraBrowseUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white shadow hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-500"
              >
                View in Jira
                <ExternalLink className="h-4 w-4 shrink-0 opacity-90" />
              </a>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
