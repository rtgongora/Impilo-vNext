import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("citizen-onboarding golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("UI route wires bounded-context client", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/auth/register/page.tsx"), "utf8");
    expect(page).toContain("apiClient");
    expect(page).toContain("register");
  });

  it("BFF controller exposes journey endpoints", () => {
    const controller = readFileSync(resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/AuthSessionController.java"), "utf8");
    expect(controller).toContain("/internal/v1");
    expect(controller).toContain("register");
  });
});
