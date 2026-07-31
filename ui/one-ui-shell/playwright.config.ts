import { defineConfig, devices } from "@playwright/test";

const prodBuild = process.env.PLAYWRIGHT_PROD_BUILD === "1";
const skipWebServer =
  !!process.env.PLAYWRIGHT_SKIP_WEBSERVER || !!process.env.PLAYWRIGHT_COMPOSE_E2E;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: !process.env.PLAYWRIGHT_SKIP_WEBSERVER,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: process.env.PLAYWRIGHT_SKIP_WEBSERVER || process.env.PLAYWRIGHT_COMPOSE_E2E || prodBuild
    ? 60_000
    : 15_000,
  workers: process.env.PLAYWRIGHT_SKIP_WEBSERVER || process.env.CI || prodBuild ? 1 : undefined,
  reporter: "list",
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",
    trace: "on-first-retry",
    actionTimeout: process.env.PLAYWRIGHT_SKIP_WEBSERVER || prodBuild ? 15_000 : 5_000,
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
  webServer: skipWebServer
    ? undefined
    : prodBuild
      ? {
          // Service workers are unreliable under `next dev`. W16b specs require a prod server.
          // Gateway/BFF URLs are required at build time (rewrites bake them in); the W16b
          // specs intercept `/internal/v1/**` in the browser, so a live BFF is not required.
          command:
            "API_GATEWAY_URL=http://127.0.0.1:8160 BFF_URL=http://127.0.0.1:8160 NEXT_PUBLIC_API_GATEWAY_URL=http://127.0.0.1:8160 NEXT_PUBLIC_BFF_URL=http://127.0.0.1:8160 npm run build && npm run start -- --port 3000",
          url: "http://localhost:3000",
          reuseExistingServer: !process.env.CI,
          timeout: 600_000,
        }
      : {
          command: "npm run dev",
          url: "http://localhost:3000",
          reuseExistingServer: !process.env.CI,
          timeout: 120_000,
        },
});
