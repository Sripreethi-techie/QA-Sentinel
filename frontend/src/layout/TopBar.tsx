import { Bot, ChevronDown, Moon, Sun, User } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { fetchJiraStories, type JiraStoryListItem } from "../api/qaApi";
import { TICKET_ALL, useSentinel } from "../context/SentinelContext";
import { useAppMode } from "../context/AppModeContext";
import { StatusBadge } from "../components/StatusBadge";
import { useTheme } from "../theme/ThemeProvider";

const PROJECT_DEFAULT_ISSUE: Record<string, string> = {
  SCRUM: "SCRUM-1",
  DEMO: "DEMO-1",
  ACME: "ACME-1",
};

function truncate(s: string, max: number) {
  const t = s.trim();
  if (t.length <= max) return t;
  return `${t.slice(0, max - 1)}…`;
}

export function TopBar() {
  const {
    projectKey,
    setProjectKey,
    setIssueKey,
    ticketSelection,
    setTicketSelection,
    runAgent,
    running,
    health,
  } = useSentinel();
  const { resolved, toggle } = useTheme();
  const { setMode } = useAppMode();
  const [profileOpen, setProfileOpen] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);
  const [stories, setStories] = useState<JiraStoryListItem[]>([]);
  const [storiesError, setStoriesError] = useState<string | null>(null);

  const statusLabel = useMemo(() => {
    if (health === "degraded") return "At Risk" as const;
    return "Healthy" as const;
  }, [health]);

  useEffect(() => {
    if (!profileOpen) return;
    const onDown = (e: MouseEvent) => {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) {
        setProfileOpen(false);
      }
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, [profileOpen]);

  useEffect(() => {
    const pk = projectKey.trim();
    if (!pk) {
      setStories([]);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const { items, error } = await fetchJiraStories(pk);
        if (cancelled) return;
        setStories(items);
        setStoriesError(error?.trim() || null);
        setTicketSelection((cur) => {
          if (cur === TICKET_ALL) return TICKET_ALL;
          return items.some((s) => s.key === cur) ? cur : TICKET_ALL;
        });
      } catch (e) {
        if (cancelled) return;
        setStories([]);
        setStoriesError(e instanceof Error ? e.message : "Could not load stories");
        setTicketSelection(TICKET_ALL);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [projectKey, setTicketSelection]);

  const runLabel = useMemo(() => {
    if (running) return "Running…";
    if (ticketSelection === TICKET_ALL) return "Run Agent (all stories)";
    return `Run Agent (${ticketSelection})`;
  }, [running, ticketSelection]);

  const runTitle =
    ticketSelection === TICKET_ALL
      ? "Run Playwright QA for every user story in the selected Jira project (batch; failures file bugs when configured)"
      : `Run Playwright QA for only ${ticketSelection} (failures file bugs when configured)`;

  return (
    <header className="flex h-14 shrink-0 items-center justify-between gap-4 border-b border-slate-200 bg-white/90 px-4 backdrop-blur dark:border-slate-800 dark:bg-slate-950/90">
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-3">
        <label className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
          <span className="hidden sm:inline">Project</span>
          <select
            value={projectKey}
            onChange={(e) => {
              const pk = e.target.value;
              setProjectKey(pk);
              const def = PROJECT_DEFAULT_ISSUE[pk];
              if (def) setIssueKey(def);
              setTicketSelection(TICKET_ALL);
            }}
            className="rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm font-medium text-slate-900 shadow-sm dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
          >
            <option value="SCRUM">SCRUM</option>
            <option value="DEMO">DEMO</option>
            <option value="ACME">ACME</option>
          </select>
        </label>
        <label className="flex min-w-0 max-w-md flex-1 items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
          <span className="hidden shrink-0 sm:inline">Ticket</span>
          <select
            value={ticketSelection}
            onChange={(e) => {
              const v = e.target.value;
              setTicketSelection(v);
              if (v !== TICKET_ALL) {
                setIssueKey(v);
              }
            }}
            title={storiesError || undefined}
            className="w-full min-w-[8rem] max-w-md rounded-lg border border-slate-200 bg-white py-1.5 pl-2 pr-8 text-sm text-slate-900 shadow-sm dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
          >
            <option value={TICKET_ALL}>All</option>
            {stories.map((s) => (
              <option key={s.key} value={s.key} title={s.summary}>
                {s.key} — {truncate(s.summary, 48)}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          disabled={running}
          title={runTitle}
          onClick={() => void runAgent()}
          className="inline-flex shrink-0 items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 disabled:opacity-60 dark:bg-blue-500 dark:hover:bg-blue-600"
        >
          <Bot className="h-4 w-4" strokeWidth={2} />
          <span className="max-w-[11rem] truncate sm:max-w-[14rem]">{runLabel}</span>
        </button>
      </div>
      <div className="flex items-center gap-2 sm:gap-3">
        <div className="hidden items-center gap-2 sm:flex">
          <span className="text-xs text-slate-500 dark:text-slate-500">Status</span>
          <StatusBadge label={statusLabel} />
        </div>
        <button
          type="button"
          onClick={() => void toggle()}
          className="rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900"
          aria-label="Toggle theme"
        >
          {resolved === "dark" ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        </button>
        <div className="relative" ref={profileRef}>
          <button
            type="button"
            onClick={() => setProfileOpen((o) => !o)}
            className="flex items-center gap-1 rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900"
            aria-expanded={profileOpen}
            aria-haspopup="menu"
            aria-label="User menu"
          >
            <User className="h-4 w-4" strokeWidth={1.75} />
            <ChevronDown className="h-3.5 w-3.5 opacity-60" />
          </button>
          {profileOpen ? (
            <div
              role="menu"
              className="absolute right-0 z-50 mt-1 w-56 rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-700 dark:bg-slate-900"
            >
              <div className="border-b border-slate-100 px-3 py-2 dark:border-slate-800">
                <p className="text-xs font-medium text-slate-500 dark:text-slate-400">Signed in</p>
                <p className="truncate text-sm text-slate-900 dark:text-slate-100">QA operator</p>
              </div>
              <button
                type="button"
                role="menuitem"
                className="w-full px-3 py-2.5 text-left text-sm text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800"
                onClick={() => {
                  setProfileOpen(false);
                  setMode("DEMO");
                }}
              >
                Switch to Demo App
              </button>
            </div>
          ) : null}
        </div>
      </div>
    </header>
  );
}
