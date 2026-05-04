# QA Sentinel runbook

## Jira bug parent vs link-only (`jira.bug-parent-story-enabled`)

- **`true`**: The backend sets Jira `parent` on the new bug to the user story key so the bug nests under the story in backlogs that allow **Bug → parent Story** (common on some team-managed / Next-gen projects).
- **`false`**: No parent field; the bug is still **linked** to the story using `jira.bug-link-type-name` (default `Relates`). Use this if issue create fails with a parent/hierarchy validation error.
- **Sub-task workflow**: If your site requires work under a story to be a **Sub-task** (not a top-level Bug), change `jira.bug-issue-type-name` to `Sub-task` and keep parent enabled only if your template allows Sub-task under Story—this is site-specific; verify in Jira **Create issue** for that project.

## Issue type names for batch/dashboard

Stories are loaded with JQL using `qa.flow.batch-story-issue-type-name`. The name must match Jira exactly (e.g. `Story` vs `User Story`).

- Call **`GET /api/v1/jira/projects/{PROJECT}/issue-types`** for a list of names, then copy the value into `qa.flow.batch-story-issue-type-name`.

## Persistence and idempotency

- **Run history** is appended to `~/.qa-sentinel/run-history.jsonl` by default (see `qa.sentinel.run-history-file`). Restarting the JVM reloads recent runs so **Bug Reports**, **screenshots**, and the **AI agent** still see past failures.
- **Duplicate bugs**: `qa.sentinel.bug-idempotency-enabled` stores a fingerprint of `(story, trace, failure text, failed step)` → Jira key so repeated batch runs do not file the same bug again.

## API key (production)

Set **`qa.sentinel.api-key`** and send header **`X-QA-Sentinel-Api-Key`** on all `/api/**` requests except **`/api/v1/health/**`** (for probes). Leave blank in local dev.

## Target URL hardening

With **`qa.flow.allow-private-env-urls=false`** (default in properties comments), URLs whose host is RFC1918 private (10.x, 192.168.x, 172.16–31.x) are rejected. **`localhost`** and **`127.0.0.1`** remain allowed for local QA.

## Batch throttling

- **`qa.flow.batch-max-stories`**: cap list size.
- **`qa.flow.batch-concurrency`**: parallel `runQaFlow` threads (default `1`). When `> 1`, **`qa.flow.batch-delay-ms-between-stories`** is ignored.
- **`qa.flow.batch-dry-run`**: list stories only; no Playwright/Jira bug steps.
