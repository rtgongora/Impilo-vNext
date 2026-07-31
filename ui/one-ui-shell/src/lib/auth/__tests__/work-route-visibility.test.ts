import { describe, expect, it } from "vitest";

import {
  resolveWorkRouteVisibility,
  type WorkRouteVisibilityInput,
} from "../work-route-visibility";

/** A clinician mid-load: identity reads in flight, so the heuristic still says citizen. */
const loadingClinician: WorkRouteVisibilityInput = {
  isCitizenOnly: true,
  identityLoading: true,
  routeBlockedForCitizen: true,
  hasContract: false,
  contractLoading: true,
  contractGrantsRoute: false,
};

describe("resolveWorkRouteVisibility", () => {
  it("waits while the identity reads are still in flight", () => {
    expect(resolveWorkRouteVisibility(loadingClinician)).toBe("wait");
  });

  it("still waits for identity after the contract has settled without granting", () => {
    // The regression this exists for: the contract query resolves fast (its local
    // fallback needs no network), the identity reads have not, and acting on that
    // gap bounced every /clinical deep link to /home on full page load.
    expect(
      resolveWorkRouteVisibility({
        ...loadingClinician,
        hasContract: true,
        contractLoading: false,
        contractGrantsRoute: false,
      }),
    ).toBe("wait");
  });

  it("blocks once both sources have settled and neither resolved a professional identity", () => {
    expect(
      resolveWorkRouteVisibility({
        ...loadingClinician,
        identityLoading: false,
        hasContract: true,
        contractLoading: false,
        contractGrantsRoute: false,
      }),
    ).toBe("block");
  });

  it("blocks when both sources failed rather than opening the surface", () => {
    // isLoading goes false on error too, so an outage lands here. Failing closed is
    // the point: a work surface must never open because a read could not be made.
    expect(
      resolveWorkRouteVisibility({
        ...loadingClinician,
        identityLoading: false,
        contractLoading: false,
      }),
    ).toBe("block");
  });

  it("lets a contract-granted route render even while identity is still loading", () => {
    expect(
      resolveWorkRouteVisibility({
        ...loadingClinician,
        hasContract: true,
        contractGrantsRoute: true,
      }),
    ).toBe("allow");
  });

  it("allows any route once a professional identity has resolved", () => {
    expect(
      resolveWorkRouteVisibility({ ...loadingClinician, isCitizenOnly: false }),
    ).toBe("allow");
  });

  it("allows citizen-safe paths without consulting either source", () => {
    expect(
      resolveWorkRouteVisibility({ ...loadingClinician, routeBlockedForCitizen: false }),
    ).toBe("allow");
  });
});
