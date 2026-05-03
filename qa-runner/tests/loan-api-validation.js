/**
 * Contract checks for the demo loan API (Spring Boot {@code POST/GET /api/loan/*}).
 * These encode *correct* behavior; they fail against the intentional bugs
 * (always success on apply, swapped list fields, garbled amounts).
 */

/** @typedef {{ name: string; email: string; loanAmount: string }} LoanSubmit */

/**
 * Invalid payloads must be rejected (4xx or success: false).
 * Demo bug: POST always returns 200 + { success: true }.
 *
 * @param {number} status HTTP status
 * @param {unknown} json parsed body
 * @returns {{ ok: boolean; errors: string[] }}
 */
export function validateInvalidApplyRejected(status, json) {
  const errors = [];
  const body = json && typeof json === "object" ? json : {};
  const success = /** @type {{ success?: boolean }} */ (body).success;

  if (status >= 200 && status < 300 && success === true) {
    errors.push("Invalid apply must not return 2xx with success:true (server should reject bad input)");
  }
  return { ok: errors.length === 0, errors };
}

/**
 * GET /api/loan/list must return { applications: LoanRow[] } with stable keys.
 * @param {unknown} json
 * @returns {{ ok: boolean; errors: string[] }}
 */
export function validateLoanListEnvelope(json) {
  const errors = [];
  if (!json || typeof json !== "object") {
    return { ok: false, errors: ["Response must be a JSON object"] };
  }
  const apps = /** @type {{ applications?: unknown }} */ (json).applications;
  if (!Array.isArray(apps)) {
    errors.push("Missing or invalid `applications` array");
    return { ok: false, errors };
  }
  for (let i = 0; i < apps.length; i++) {
    const row = apps[i];
    if (!row || typeof row !== "object") {
      errors.push(`applications[${i}] must be an object`);
      continue;
    }
    for (const key of ["id", "name", "email", "loanAmount"]) {
      if (!(key in row)) {
        errors.push(`applications[${i}] missing "${key}"`);
      }
    }
  }
  return { ok: errors.length === 0, errors };
}

/**
 * After a successful submit, list rows must match what was stored (no swap, exact amount).
 * Demo bugs: name/email swapped; loanAmount gets "00" appended.
 *
 * @param {unknown[]} applications
 * @param {LoanSubmit} submitted
 * @param {string} applicationId from POST /api/loan/apply (`id` field)
 * @returns {{ ok: boolean; errors: string[] }}
 */
export function validateListReflectsSubmission(applications, submitted, applicationId) {
  const errors = [];
  if (!Array.isArray(applications)) {
    return { ok: false, errors: ["applications must be an array"] };
  }
  const row = applications.find(
    (a) => a && typeof a === "object" && /** @type {{ id?: string }} */ (a).id === applicationId,
  );
  if (!row || typeof row !== "object") {
    return { ok: false, errors: [`Could not find application id="${applicationId}" in list`] };
  }
  const r = /** @type {{ name?: string; email?: string; loanAmount?: string }} */ (row);
  if (r.name !== submitted.name) {
    errors.push(`name mismatch: expected "${submitted.name}", got "${r.name}"`);
  }
  if (r.email !== submitted.email) {
    errors.push(`email mismatch: expected "${submitted.email}", got "${r.email}"`);
  }
  if (r.loanAmount !== submitted.loanAmount) {
    errors.push(`loanAmount mismatch: expected "${submitted.loanAmount}", got "${r.loanAmount}"`);
  }
  return { ok: errors.length === 0, errors };
}

/**
 * Each `email` field should look like an email (list API is not swapping columns).
 * Fails on the demo bug where `email` holds the applicant's name.
 *
 * @param {unknown[]} applications
 * @param {string} [applicationId] when set, only this row is checked (clearer failures)
 * @returns {{ ok: boolean; errors: string[] }}
 */
export function validateLoanListEmailColumnShape(applications, applicationId) {
  const errors = [];
  if (!Array.isArray(applications)) {
    return { ok: false, errors: ["applications must be an array"] };
  }
  const rows =
    applicationId != null && String(applicationId).trim() !== ""
      ? applications.filter(
          (a) => a && typeof a === "object" && /** @type {{ id?: string }} */ (a).id === applicationId,
        )
      : applications;
  if (applicationId != null && String(applicationId).trim() !== "" && rows.length === 0) {
    return { ok: false, errors: [`no list row with id "${applicationId}"`] };
  }
  const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i];
    if (!row || typeof row !== "object") {
      errors.push(`applications[${i}] must be an object`);
      continue;
    }
    const email = String(/** @type {{ email?: string }} */ (row).email ?? "").trim();
    if (email && email !== "(unknown)" && !emailRe.test(email)) {
      errors.push(
        `applications[].email must be a valid email address; got "${email}" (possible name/email swap)`,
      );
    }
  }
  return { ok: errors.length === 0, errors };
}

/**
 * Playwright-style assertion helper: throws with joined errors if invalid.
 * @param {{ ok: boolean; errors: string[] }} result
 * @param {string} [label]
 */
export function assertValidation(result, label = "API validation") {
  if (result.ok) {
    return;
  }
  throw new Error(`${label} failed:\n${result.errors.join("\n")}`);
}
