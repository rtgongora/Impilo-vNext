import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("blood-services-chain cross-service golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("MADI blood order UI routes register", () => {
    const routes = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/lib/routes.ts"), "utf8");
    expect(routes).toContain("/madi/orders");
    expect(routes).toContain("/madi/transfusion");
  });

  it("madiApi exposes blood order client methods", () => {
    const madi = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/lib/madi.ts"), "utf8");
    expect(madi).toContain('const BASE = "/internal/v1/madi"');
    expect(madi).toContain("createBloodOrder");
    expect(madi).toContain("startTransfusion");
  });

  it("patient journey hooks remain available for transfusion context", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/usePatients.ts"), "utf8");
    expect(hook).toContain("/internal/v1/patients");
  });
});
