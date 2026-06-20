import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("identity-login-context cross-service golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("provider login UI reaches auth BFF", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/auth/login/provider-id/page.tsx"), "utf8");
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useAuth.ts"), "utf8");
    expect(page.length).toBeGreaterThan(0);
    expect(hook).toContain("/internal/v1/auth");
  });

  it("registry identity operations reach identity BFF", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useIdentityOperations.ts"), "utf8");
    const controller = readFileSync(
      resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/IdentityServicesController.java"),
      "utf8",
    );
    expect(hook).toContain("/internal/v1/identity");
    expect(controller).toContain("/internal/v1/identity");
  });

  it("BFF auth session composes trust plane", () => {
    const controller = readFileSync(
      resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/AuthSessionController.java"),
      "utf8",
    );
    expect(controller).toContain('@RequestMapping("/internal/v1")');
    expect(controller).toContain("/auth/login");
  });
});
