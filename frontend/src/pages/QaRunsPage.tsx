import { ImageOff } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { LogPanel } from "../components/LogPanel";
import { StatusBadge } from "../components/StatusBadge";
import { StepTimeline } from "../components/StepTimeline";
import { useSentinel } from "../context/SentinelContext";

function basename(path: string) {
  const parts = path.replace(/\\/g, "/").split("/");
  return parts[parts.length - 1] || path;
}

function runOutcomeStyles(status: string) {
  if (status === "PASS") {
    return "border-l-4 border-l-emerald-500 bg-emerald-50/40 dark:border-l-emerald-400 dark:bg-emerald-950/25";
  }
  if (status === "FAIL" || status === "ERROR") {
    return "border-l-4 border-l-rose-500 bg-rose-50/50 dark:border-l-rose-400 dark:bg-rose-950/30";
  }
  if (status === "RUNNING" || status === "PENDING") {
    return "border-l-4 border-l-amber-400 bg-amber-50/30 dark:border-l-amber-500 dark:bg-amber-950/20";
  }
  return "border-l-4 border-l-slate-300 bg-slate-50/40 dark:border-l-slate-600 dark:bg-slate-900/40";
}

export function QaRunsPage() {
  const { runs, lastError } = useSentinel();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [pulse, setPulse] = useState(0);

  useEffect(() => {
    if (runs[0]?.id && (selectedId == null || !runs.some((r) => r.id === selectedId))) {
      setSelectedId(runs[0].id);
    }
  }, [runs, selectedId]);

  const selected = useMemo(
    () => runs.find((r) => r.id === selectedId) ?? runs[0] ?? null,
    [runs, selectedId],
  );

  useEffect(() => {
    if (selected?.status !== "RUNNING") return;
    const t = setInterval(() => setPulse((p) => (p + 1) % 4), 700);
    return () => clearInterval(t);
  }, [selected?.status]);

  const showShot =
    selected &&
    selected.screenshotPath &&
    (selected.status === "FAIL" || selected.status === "ERROR");

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6 lg:flex-row">
      <section className="lg:w-64 lg:shrink-0">
        <h1 className="text-xl font-semibold text-slate-900 dark:text-white">QA runs</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Execution history and live orchestration output.
        </p>
        <ul className="mt-4 space-y-1">
          {runs.length === 0 ? (
            <li className="rounded-lg border border-dashed border-slate-200 p-4 text-sm text-slate-500 dark:border-slate-700">
              No runs yet. Use Run Agent in the header to batch all stories in the project.
            </li>
          ) : (
            runs.map((r) => (
              <li key={r.id}>
                <button
                  type="button"
                  onClick={() => setSelectedId(r.id)}
                  className={`flex w-full flex-col rounded-lg border border-transparent px-3 py-2 text-left text-sm transition-colors ${runOutcomeStyles(r.status)} ${
                    r.id === selected?.id
                      ? "ring-2 ring-blue-500/70 ring-offset-1 ring-offset-white dark:ring-blue-400/60 dark:ring-offset-slate-950"
                      : "hover:brightness-[0.99] dark:hover:brightness-110"
                  }`}
                >
                  <span className="font-mono text-xs text-slate-600 dark:text-slate-300">
                    {r.issueKey}
                  </span>
                  <span className="mt-1">
                    {r.status === "RUNNING" ? (
                      <StatusBadge label="RUNNING" />
                    ) : (
                      <StatusBadge label={r.status} />
                    )}
                  </span>
                  <span className="mt-1 text-[10px] text-slate-400">
                    {new Date(r.startedAt).toLocaleString()}
                  </span>
                </button>
              </li>
            ))
          )}
        </ul>
      </section>

      <section className="min-w-0 flex-1 space-y-4">
        {lastError ? (
          <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-200">
            {lastError}
          </div>
        ) : null}

        <div className="grid gap-4 lg:grid-cols-2">
          <div className="flex min-h-[280px] flex-col">
            <h2 className="mb-2 text-sm font-semibold text-slate-800 dark:text-slate-200">
              Live logs
            </h2>
            <LogPanel lines={selected?.logs ?? []} />
          </div>
          <div>
            <h2 className="mb-2 text-sm font-semibold text-slate-800 dark:text-slate-200">
              Steps executed
            </h2>
            <StepTimeline run={selected} pulseIndex={pulse} />
          </div>
        </div>

        {showShot ? (
          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-card dark:border-slate-800 dark:bg-slate-900/80 dark:shadow-card-dark">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-white">
              Screenshot on failure
            </h2>
            <p className="mt-1 font-mono text-xs text-slate-500">{selected.screenshotPath}</p>
            <div className="relative mt-3 flex aspect-video max-h-80 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-gradient-to-br from-slate-100 to-slate-200 dark:border-slate-700 dark:from-slate-900 dark:to-slate-800">
              <div className="flex flex-col items-center gap-2 text-slate-500 dark:text-slate-400">
                <ImageOff className="h-10 w-10 opacity-60" strokeWidth={1.25} />
                <span className="text-xs">
                  Preview not available (runner-local file: {basename(selected.screenshotPath)})
                </span>
              </div>
            </div>
          </div>
        ) : null}
      </section>
    </div>
  );
}
