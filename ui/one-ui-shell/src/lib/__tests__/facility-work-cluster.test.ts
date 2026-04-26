import { describe, expect, it } from "vitest";
import {
  FACILITY_WORK_CLUSTER_SUBCONTEXTS,
  FACILITY_WORK_SUBCONTEXT_NAV,
  isFacilityWorkClusterSubcontext,
} from "../facility-work-cluster";

describe("facility-work-cluster", () => {
  it("isFacilityWorkClusterSubcontext validates cluster keys", () => {
    expect(isFacilityWorkClusterSubcontext("triage")).toBe(true);
    expect(isFacilityWorkClusterSubcontext("bogus")).toBe(false);
  });

  it("every cluster subcontext has a real next route", () => {
    for (const key of FACILITY_WORK_CLUSTER_SUBCONTEXTS) {
      const nav = FACILITY_WORK_SUBCONTEXT_NAV[key];
      expect(nav.href).toMatch(/^\//);
      expect(nav.shortLabel.length).toBeGreaterThan(0);
      expect(nav.nextCue.length).toBeGreaterThan(0);
    }
  });
});
