import { FileText, List } from "lucide-react";
import { NavLink, Outlet } from "react-router-dom";
import { useAppMode } from "../context/AppModeContext";

export function DemoLayout() {
  const { setMode } = useAppMode();

  return (
    <div className="flex min-h-screen flex-col bg-gradient-to-b from-slate-50 to-slate-100 dark:from-slate-950 dark:to-slate-900">
      <header className="border-b border-slate-200/80 bg-white/95 shadow-sm backdrop-blur dark:border-slate-800 dark:bg-slate-950/95">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between gap-4 px-4 sm:px-6">
          <div className="min-w-0">
            <h1 className="truncate text-sm font-semibold text-slate-900 dark:text-white sm:text-base">
              Loan Application Portal
            </h1>
            <p className="hidden text-xs text-slate-500 dark:text-slate-400 sm:block">
              Apply online — quick and simple
            </p>
          </div>
          <nav className="flex shrink-0 items-center gap-1 sm:gap-2">
            <NavLink
              to="/loan-form"
              className={({ isActive }) =>
                `inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                  isActive
                    ? "bg-emerald-600 text-white shadow-sm dark:bg-emerald-500"
                    : "text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
                }`
              }
            >
              <FileText className="h-4 w-4 shrink-0" strokeWidth={1.75} />
              <span className="hidden sm:inline">Apply</span>
            </NavLink>
            <NavLink
              to="/loan-list"
              className={({ isActive }) =>
                `inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                  isActive
                    ? "bg-emerald-600 text-white shadow-sm dark:bg-emerald-500"
                    : "text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
                }`
              }
            >
              <List className="h-4 w-4 shrink-0" strokeWidth={1.75} />
              <span className="hidden sm:inline">My applications</span>
            </NavLink>
            <button
              type="button"
              onClick={() => setMode("QA")}
              className="ml-1 rounded-lg border border-slate-200 px-3 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800 sm:text-sm"
            >
              Back to QA Sentinel
            </button>
          </nav>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8 sm:px-6">
        <Outlet />
      </main>
    </div>
  );
}
