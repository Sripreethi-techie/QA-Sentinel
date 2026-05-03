import { test, expect } from "@playwright/test";

/**
 * Targets the demo loan UI + Spring API (Vite proxies /api to backend).
 * @see RUNBOOK.md at repo root
 */
function loanOrigin() {
  const raw =
    process.env.LOAN_ORIGIN?.trim() || process.env.DEMO_LOAN_BASE_URL?.trim() || process.env.TARGET_URL?.trim() || "";
  if (!raw) return "http://localhost:9096";
  try {
    const u = new URL(/^https?:\/\//i.test(raw) ? raw : `http://${raw}`);
    const p = u.pathname.replace(/\/$/, "") || "/";
    if (p.endsWith("/loan")) u.pathname = "/";
    return u.origin;
  } catch {
    return "http://localhost:9096";
  }
}
const LOAN_ORIGIN = loanOrigin();
const LOAN_PAGE = `${LOAN_ORIGIN}/loan`;

test.describe.configure({ mode: "serial" });

test.describe("demo-loan-app (intentional bugs for QA)", () => {
  test("POST /api/loan/apply succeeds with empty body (API bug)", async ({ request }) => {
    const res = await request.post(`${LOAN_ORIGIN}/api/loan/apply`, {
      data: { name: "", email: "", loanAmount: "" },
    });
    expect(res.ok()).toBeTruthy();
    const json = await res.json();
    expect(json.success).toBe(true);
  });

  test("GET /api/loan/list returns swapped name/email (API bug)", async ({ request }) => {
    const suffix = Date.now();
    const email = `loan-pw-${suffix}@test.com`;
    await request.post(`${LOAN_ORIGIN}/api/loan/apply`, {
      data: { name: "PlaywrightAlice", email, loanAmount: "12000" },
    });
    const res = await request.get(`${LOAN_ORIGIN}/api/loan/list`);
    expect(res.ok()).toBeTruthy();
    const { applications } = await res.json();
    const row = applications.find((a) => a.name === email);
    expect(row, "expected swapped mapping: name field carries email value").toBeTruthy();
    expect(row.email).toBe("PlaywrightAlice");
    expect(row.loanAmount).toBe("1200000");
  });

  test("UI: empty submit still shows success (relies on API bug)", async ({ page }) => {
    await page.goto(`${LOAN_PAGE}/`);
    await page.getByTestId("submit-loan").click();
    await expect(page.getByTestId("submit-toast")).toContainText("Application received");
  });

  test("UI: fill form, list shows wrong columns vs entered values", async ({ page }) => {
    await page.goto(`${LOAN_PAGE}/`);
    await page.getByLabel(/Full name/i).fill("Bob Tester");
    await page.getByLabel(/Email/i).fill("bob-ui@example.com");
    await page.getByLabel(/Loan amount/i).fill("7500");
    await page.getByTestId("submit-loan").click();
    await page.waitForURL("**/loan-list");
    await expect(page.getByTestId("loan-table")).toBeVisible();
    const row = page.getByTestId("loan-row").filter({ hasText: "bob-ui@example.com" }).first();
    await expect(row).toBeVisible();
    await expect(row.getByTestId("cell-name")).toContainText("bob-ui@example.com");
    await expect(row.getByTestId("cell-email")).toContainText("Bob Tester");
  });

  test("UI: validation error is not visible (broken error styles)", async ({ page }) => {
    await page.goto(`${LOAN_PAGE}/`);
    await page.getByLabel(/Full name/i).fill("x");
    await page.getByLabel(/Email/i).fill("not-an-email");
    await page.getByLabel(/Loan amount/i).fill("-1");
    await page.getByTestId("submit-loan").click();
    const banner = page.locator(".error-banner--broken");
    await expect(banner).toBeAttached();
    const opacity = await banner.evaluate((el) => parseFloat(getComputedStyle(el).opacity));
    expect(opacity).toBeLessThan(0.1);
  });

  test("UI: submit button partially off-screen on narrow viewport", async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 640 });
    await page.goto(`${LOAN_PAGE}/`);
    const box = await page.getByTestId("submit-loan").boundingBox();
    expect(box).toBeTruthy();
    const vw = page.viewportSize()?.width ?? 360;
    expect(box.x + box.width).toBeGreaterThan(vw * 0.85);
  });
});
