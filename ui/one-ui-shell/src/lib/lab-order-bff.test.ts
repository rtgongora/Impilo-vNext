import { describe, expect, it } from "vitest";
import { toLabOrderBffBody } from "./lab-order-bff";

describe("toLabOrderBffBody", () => {
  it("maps UI payload to typed BFF snake_case contract", () => {
    const body = toLabOrderBffBody({
      patientId: "patient-1",
      encounterId: "enc-9",
      testName: "Full Blood Count",
      testCode: "FBC",
      category: "LABORATORY",
      priority: "ROUTINE",
      clinicalNotes: "Fever workup",
      facilityId: "facility-1",
      orderedBy: "user-1",
      orderedByName: "Dr. Moyo",
      patientCpid: "CPID-ZW-00001",
      pctEncounterRef: "enc-9",
    });

    expect(body).toEqual({
      patient_id: "patient-1",
      encounter_id: "enc-9",
      test_name: "Full Blood Count",
      test_code: "FBC",
      category: "LABORATORY",
      priority: "ROUTINE",
      clinical_notes: "Fever workup",
      ordered_by: "user-1",
      ordered_by_name: "Dr. Moyo",
      facility_id: "facility-1",
      patient_cpid: "CPID-ZW-00001",
      pct_encounter_ref: "enc-9",
    });
  });

  it("requires ordered_by and test_name", () => {
    expect(() =>
      toLabOrderBffBody({
        patientId: "p",
        encounterId: "e",
        testName: "",
        testCode: "",
        category: "LABORATORY",
        priority: "ROUTINE",
        facilityId: "f",
        orderedBy: "user-1",
      }),
    ).toThrow(/test_name/);
  });
});
