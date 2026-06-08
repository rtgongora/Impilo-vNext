import { describe, expect, it } from "vitest";
import { deriveClientIntakeBadges } from "./ClientIntakeStatusBadges";

describe("deriveClientIntakeBadges", () => {
  it("returns unverified when verification is not complete", () => {
    expect(
      deriveClientIntakeBadges({
        verificationStatus: "UNVERIFIED",
        latestRegistrationType: "SELF_INITIATED",
      }),
    ).toEqual(["unverified"]);
  });

  it("returns verified when verification status is VERIFIED", () => {
    expect(
      deriveClientIntakeBadges({
        verificationStatus: "VERIFIED",
        lifecycleStatus: "ACTIVE",
      }),
    ).toEqual(["verified"]);
  });

  it("adds facility-registered when registration came through a facility channel", () => {
    expect(
      deriveClientIntakeBadges({
        verificationStatus: "UNVERIFIED",
        latestRegistrationType: "FACILITY_REGISTRATION",
        latestRegistrationChannel: "FACILITY_DESK",
      }),
    ).toEqual(["unverified", "facility-registered"]);
  });

  it("treats issued Impilo ID as verified intake when issuance state is available", () => {
    expect(
      deriveClientIntakeBadges({
        verificationStatus: "PARTIALLY_VERIFIED",
        issuanceState: "ISSUED",
        latestRegistrationType: "FACILITY_REGISTRATION",
      }),
    ).toEqual(["verified", "facility-registered"]);
  });
});
