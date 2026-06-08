import { describe, expect, it } from "vitest";
import { buildPostCheckInRoute } from "../appointment-check-in-routing";

describe("buildPostCheckInRoute", () => {
  it("routes to encounter when encounter_id is present", () => {
    const route = buildPostCheckInRoute({
      patient_id: "patient-1",
      journey_id: "journey-42",
      encounter_id: "9001",
      core_transaction_id: "encounter-9001",
    });
    expect(route).toBe(
      "/ehr/patient-1/encounter/9001?transaction_id=encounter-9001&journey_id=journey-42",
    );
  });

  it("falls back to encounters list without encounter_id", () => {
    const route = buildPostCheckInRoute({
      patient_id: "patient-1",
      journey_id: "journey-42",
      core_transaction_id: "encounter-9001",
    });
    expect(route).toBe("/ehr/patient-1/encounters?transaction_id=encounter-9001&journey_id=journey-42");
  });

  it("returns null when patient or transaction missing", () => {
    expect(buildPostCheckInRoute({ core_transaction_id: "encounter-1" })).toBeNull();
    expect(buildPostCheckInRoute({ patient_id: "p1" })).toBeNull();
  });
});
