import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: !process.env.PLAYWRIGHT_SKIP_WEBSERVER,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: process.env.PLAYWRIGHT_SKIP_WEBSERVER || process.env.PLAYWRIGHT_COMPOSE_E2E ? 60_000 : 15_000,
  workers: process.env.PLAYWRIGHT_SKIP_WEBSERVER || process.env.CI ? 1 : undefined,
  reporter: "list",
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",
    trace: "on-first-retry",
    actionTimeout: process.env.PLAYWRIGHT_SKIP_WEBSERVER ? 15_000 : 5_000,
  },
  projects: [
    {
      name: "chromium",
      testIgnore: "journeys/**",
      use: {
        ...devices["Desktop Chrome"],
        ...(process.env.PLAYWRIGHT_USE_SYSTEM_CHROME
          ? { channel: "chrome" as const }
          : process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
            ? { launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH } }
            : {}),
      },
    },
    {
      // Golden-journey persona walkthroughs — honest auth against a live estate.
      // Sequential (workers=1 via CLI), long timeouts, evidence-grade artefacts.
      name: "journeys",
      testMatch: "journeys/**/*.spec.ts",
      timeout: 180_000,
      use: {
        ...devices["Desktop Chrome"],
        video: "retain-on-failure",
        screenshot: "on",
        trace: "retain-on-failure",
        actionTimeout: 15_000,
        ...(process.env.PLAYWRIGHT_USE_SYSTEM_CHROME
          ? { channel: "chrome" as const }
          : process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
            ? { launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH } }
            : {}),
        // Hairpin NAT on the preview VM: the public hostname is unreachable from
        // inside, so map it to the local ingress (curl's --resolve, for Chromium).
        ...(process.env.PLAYWRIGHT_HOST_RESOLVER_RULES
          ? {
              launchOptions: {
                ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
                  ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH }
                  : {}),
                args: [`--host-resolver-rules=${process.env.PLAYWRIGHT_HOST_RESOLVER_RULES}`],
              },
            }
          : {}),
      },
    },
  ],
  webServer: process.env.PLAYWRIGHT_SKIP_WEBSERVER || process.env.PLAYWRIGHT_COMPOSE_E2E
    ? undefined
    : {
        command: "npm run dev",
        url: "http://localhost:3000",
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
});
