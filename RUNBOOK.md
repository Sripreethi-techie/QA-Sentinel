# Autonomous QA Sentinel — runbook

Monorepo layout:

- [`backend/`](backend/) — Spring Boot (API, QA orchestration, Groq, Jira, bundled SPA when built)
- [`frontend/`](frontend/) — Vite + React + Tailwind (optional dev server with `/api` proxy to Spring)
- [`qa-runner/`](qa-runner/) — Playwright (`npx playwright test`)

## Prerequisites

- JDK 21, Node 18+, npm
- Optional: Jira base URL + API token, Groq API key → copy `backend/src/main/resources/application-local.example.properties` to `application-local.properties` and fill values

## One-server demo (Spring serves UI + API)

1. Build the UI once:

   ```bash
   cd frontend
   npm install
   npm run build
   ```

2. Start the backend (copies `frontend/dist` into the classpath automatically when `dist` exists):

   ```bash
   cd backend
   ./gradlew bootRun
   ```

   Windows: `.\gradlew.bat bootRun`

3. Open **http://localhost:9096** (sidebar: Dashboard, QA Runs, Bug Reports, Loan App, AI Agent).

4. Install Playwright browsers once:

   ```bash
   cd qa-runner
   npm install
   ```

5. Run QA from the UI (**Run QA**) or:

   `POST http://localhost:9096/api/qa/run/SCRUM-1` (or any issue key in your Jira project)

   Playwright’s working directory is `../qa-runner` relative to `backend/` (see `qa.playwright.working-dir` in `application.properties`).

## Dev mode (hot-reload UI)

1. Terminal A: `cd backend && ./gradlew bootRun`
2. Terminal B: `cd frontend && npm run dev` → **http://localhost:5173**
3. Set `qa.flow.default-env-base-url=http://localhost:5173/loan` if you want the orchestrator’s default browser target to match Vite.

## Playwright

- **Green (intentional bugs asserted):** `npx playwright test tests/demo-loan-app.spec.js` — expects swapped list, invisible error banner, etc.
- **Red (correct behavior asserted):** `npx playwright test tests/dynamic-test.spec.js -g "demo loan application"` — three tests stay **failing** while API/UI bugs exist; they document “what should pass” after fixes.

Override base URL:

```bash
set LOAN_ORIGIN=http://localhost:9096
npx playwright test tests/demo-loan-app.spec.js
```

On Unix: `LOAN_ORIGIN=http://localhost:9096 npx playwright test ...`

## Deprecated

The old Express demo under `AgenticPMO/demo-loan-app/` is replaced by Spring `POST/GET /api/loan/*` and the Loan App routes in `frontend/`.
