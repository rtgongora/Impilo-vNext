import { describe, expect, it } from "vitest";
import {
  exitedEnrolments,
  groupProblems,
  openEnrolments,
  programmeLabel,
} from "../../features/medicine/medicine-summary";
import type { ConditionResource, ProgrammeEnrolment } from "../../services/medicineService";

describe("medicine-summary", () => {
  it("groups active, remission, and inactive problems", () => {
    const conditions: ConditionResource[] = [
      {
        id: "1",
        type: "condition",
        attributes: { patientId: "p", conditionName: "HTN", icdCode: "I10", clinicalStatus: "ACTIVE" },
      },
      {
        id: "2",
        type: "condition",
        attributes: { patientId: "p", conditionName: "TB", icdCode: null, clinicalStatus: "REMISSION" },
      },
      {
        id: "3",
        type: "condition",
        attributes: { patientId: "p", conditionName: "Old injury", icdCode: null, clinicalStatus: "RESOLVED" },
      },
    ];
    const groups = groupProblems(conditions);
    expect(groups.active).toHaveLength(1);
    expect(groups.remission).toHaveLength(1);
    expect(groups.inactive).toHaveLength(1);
  });

  it("splits open vs exited programme enrolments", () => {
    const enrolments: ProgrammeEnrolment[] = [
      { enrolment_id: "a", subject_cpid: "p", programme: "HIV_CARE", status: "ON_TREATMENT", exit_reason: null, enrolled_on: "2026-01-01" },
      { enrolment_id: "b", subject_cpid: "p", programme: "TB_TREATMENT", status: "LOST_TO_FOLLOW_UP", exit_reason: "LOST", enrolled_on: "2025-01-01" },
    ];
    expect(openEnrolments(enrolments)).toHaveLength(1);
    expect(exitedEnrolments(enrolments)).toHaveLength(1);
    expect(programmeLabel("HIV_CARE")).toBe("HIV care");
  });
});

describe("medicineService routes", () => {
  it("documents BFF paths used by mobile mirrors", () => {
    expect("/internal/v1/conditions").toContain("/internal/v1/conditions");
    expect("/internal/v1/medicine/cds/cvd-risk/evaluate").toContain("medicine/cds");
    expect("/internal/v1/programmes/register").toContain("programmes/register");
    expect("/internal/v1/clerking/visit-attestations").toContain("clerking");
  });
});
