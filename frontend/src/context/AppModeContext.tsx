import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useNavigate } from "react-router-dom";

export type AppMode = "QA" | "DEMO";

const STORAGE_KEY = "sentinel-app-mode";

type AppModeContextValue = {
  mode: AppMode;
  setMode: (mode: AppMode) => void;
  /** True briefly while switching modes (for UI transition). */
  switching: boolean;
};

const AppModeContext = createContext<AppModeContextValue | null>(null);

function readStoredMode(): AppMode {
  try {
    const s = localStorage.getItem(STORAGE_KEY);
    return s === "DEMO" ? "DEMO" : "QA";
  } catch {
    return "QA";
  }
}

/** Loan UI lives in demo mode; deep-linking here forces DEMO + persistence (tests, bookmarks). */
function initialModeFromLocation(): AppMode {
  if (typeof window === "undefined") return "QA";
  const path = window.location.pathname.replace(/\/$/, "") || "/";
  if (
    path === "/loan-form" ||
    path === "/loan-list" ||
    path === "/loan" ||
    path === "/loan/list"
  ) {
    try {
      localStorage.setItem(STORAGE_KEY, "DEMO");
    } catch {
      /* ignore */
    }
    return "DEMO";
  }
  return readStoredMode();
}

export function AppModeProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [mode, setModeState] = useState<AppMode>(initialModeFromLocation);
  const [switching, setSwitching] = useState(false);

  const setMode = useCallback(
    (next: AppMode) => {
      if (next === mode) return;
      setSwitching(true);
      try {
        localStorage.setItem(STORAGE_KEY, next);
      } catch {
        /* ignore quota / private mode */
      }
      setModeState(next);
      // Navigate immediately so URL never stays on a loan path while mode is QA (avoids fighting redirects).
      if (next === "QA") {
        navigate("/dashboard", { replace: true });
      } else {
        navigate("/loan-form", { replace: true });
      }
      window.setTimeout(() => setSwitching(false), 200);
    },
    [mode, navigate],
  );

  const value = useMemo(
    () => ({
      mode,
      setMode,
      switching,
    }),
    [mode, setMode, switching],
  );

  return <AppModeContext.Provider value={value}>{children}</AppModeContext.Provider>;
}

export function useAppMode() {
  const ctx = useContext(AppModeContext);
  if (!ctx) {
    throw new Error("useAppMode must be used within AppModeProvider");
  }
  return ctx;
}
