import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("payment billing claim golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("payer-ops page uses finance payer hooks and typed intent mutations", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/finance/payer-ops/page.tsx"),
      "utf8",
    );
    const hooks = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useFinancePayerOps.ts"),
      "utf8",
    );
    expect(page).toContain("usePayerOpsPaymentIntent");
    expect(page).toContain("usePayerOpsInitiateAttempt");
    expect(page).toContain("usePayerOpsClaimRemittance");
    expect(hooks).toContain("/internal/v1/finance/payer-ops");
  });

  it("BFF finance controllers expose billing and payer-ops composition", () => {
    const payerOps = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PayerOpsController.java",
      ),
      "utf8",
    );
    const finance = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/FinanceController.java",
      ),
      "utf8",
    );
    expect(payerOps).toContain("/internal/v1/finance/payer-ops");
    expect(finance).toContain("/internal/v1/finance");
  });
});
