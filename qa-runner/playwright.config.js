import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 60_000,
  use: {
    baseURL:
      process.env.TARGET_URL?.trim() ||
      "https://the-internet.herokuapp.com/login",
    // Production-style failure artifacts (trace viewer: npx playwright show-trace <file>)
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },
});
