import type { QaStatus } from "../types/qa";

const styles: Record<string, string> = {
  PASS:
    "bg-emerald-500/15 text-emerald-700 ring-emerald-500/25 dark:text-emerald-400 dark:ring-emerald-400/30",
  FAIL: "bg-rose-500/15 text-rose-700 ring-rose-500/25 dark:text-rose-400 dark:ring-rose-400/30",
  ERROR: "bg-amber-500/15 text-amber-800 ring-amber-500/25 dark:text-amber-300 dark:ring-amber-400/25",
  RUNNING:
    "bg-blue-500/15 text-blue-700 ring-blue-500/25 dark:text-blue-400 dark:ring-blue-400/30",
  PENDING:
    "bg-slate-500/15 text-slate-700 ring-slate-500/20 dark:text-slate-300 dark:ring-slate-400/25",
  "—": "bg-slate-500/10 text-slate-600 ring-slate-500/15 dark:text-slate-400",
  Healthy:
    "bg-emerald-500/15 text-emerald-700 ring-emerald-500/25 dark:text-emerald-400 dark:ring-emerald-400/30",
  "At Risk":
    "bg-rose-500/15 text-rose-700 ring-rose-500/25 dark:text-rose-400 dark:ring-rose-400/30",
  Open: "bg-violet-500/15 text-violet-700 ring-violet-500/25 dark:text-violet-300 dark:ring-violet-400/25",
};

export function StatusBadge({
  label,
  className = "",
}: {
  label: QaStatus | "RUNNING" | "PENDING" | "—" | "Healthy" | "At Risk" | "Open" | string;
  className?: string;
}) {
  const key = label in styles ? label : "—";
  return (
    <span
      className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${styles[key] ?? styles["—"]} ${className}`}
    >
      {label}
    </span>
  );
}
