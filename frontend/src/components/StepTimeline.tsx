import { CheckCircle2, Circle, Loader2, XCircle } from "lucide-react";
import type { SentinelRun, TestStep } from "../types/qa";

export function StepTimeline({
  run,
  pulseIndex,
}: {
  run: SentinelRun | null;
  /** While running, synthetic index to highlight activity */
  pulseIndex?: number;
}) {
  const steps = run?.stepsExecuted ?? [];
  const failed = run?.failedStep;
  const running = run?.status === "RUNNING";

  if (steps.length === 0 && !running) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 p-6 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
        No steps yet. Start a QA run to see Groq / Playwright steps here.
      </div>
    );
  }

  if (steps.length === 0 && running) {
    return (
      <div className="space-y-2">
        {[0, 1, 2, 3].map((i) => (
          <StepRow
            key={i}
            step={{
              stepNumber: i + 1,
              action: "pending",
              description: i === (pulseIndex ?? 0) ? "Executing pipeline…" : "Queued",
            }}
            state={i === (pulseIndex ?? 0) ? "current" : "idle"}
          />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-1">
      {steps.map((s) => {
        const isFailedStep =
          failed != null &&
          failed.stepNumber === s.stepNumber &&
          (run?.status === "FAIL" || run?.status === "ERROR");
        const state = isFailedStep ? "fail" : "done";
        return <StepRow key={s.stepNumber} step={s} state={state} />;
      })}
    </div>
  );
}

function StepRow({
  step,
  state,
}: {
  step: TestStep;
  state: "current" | "done" | "fail" | "idle";
}) {
  const label = [step.action, step.target].filter(Boolean).join(" · ");
  return (
    <div
      className={`flex gap-3 rounded-lg border px-3 py-2.5 transition-colors ${
        state === "current"
          ? "border-blue-300 bg-blue-50/80 dark:border-blue-800 dark:bg-blue-950/40"
          : state === "fail"
            ? "border-rose-300 bg-rose-50/80 dark:border-rose-900 dark:bg-rose-950/30"
            : "border-transparent bg-slate-50/80 dark:bg-slate-900/50"
      }`}
    >
      <div className="mt-0.5 shrink-0">
        {state === "current" ? (
          <Loader2 className="h-4 w-4 animate-spin text-blue-600 dark:text-blue-400" />
        ) : state === "fail" ? (
          <XCircle className="h-4 w-4 text-rose-600 dark:text-rose-400" />
        ) : state === "done" ? (
          <CheckCircle2 className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
        ) : (
          <Circle className="h-4 w-4 text-slate-300 dark:text-slate-600" />
        )}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline gap-2">
          <span className="text-xs font-mono text-slate-400">#{step.stepNumber}</span>
          <span className="text-sm font-medium text-slate-800 dark:text-slate-100">
            {step.description || label || step.action}
          </span>
        </div>
        {label && step.description ? (
          <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{label}</p>
        ) : null}
        {step.expected ? (
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-500">Expected: {step.expected}</p>
        ) : null}
      </div>
    </div>
  );
}
