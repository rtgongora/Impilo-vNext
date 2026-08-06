import { defineConfig, devices } from "@playwright/test";

/**
 * CP3 browser runtime proof against the live preview.
 *
 * The public hostname is only reachable through the in-cluster Traefik ingress IP from the
 * engineering host, so Chromium is told to resolve it there while keeping the real SNI/Host
 * (required for the __Host- cookie prefix and the correct Keycloak realm). Self-signed/preview
 * TLS is tolerated. This exercises the real BFF + Keycloak, not a local mock.
 */
const INGRESS_IP = process.env.PREVIEW_INGRESS_IP ?? "10.50.1.67";
const HOST = process.env.PREVIEW_HOST ?? "impilo.mohcc.gov.zw";

export default defineConfig({
  testDir: "./e2e",
  testMatch: /browser-preview-(security|authenticated|facility)\.spec\.ts/,
  timeout: 45_000,
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: `https://${HOST}`,
    ignoreHTTPSErrors: true,
  },
  projects: [
    {
      name: "chromium-preview",
      use: {
        ...devices["Desktop Chrome"],
        ignoreHTTPSErrors: true,
        launchOptions: {
          args: [
            `--host-resolver-rules=MAP ${HOST} ${INGRESS_IP}, MAP *.${HOST} ${INGRESS_IP}`,
            "--ignore-certificate-errors",
          ],
        },
      },
    },
  ],
});
