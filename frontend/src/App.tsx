import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AppModeProvider, useAppMode } from "./context/AppModeContext";
import { SentinelProvider } from "./context/SentinelContext";
import { DemoLayout } from "./layout/DemoLayout";
import { QaLayout } from "./layout/QaLayout";
import { ThemeProvider } from "./theme/ThemeProvider";
import { AiAgentPage } from "./pages/AiAgentPage";
import { BugReportsPage } from "./pages/BugReportsPage";
import { DashboardPage } from "./pages/DashboardPage";
import { LoanForm } from "./pages/LoanApp/LoanForm";
import { LoanList } from "./pages/LoanApp/LoanList";
import { QaRunsPage } from "./pages/QaRunsPage";

function ModeRoutes() {
  const { mode, switching } = useAppMode();

  return (
    <div
      className={`min-h-screen transition-opacity duration-200 ease-out ${
        switching ? "pointer-events-none opacity-50" : "opacity-100"
      }`}
    >
      <Routes key={mode}>
        {mode === "QA" ? (
          <Route element={<QaLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/qa-run" element={<QaRunsPage />} />
            <Route path="/bugs" element={<BugReportsPage />} />
            <Route path="/ai-agent" element={<AiAgentPage />} />
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/runs" element={<Navigate to="/qa-run" replace />} />
            <Route path="/agent" element={<Navigate to="/ai-agent" replace />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Route>
        ) : (
          <Route element={<DemoLayout />}>
            <Route path="/loan-form" element={<LoanForm />} />
            <Route path="/loan-list" element={<LoanList />} />
            <Route path="/loan" element={<Navigate to="/loan-form" replace />} />
            <Route path="/loan/list" element={<Navigate to="/loan-list" replace />} />
            <Route path="*" element={<Navigate to="/loan-form" replace />} />
          </Route>
        )}
      </Routes>
    </div>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <SentinelProvider>
        <BrowserRouter>
          <AppModeProvider>
            <ModeRoutes />
          </AppModeProvider>
        </BrowserRouter>
      </SentinelProvider>
    </ThemeProvider>
  );
}
