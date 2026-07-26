import { describe, expect, it } from "vitest";
import {
  CLINICAL_ASSERTION_OPTIONS,
  EXAM_FINDING_OPTIONS,
  assertsPresent,
  isAnsweredAssertion,
  isAnsweredExamFinding,
  optionsForKind,
  tonesForKind,
} from "../canonical-option-sets";
import { validateClinicalForm } from "../validate-form";
import type { ClinicalFormDefinition, ClinicalFormRuntimeContext } from "../types";

describe("clinical answer sets", () => {
  it("keeps unknown and not-assessed as separate answers", () => {
    // They are different clinical statements: unknown means the question was asked and the
    // answer is not available; not assessed means it was never asked. Only the second is a
    // gap the clinician can close.
    const codes = CLINICAL_ASSERTION_OPTIONS.map((o) => o.code);
    expect(codes).toEqual(["yes", "no", "unknown", "not_assessed"]);
  });

  it("treats an unanswered question as unanswered rather than negative", () => {
    expect(isAnsweredAssertion("no")).toBe(true);
    expect(isAnsweredAssertion("unknown")).toBe(true);
    expect(isAnsweredAssertion("not_assessed")).toBe(false);
    expect(isAnsweredAssertion(undefined)).toBe(false);
  });

  it("only a positive answer asserts the finding is present", () => {
    // The important half: "unknown" must not be read as a negative anywhere downstream.
    expect(assertsPresent("yes")).toBe(true);
    expect(assertsPresent("no")).toBe(false);
    expect(assertsPresent("unknown")).toBe(false);
    expect(assertsPresent("not_assessed")).toBe(false);
  });

  it("separates the reasons an examination produced no finding", () => {
    const codes = EXAM_FINDING_OPTIONS.map((o) => o.code);
    expect(codes).toContain("not_examined");
    expect(codes).toContain("unable");
    expect(codes).toContain("deferred");
    expect(codes).toContain("distressed");
    expect(codes).toContain("uncertain");

    // A deferred examination is a different next step from an uncertain finding.
    expect(isAnsweredExamFinding("uncertain")).toBe(true);
    expect(isAnsweredExamFinding("deferred")).toBe(false);
    expect(isAnsweredExamFinding("not_examined")).toBe(false);
  });

  it("every option carries a terminology code so answers are interoperable", () => {
    [...CLINICAL_ASSERTION_OPTIONS, ...EXAM_FINDING_OPTIONS].forEach((option) => {
      expect(option.terminologyCode, `${option.code} needs a terminology code`).toBeTruthy();
      expect(option.terminologySystem).toBeTruthy();
    });
  });

  it("supplies the canonical set for the clinical kinds and nothing for others", () => {
    expect(optionsForKind("clinical_assertion")).toHaveLength(4);
    expect(optionsForKind("exam_finding")).toHaveLength(7);
    expect(optionsForKind("coded_single")).toBeUndefined();
  });

  it("tones make an abnormal finding read differently from a normal one", () => {
    expect(tonesForKind("exam_finding").abnormal).toBe("danger");
    expect(tonesForKind("exam_finding").normal).toBe("positive");
    expect(tonesForKind("clinical_assertion").not_assessed).toBe("muted");
  });
});

describe("validation of clinical answers", () => {
  const context: ClinicalFormRuntimeContext = {
    patient: {
      patientId: "CPID-1",
      ageMonths: 14,
      ageBand: "INFANT",
      sex: "FEMALE",
      pregnant: null,
      programmes: [],
      knownConditionCodes: [],
    },
    encounterType: "IMCI",
    providerRoles: ["NURSE"],
  };

  const form = (kind: "clinical_assertion" | "exam_finding"): ClinicalFormDefinition => ({
    id: "test.form",
    version: "1",
    title: "Test",
    description: "",
    healthDomain: "CHILD",
    programme: "IMCI",
    encounterTypes: ["IMCI"],
    sections: [
      {
        id: "s1",
        title: "Danger signs",
        fields: [
          {
            id: "convulsions",
            linkId: "convulsions",
            label: "Convulsions",
            kind,
            validation: { required: true },
          },
        ],
      },
    ],
  });

  it("blocks an untouched required question", () => {
    const issues = validateClinicalForm(form("clinical_assertion"), {}, context);
    expect(issues).toHaveLength(1);
    expect(issues[0].message).toContain("required");
  });

  it("accepts a definite answer without complaint", () => {
    const issues = validateClinicalForm(form("clinical_assertion"), { convulsions: "no" }, context);
    expect(issues).toHaveLength(0);
  });

  it("accepts unknown, because recording it is honest", () => {
    const issues = validateClinicalForm(form("clinical_assertion"), { convulsions: "unknown" }, context);
    expect(issues).toHaveLength(0);
  });

  it("warns but does not block when a required sign was explicitly not assessed", () => {
    // Recording "not assessed" must stay possible — forcing a clinician to pick yes or no
    // to get past the form is how false negatives get written down. But the gap is shown.
    const issues = validateClinicalForm(form("clinical_assertion"), { convulsions: "not_assessed" }, context);
    expect(issues).toHaveLength(1);
    expect(issues[0].severity).toBe("warning");
    expect(issues[0].message).toContain("not been assessed");
  });

  it("warns when a required examination was deferred", () => {
    const issues = validateClinicalForm(form("exam_finding"), { convulsions: "deferred" }, context);
    expect(issues[0].severity).toBe("warning");
    expect(issues[0].message).toContain("not been examined");
  });

  it("does not warn when the examination produced an uncertain finding", () => {
    // Uncertain is a finding: the clinician looked and could not be sure.
    const issues = validateClinicalForm(form("exam_finding"), { convulsions: "uncertain" }, context);
    expect(issues).toHaveLength(0);
  });
});
