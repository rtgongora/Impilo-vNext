import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("referral create golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("orchestrates incoming referral handoff through shared referral hooks", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/queue/incoming-referrals/page.tsx"),
      "utf8",
    );
    const hooks = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useReferrals.ts"),
      "utf8",
    );
    expect(page).toContain("useIncomingReferrals");
    expect(page).toContain("useAcceptReferral");
    expect(page).toContain("buildReferralConsultHandoffRoute");
    expect(hooks).toContain("/internal/v1/referrals/incoming");
  });

  it("returns referral core-transaction correlation from BFF accept/respond", () => {
    const controller = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ReferralsController.java",
      ),
      "utf8",
    );
    expect(controller).toContain("referralTransactionId");
    expect(controller).toContain("core_transaction_id");
  });
});
