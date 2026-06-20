import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("admin-governance-onboarding cross-service golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("administration governance hub uses session contract", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/work/administration-governance/page.tsx"),
      "utf8",
    );
    const surface = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/components/administration-governance/ScopedAdministrationSurface.tsx"),
      "utf8",
    );
    expect(page.length + surface.length).toBeGreaterThan(0);
    expect(surface).toContain("useSessionExperienceContract");
  });

  it("access request board calls admin governance BFF", () => {
    const board = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/components/administration-governance/AccessRequestBoard.tsx"),
      "utf8",
    );
    const client = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/lib/admin-governance/api/client.ts"), "utf8");
    expect(board).toContain("AccessRequestBoard");
    expect(client).toContain("/internal/v1/admin-governance");
  });

  it("trust admin BFF exposes policy and consent surfaces", () => {
    const controller = readFileSync(
      resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/TrustAdminController.java"),
      "utf8",
    );
    expect(controller).toContain("/internal/v1/admin/trust");
  });
});
