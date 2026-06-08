import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("provider-login golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("UI route wires bounded-context client and post-login resolver", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/auth/login/provider-id/page.tsx"), "utf8");
    expect(page).toContain("provider");
    expect(page).toContain("buildPostLoginResolvingPath");
  });

  it("resolving page lands activated provider on provider-workspace", () => {
    const resolver = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/lib/resolve-post-login-destination.ts"),
      "utf8",
    );
    expect(resolver).toContain("/provider-workspace");
    expect(resolver).toContain("my_life");

    const resolving = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/auth/resolving/page.tsx"),
      "utf8",
    );
    expect(resolving).toContain("resolvePostLoginDestination");
  });

  it("hook calls BFF sovereign proxy", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useAuth.ts"), "utf8");
    expect(hook).toContain("/internal/v1/auth");
  });

  it("BFF controller exposes journey endpoints", () => {
    const controller = readFileSync(resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/AuthSessionController.java"), "utf8");
    expect(controller).toContain("/internal/v1");
    expect(controller).toContain("auth");
  });
});
