import type { LucideIcon } from "lucide-react";

export function KpiCard({
  title,
  value,
  hint,
  icon: Icon,
  accent = "slate",
}: {
  title: string;
  value: string | number;
  hint?: string;
  icon: LucideIcon;
  accent?: "slate" | "blue" | "rose" | "emerald" | "violet";
}) {
  const ring = {
    slate: "ring-slate-200 dark:ring-slate-700",
    blue: "ring-blue-200 dark:ring-blue-900/50",
    rose: "ring-rose-200 dark:ring-rose-900/50",
    emerald: "ring-emerald-200 dark:ring-emerald-900/50",
    violet: "ring-violet-200 dark:ring-violet-900/50",
  }[accent];
  const iconBg = {
    slate: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300",
    blue: "bg-blue-100 text-blue-600 dark:bg-blue-950 dark:text-blue-400",
    rose: "bg-rose-100 text-rose-600 dark:bg-rose-950 dark:text-rose-400",
    emerald: "bg-emerald-100 text-emerald-600 dark:bg-emerald-950 dark:text-emerald-400",
    violet: "bg-violet-100 text-violet-600 dark:bg-violet-950 dark:text-violet-400",
  }[accent];

  return (
    <div
      className={`rounded-xl border border-slate-200/80 bg-white p-5 shadow-card dark:border-slate-800 dark:bg-slate-900/80 dark:shadow-card-dark ${ring} ring-1`}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-medium text-slate-500 dark:text-slate-400">{title}</p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 dark:text-white">
            {value}
          </p>
          {hint ? (
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-500">{hint}</p>
          ) : null}
        </div>
        <div className={`rounded-lg p-2.5 ${iconBg}`}>
          <Icon className="h-5 w-5" strokeWidth={1.75} />
        </div>
      </div>
    </div>
  );
}
