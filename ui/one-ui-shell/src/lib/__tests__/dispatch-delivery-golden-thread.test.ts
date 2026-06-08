import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("dispatch-delivery golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("UI route wires bounded-context client", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/operations/dispatch/page.tsx"), "utf8");
    expect(page).toContain("useDispatchDeliveries");
  });

  it("hook calls BFF sovereign proxy", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useDispatchOps.ts"), "utf8");
    expect(hook).toContain("/internal/v1/dispatch");
  });

  it("BFF controller exposes journey endpoints", () => {
    const controller = readFileSync(resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/DispatchController.java"), "utf8");
    expect(controller).toContain("/internal/v1/dispatch");
  });
});
