import { readFileSync, writeFileSync, mkdirSync, existsSync, unlinkSync } from "node:fs";
import { join } from "node:path";
import { test, expect } from "@playwright/test";
import {
  assertValidation,
  validateInvalidApplyRejected,
  validateLoanListEnvelope,
  validateListReflectsSubmission,
  validateLoanListEmailColumnShape,
} from "./loan-api-validation.js";

const RESULT_DIR = "test-results";

/** Default: Spring Boot + bundled SPA ({@code /loan}). Override with LOAN_ORIGIN or TARGET_URL. See RUNBOOK.md */
function resolveLoanOrigin() {
  const raw =
    process.env.LOAN_ORIGIN?.trim() ||
    process.env.DEMO_LOAN_BASE_URL?.trim() ||
    process.env.TARGET_URL?.trim() ||
    "";
  if (!raw) return "http://localhost:9096";
  try {
    const u = new URL(/^https?:\/\//i.test(raw) ? raw : `http://${raw}`);
    const p = u.pathname.replace(/\/$/, "") || "/";
    if (p.endsWith("/loan")) {
      u.pathname = "/";
    }
    return u.origin;
  } catch {
    return "http://localhost:9096";
  }
}
const LOAN_ORIGIN = resolveLoanOrigin();
const DEMO_LOAN_PAGE = `${LOAN_ORIGIN}/loan`;

const FAILURE_SHOT = `${RESULT_DIR}/dynamic-failure.png`;
const FAILURE_EVIDENCE = `${RESULT_DIR}/dynamic-failure-evidence.json`;
const TRACE_ZIP = `${RESULT_DIR}/dynamic-failure-trace.zip`;
const CONSOLE_LOG = `${RESULT_DIR}/dynamic-failure-console.log`;
const SENTINEL = "[QA SENTINEL]";

function clearFailureEvidence() {
  for (const p of [FAILURE_EVIDENCE, TRACE_ZIP, CONSOLE_LOG]) {
    try {
      if (existsSync(p)) {
        unlinkSync(p);
      }
    } catch {
      /* ignore */
    }
  }
}

/** Sentinel run uses a fixed-path trace for the Java Jira uploader; project default stays retain-on-failure for other specs. */
async function startSentinelTrace(context) {
  try {
    await context.tracing.start({
      screenshots: true,
      snapshots: true,
      sources: true,
    });
  } catch {
    /* ignore */
  }
}

async function stopSentinelTraceDiscard(context) {
  try {
    await context.tracing.stop();
  } catch {
    /* ignore */
  }
}

async function stopSentinelTraceToZip(context, destPath) {
  try {
    await context.tracing.stop({ path: destPath });
  } catch {
    /* ignore */
  }
}

function writeConsoleLogFile(lines) {
  if (!lines?.length) {
    return false;
  }
  try {
    const tail = lines.slice(-800);
    writeFileSync(CONSOLE_LOG, tail.join("\n"), "utf8");
    return true;
  } catch {
    return false;
  }
}

/** Written for Java QA host to parse (page URL, failing step, errors, artifact paths). */
function writeFailureEvidence(page, failedStepIndex, failedStep, error, artifactMeta = {}) {
  try {
    const payload = {
      pageUrl: page.url(),
      failedStepIndex,
      failedStep: failedStep ?? null,
      errorMessage: String(error?.message || error || ""),
      timestampUtc: new Date().toISOString(),
      traceZipRelative: artifactMeta.traceZipRelative ?? null,
      consoleLogRelative: artifactMeta.consoleLogRelative ?? null,
    };
    writeFileSync(FAILURE_EVIDENCE, JSON.stringify(payload, null, 2), "utf8");
  } catch {
    /* ignore */
  }
}

function narrate(message) {
  console.log(SENTINEL);
  console.log(message);
}

function loadSteps() {
  const filePath = process.env.QA_STEPS_FILE?.trim();
  if (filePath && existsSync(filePath)) {
    try {
      const raw = readFileSync(filePath, "utf8");
      const arr = JSON.parse(raw);
      return Array.isArray(arr) ? arr : [];
    } catch {
      return [];
    }
  }
  const raw = process.env.QA_STEPS_JSON;
  if (!raw?.trim()) {
    return [];
  }
  try {
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}

/** Strip leading verb from Groq step description. */
function restAfterVerb(description, verb) {
  const d = (description || "").trim();
  const re = new RegExp(`^${verb}\\s+`, "i");
  return d.replace(re, "").trim() || d;
}

function firstUrl(text) {
  const m = (text || "").match(/https?:\/\/[^\s"'<>]+/i);
  return m ? m[0] : null;
}

/** Prefer structured `value` (URL), then URL-like `target`, then description. */
function pickNavigateUrl(step, fallbackUrl) {
  const v = step.value != null && String(step.value).trim() ? String(step.value).trim() : "";
  if (/^https?:\/\//i.test(v)) {
    return v;
  }
  const t = step.target != null && String(step.target).trim() ? String(step.target).trim() : "";
  if (/^https?:\/\//i.test(t)) {
    return t;
  }
  const fromTarget = firstUrl(t);
  if (fromTarget) {
    return fromTarget;
  }
  const fromDesc = firstUrl(step.description);
  if (fromDesc) {
    return fromDesc;
  }
  return fallbackUrl;
}

function parseTypeValue(description) {
  const rest = restAfterVerb(description || "", "type");
  const quoted = rest.match(/^["']([^"']+)["']/);
  if (quoted) {
    return quoted[1];
  }
  return rest.split(/\s+/).slice(0, 32).join(" ");
}

/** Field hint for type steps: label before "with" / "into" or whole rest. */
function typeFieldHint(description, explicitTarget) {
  if (explicitTarget && String(explicitTarget).trim()) {
    return String(explicitTarget).trim();
  }
  const rest = restAfterVerb(description || "", "type");
  const m = rest.match(/^(?:into|in)\s+(.+)/i) || rest.match(/^(.+?)\s+with\s+/i);
  return (m ? m[1] : rest).trim() || rest.trim();
}

function escapeForRegex(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function looksLikeStrictSelector(s) {
  const t = (s || "").trim();
  return /^[#.\[]|^css=|^xpath=|^role=/i.test(t) || t.startsWith("//");
}

function logPrimary() {
  console.log(SENTINEL);
  console.log("… Trying primary selector");
}

function logFallback() {
  console.log(SENTINEL);
  console.log("… Fallback strategy applied");
}

/**
 * Self-healing click: prefer visible text, then placeholder, then ARIA role, then first button.
 */
async function resilientClick(page, label) {
  const text = (label || "").trim();
  if (!text) {
    throw new Error("click: empty label / target");
  }
  const nameRe = new RegExp(escapeForRegex(text), "i");

  logPrimary();
  try {
    await page.getByText(text, { exact: false }).first().click({ timeout: 12_000 });
    return;
  } catch {
    logFallback();
  }

  try {
    await page.getByPlaceholder(nameRe).first().click({ timeout: 12_000 });
    return;
  } catch {
    logFallback();
  }

  try {
    await page.getByRole("button", { name: nameRe }).first().click({ timeout: 12_000 });
    return;
  } catch {
    logFallback();
  }

  try {
    await page.getByRole("link", { name: nameRe }).first().click({ timeout: 12_000 });
    return;
  } catch {
    logFallback();
  }

  try {
    await page.locator("button").first().click({ timeout: 12_000 });
  } catch (e) {
    throw new Error(`click: could not resolve "${text}" — ${e?.message || e}`);
  }
}

/**
 * Self-healing fill: label / placeholder / textbox role, then first visible field (no strict CSS first).
 */
async function resilientFill(page, fieldHint, value) {
  const hint = (fieldHint || "").trim();
  if (!value && value !== "") {
    throw new Error("type: empty value");
  }
  const nameRe = hint ? new RegExp(escapeForRegex(hint), "i") : /.+/;

  logPrimary();
  if (hint) {
    try {
      await page.getByLabel(nameRe).first().fill(value, { timeout: 12_000 });
      return;
    } catch {
      logFallback();
    }
    try {
      await page.getByPlaceholder(nameRe).first().fill(value, { timeout: 12_000 });
      return;
    } catch {
      logFallback();
    }
    try {
      await page.getByRole("textbox", { name: nameRe }).first().fill(value, { timeout: 12_000 });
      return;
    } catch {
      logFallback();
    }
  }

  try {
    await page.getByRole("textbox").first().fill(value, { timeout: 12_000 });
    return;
  } catch {
    logFallback();
  }

  try {
    await page.locator("input:visible, textarea:visible").first().fill(value, { timeout: 12_000 });
  } catch (e) {
    throw new Error(`type: could not fill — ${e?.message || e}`);
  }
}

/**
 * Backend HTTP step: target = path starting with /; value = optional JSON body string;
 * expected = HTTP code (200), 2xx, or substring in response body.
 */
async function runApiStep(request, step) {
  const action = (step.action || "").toLowerCase();
  const path = (step.target || "").trim();
  if (!path.startsWith("/")) {
    throw new Error(`api_*: target must be a path starting with / (got "${path}")`);
  }
  const url = `${LOAN_ORIGIN}${path}`;
  const rawBody = step.value != null ? String(step.value).trim() : "";
  let data;
  if (rawBody) {
    try {
      data = JSON.parse(rawBody);
    } catch {
      data = rawBody;
    }
  } else {
    data = undefined;
  }
  const timeout = 45_000;
  const failOnStatusCode = false;
  let res;
  switch (action) {
    case "api_get":
      res = await request.get(url, { timeout, failOnStatusCode });
      break;
    case "api_post":
      res = await request.post(url, {
        timeout,
        failOnStatusCode,
        data,
        headers: { "Content-Type": "application/json" },
      });
      break;
    case "api_put":
      res = await request.put(url, {
        timeout,
        failOnStatusCode,
        data,
        headers: { "Content-Type": "application/json" },
      });
      break;
    case "api_patch":
      res = await request.patch(url, {
        timeout,
        failOnStatusCode,
        data,
        headers: { "Content-Type": "application/json" },
      });
      break;
    case "api_delete":
      res = await request.delete(url, { timeout, failOnStatusCode });
      break;
    default:
      throw new Error(`Unknown API action: ${action}`);
  }
  const status = res.status();
  const text = await res.text();
  const exp = (step.expected || "").trim();
  if (!exp) {
    if (status >= 400) {
      throw new Error(`api: HTTP ${status} — ${text.slice(0, 600)}`);
    }
    return;
  }
  if (exp.toLowerCase() === "2xx") {
    if (status < 200 || status >= 300) {
      throw new Error(`api: expected 2xx, got ${status} — ${text.slice(0, 600)}`);
    }
    return;
  }
  if (/^\d{3}$/.test(exp)) {
    const want = parseInt(exp, 10);
    if (status !== want) {
      throw new Error(`api: expected HTTP ${want}, got ${status} — ${text.slice(0, 600)}`);
    }
    return;
  }
  if (!text.toLowerCase().includes(exp.toLowerCase())) {
    throw new Error(`api: response must contain "${exp}" (HTTP ${status}) — ${text.slice(0, 500)}`);
  }
}

async function postQaReport(passed, message, screenshotHint) {
  const url = process.env.QA_REPORT_URL?.trim();
  if (!url) {
    return;
  }
  const projectKey = process.env.QA_PROJECT_KEY?.trim() || "UNKNOWN";
  const issueKey = process.env.QA_ISSUE_KEY?.trim() || "UNKNOWN-0";
  const traceId = process.env.TRACE_ID?.trim() || "";
  const body = {
    projectKey,
    issueKey,
    traceId,
    passed,
    message: message || (passed ? "Playwright run completed." : "Playwright run failed."),
    screenshotHint: screenshotHint || null,
  };
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      console.error(SENTINEL, "Callback POST failed:", res.status);
    }
  } catch (e) {
    console.error(SENTINEL, "Callback POST error:", e?.message || e);
  }
}

async function runNavigate(page, step, fallbackUrl) {
  const url = pickNavigateUrl(step, fallbackUrl);
  await page.goto(url, { waitUntil: "domcontentloaded", timeout: 60_000 });
}

async function runClick(page, step) {
  const label =
    (step.target && String(step.target).trim()) ||
    restAfterVerb(step.description || "", "click");
  if (!label) {
    throw new Error("click: set target (or description) for the control label");
  }
  await resilientClick(page, label);
}

async function runType(page, step) {
  const value =
    step.value != null && String(step.value).length > 0
      ? String(step.value)
      : parseTypeValue(step.description || "");
  const rawTarget = step.target && String(step.target).trim();
  if (rawTarget && looksLikeStrictSelector(rawTarget)) {
    logPrimary();
    try {
      await page.locator(rawTarget).first().fill(value, { timeout: 12_000 });
      return;
    } catch {
      logFallback();
      const hint = rawTarget ? rawTarget : typeFieldHint(step.description, null);
      await resilientFill(page, hint || rawTarget, value);
    }
    return;
  }
  const hint = rawTarget ? rawTarget.trim() : typeFieldHint(step.description, null);
  await resilientFill(page, hint, value);
}

async function runVerify(page, step) {
  const target = step.target && String(step.target).trim();
  const expected =
    step.expected != null && String(step.expected).trim() ? String(step.expected).trim() : "";

  if (target && looksLikeStrictSelector(target)) {
    logPrimary();
    try {
      await expect(page.locator(target).first()).toBeVisible({ timeout: 15_000 });
      return;
    } catch {
      logFallback();
    }
  }

  const rest =
    expected ||
    (target && !looksLikeStrictSelector(target) ? target : "") ||
    restAfterVerb(step.description || "", "verify");
  if (!rest) {
    await expect(page.locator("body")).toBeVisible({ timeout: 30_000 });
    return;
  }

  logPrimary();
  try {
    await expect(page.getByText(rest, { exact: false }).first()).toBeVisible({
      timeout: 15_000,
    });
    return;
  } catch {
    logFallback();
  }

  try {
    await expect(page.getByPlaceholder(new RegExp(escapeForRegex(rest), "i")).first()).toBeVisible({
      timeout: 12_000,
    });
    return;
  } catch {
    logFallback();
  }

  try {
    await expect(
      page.getByRole("heading", { name: new RegExp(escapeForRegex(rest), "i") }).first(),
    ).toBeVisible({ timeout: 12_000 });
    return;
  } catch {
    logFallback();
  }

  await expect(page.locator("body")).toContainText(rest, { timeout: 30_000 });
}

// trace must be file-level (not inside describe) — see Playwright "forces a new worker" error.
// Sentinel uses manual context.tracing.* instead of retain-on-failure for this file.
test.use({ trace: "off" });

test.describe("QA Sentinel — dynamic Groq-driven runner", () => {
  test("dynamic QA steps (QA_STEPS_FILE | QA_STEPS_JSON)", async ({ page, context, request }) => {
    mkdirSync(RESULT_DIR, { recursive: true });
    clearFailureEvidence();
    const consoleBuffer = [];
    page.on("console", (msg) => {
      consoleBuffer.push(`[${msg.type()}] ${msg.text()}`);
    });
    await startSentinelTrace(context);

    const steps = loadSteps();
    const fallbackUrl = DEMO_LOAN_PAGE;

    async function failAndRethrow(e, failedStepIndex, failedStep) {
      let shot = "";
      try {
        await page.screenshot({ path: FAILURE_SHOT, fullPage: true });
        shot = FAILURE_SHOT;
      } catch {
        /* ignore */
      }
      await stopSentinelTraceToZip(context, TRACE_ZIP);
      const hadConsole = writeConsoleLogFile(consoleBuffer);
      writeFailureEvidence(page, failedStepIndex, failedStep, e, {
        traceZipRelative: TRACE_ZIP,
        consoleLogRelative: hadConsole ? CONSOLE_LOG : null,
      });
      await postQaReport(false, String(e?.message || e), shot);
      throw e;
    }

    if (steps.length === 0) {
      narrate("Step 3: Executing browser actions (smoke visit only — no JSON steps)");
      try {
        await page.goto(fallbackUrl, { waitUntil: "domcontentloaded", timeout: 45_000 });
        await expect(page.locator("body")).toBeVisible();
        await stopSentinelTraceDiscard(context);
        await postQaReport(true, "No steps JSON — smoke navigation only.", "");
      } catch (e) {
        await failAndRethrow(e, null, null);
      }
      return;
    }

    try {
      narrate("… Inside the browser: running the scripted actions");
      for (let i = 0; i < steps.length; i++) {
        const step = steps[i];
        try {
          const action = (step.action || "").toLowerCase();
          switch (action) {
            case "navigate":
              await runNavigate(page, step, fallbackUrl);
              break;
            case "click":
              await runClick(page, step);
              break;
            case "type":
              await runType(page, step);
              break;
            case "verify":
              await runVerify(page, step);
              break;
            case "api_get":
            case "api_post":
            case "api_put":
            case "api_patch":
            case "api_delete":
              await runApiStep(request, step);
              break;
            default:
              throw new Error(`Unknown action: ${action}`);
          }
        } catch (e) {
          await failAndRethrow(e, i, step);
        }
      }
      await stopSentinelTraceDiscard(context);
      await postQaReport(true, `Completed ${steps.length} step(s).`, "");
    } catch (e) {
      if (existsSync(FAILURE_EVIDENCE)) {
        throw e;
      }
      await failAndRethrow(e, null, null);
    }
  });
});

test.describe("demo loan application (assertions surface known bugs)", () => {
  /**
   * @param {import("@playwright/test").Page} page
   * @param {import("@playwright/test").TestInfo} testInfo
   * @param {string} fileName
   */
  async function captureStep(page, testInfo, fileName) {
    mkdirSync(RESULT_DIR, { recursive: true });
    const buf = await page.screenshot({ fullPage: true });
    writeFileSync(join(RESULT_DIR, fileName), buf);
    await testInfo.attach(fileName, { body: buf, contentType: "image/png" });
  }

  test("invalid form: client error must be readable (fails: broken error banner opacity)", async ({
    page,
  }, testInfo) => {
    await page.goto(`${DEMO_LOAN_PAGE}/`, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await captureStep(page, testInfo, "loan-ui-01-nav.png");

    await page.getByLabel(/Full name/i).fill("x");
    await page.getByLabel(/Email/i).fill("not-an-email");
    await page.getByLabel(/Loan amount/i).fill("-1");
    await captureStep(page, testInfo, "loan-ui-02-filled-invalid.png");

    await page.getByTestId("submit-loan").click();
    await page.locator(".error-banner--broken").waitFor({ state: "attached", timeout: 15_000 });
    await captureStep(page, testInfo, "loan-ui-03-after-submit.png");

    const banner = page.locator(".error-banner--broken");
    const opacity = await banner.evaluate((el) => parseFloat(getComputedStyle(el).opacity));
    // Correct UX: user-visible error (demo uses near-zero opacity on `.error-banner--broken`)
    expect(opacity, "error banner should be clearly visible").toBeGreaterThan(0.85);
    await expect(banner).toBeVisible();
  });

  test("API: invalid apply payload must be rejected (fails: always 200 + success)", async ({ request }) => {
    const res = await request.post(`${LOAN_ORIGIN}/api/loan/apply`, {
      data: { name: "", email: "bad", loanAmount: "0" },
    });
    const json = await res.json();
    assertValidation(validateInvalidApplyRejected(res.status(), json), "POST /api/loan/apply invalid");
  });

  test("API: list must reflect submission and use well-formed emails (fails: swap + amount)", async ({
    request,
  }) => {
    const suffix = Date.now();
    const submitted = {
      name: `APIVal ${suffix}`,
      email: `apival-${suffix}@example.com`,
      loanAmount: "8800",
    };
    const applyRes = await request.post(`${LOAN_ORIGIN}/api/loan/apply`, {
      data: submitted,
    });
    expect(applyRes.ok()).toBeTruthy();
    const applyJson = await applyRes.json();
    const id = /** @type {{ id?: string }} */ (applyJson).id;
    expect(id, "apply response should include id").toBeTruthy();

    const listRes = await request.get(`${LOAN_ORIGIN}/api/loan/list`);
    expect(listRes.ok()).toBeTruthy();
    const listJson = await listRes.json();
    assertValidation(validateLoanListEnvelope(listJson), "GET /api/loan/list envelope");
    const { applications } = /** @type {{ applications: unknown[] }} */ (listJson);
    const appId = /** @type {string} */ (id);
    const shape = validateLoanListEmailColumnShape(applications, appId);
    const match = validateListReflectsSubmission(applications, submitted, appId);
    assertValidation(
      {
        ok: shape.ok && match.ok,
        errors: [...shape.errors, ...match.errors],
      },
      "GET /api/loan/list semantics (shape + fidelity)",
    );
  });
});
