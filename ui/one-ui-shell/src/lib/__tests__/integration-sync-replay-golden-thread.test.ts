import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("integration-sync-replay golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("UI route wires bounded-context client", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/admin/integration-status/page.tsx"), "utf8");
    expect(page).toContain("useIntegrationHub");
  });

  it("hook calls BFF sovereign proxy", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useIntegrationHub.ts"), "utf8");
    expect(hook).toContain("/internal/v1/integration");
  });

  it("BFF controller exposes journey endpoints", () => {
    const controller = readFileSync(resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/IntegrationHubController.java"), "utf8");
    expect(controller).toContain("/internal/v1/integration");
  });
});
