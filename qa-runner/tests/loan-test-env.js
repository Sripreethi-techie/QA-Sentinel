/** Shared loan demo origin for Playwright specs (TARGET_URL / LOAN_ORIGIN from Java runner). */

export const RESULT_DIR = "test-results";

export function resolveLoanOrigin() {
  const raw =
    process.env.LOAN_ORIGIN?.trim() ||
    process.env.DEMO_LOAN_BASE_URL?.trim() ||
    process.env.TARGET_URL?.trim() ||
    "";
  if (!raw) return "http://localhost:9096";
  try {
    const u = new URL(/^https?:\/\//i.test(raw) ? raw : `http://${raw}`);
    const p = u.pathname.replace(/\/$/, "") || "/";
    if (p.toLowerCase().endsWith("/loan")) {
      u.pathname = "/";
    }
    return u.origin;
  } catch {
    return "http://localhost:9096";
  }
}

export const LOAN_ORIGIN = resolveLoanOrigin();
export const DEMO_LOAN_PAGE = `${LOAN_ORIGIN}/loan`;
