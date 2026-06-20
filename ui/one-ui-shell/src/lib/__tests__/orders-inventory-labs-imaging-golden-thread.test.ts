import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("orders-inventory-labs-imaging cross-service golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("lab orders UI reaches OROS BFF", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useLabOrders.ts"), "utf8");
    expect(hook).toContain("/internal/v1/lab-orders");
  });

  it("pharmacy dispense hooks reach pharmacy BFF", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/usePharmacy.ts"), "utf8");
    expect(hook).toContain("/internal/v1/pharmacy");
  });

  it("imaging hooks reach imaging BFF", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useImaging.ts"), "utf8");
    expect(hook).toContain("/internal/v1/imaging");
  });
});
