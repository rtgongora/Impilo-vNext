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
      use: {
        ...devices["Desktop Chrome"],
        ...(process.env.PLAYWRIGHT_USE_SYSTEM_CHROME
          ? { channel: "chrome" as const }
          : process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
            ? { launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH } }
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
