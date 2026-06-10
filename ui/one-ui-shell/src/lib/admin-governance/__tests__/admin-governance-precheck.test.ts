import { describe, expect, it } from "vitest";
import { buildOnboardingPrecheck } from "@/lib/admin-governance/api/onboardingApi";

describe("admin governance precheck", () => {
  it("public-sector worker onboarding uses HSC-aware action", () => {
    const precheck = buildOnboardingPrecheck("public-sector-worker", { targetProviderWorkerId: "P-1" });
    expect(precheck.requestedAction).toBe("ONBOARD_PUBLIC_SECTOR_WORKER");
    expect(precheck.targetSubjectId).toBe("P-1");
  });

  it("regulator onboarding stays within council scope action", () => {
    const precheck = buildOnboardingPrecheck("regulator-user", { organisationId: "council-mdpc" });
    expect(precheck.requestedAction).toBe("ONBOARD_REGULATOR_USER");
    expect(precheck.targetOrganisationId).toBe("council-mdpc");
  });

  it("external partner onboarding requires scoped fields", () => {
    const precheck = buildOnboardingPrecheck("external-partner-user", {
      requestedDataScope: "aggregate_only",
      requestedDurationDays: 30,
    });
    expect(precheck.requestedDataScope).toBe("aggregate_only");
    expect(precheck.requestedDurationDays).toBe(30);
  });
});
