# Autonomous QA Sentinel

Hackathon prototype: **Jira → Groq → test steps → Playwright → failure → Jira Bug**, plus an **AI agent** chat and an intentionally buggy **loan demo**.

## Layout

| Path | Role |
|------|------|
| [`backend/`](backend/) | Spring Boot — QA orchestration, Groq, Jira, Playwright runner integration, loan API, optional bundled SPA |
| [`frontend/`](frontend/) | React (Vite + Tailwind) — dashboard, QA runs, bugs, loan app, AI agent |
| [`qa-runner/`](qa-runner/) | Playwright tests (`dynamic-test.spec.js`, `demo-loan-app.spec.js`) |

See **[RUNBOOK.md](RUNBOOK.md)** for start commands and environment variables.

The previous all-in-one folder **`AgenticPMO/`** is deprecated; active code lives at the repo root as above.
