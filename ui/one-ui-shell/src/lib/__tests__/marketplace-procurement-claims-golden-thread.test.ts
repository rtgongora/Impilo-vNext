import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("marketplace-procurement-claims cross-service golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("marketplace UI reaches marketplace BFF", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/marketplace/page.tsx"), "utf8");
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useMarketplace.ts"), "utf8");
    expect(page).toContain("useMarketplace");
    expect(hook).toContain("/internal/v1/marketplace");
  });

  it("commerce flow cart uses msika-flow BFF", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useCommerceFlow.ts"), "utf8");
    expect(hook).toContain("/internal/v1/commerce");
  });

  it("payer-ops finance hooks cover claims and payments", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useFinancePayerOps.ts"), "utf8");
    expect(hook).toContain("/internal/v1/finance/payer-ops");
  });
});
