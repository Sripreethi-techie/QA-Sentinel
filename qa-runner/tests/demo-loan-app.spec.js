import { writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { test, expect } from "@playwright/test";
import {
  assertValidation,
  validateInvalidApplyRejected,
  validateLoanListEnvelope,
  validateListReflectsSubmission,
  validateLoanListEmailColumnShape,
} from "./loan-api-validation.js";
import { DEMO_LOAN_PAGE, LOAN_ORIGIN, RESULT_DIR } from "./loan-test-env.js";

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

test.describe("demo loan application (assertions surface known bugs)", () => {
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
