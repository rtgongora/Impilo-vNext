import { describe, expect, it } from "vitest";
import { ANTENATAL_CONTACT_1_FORM } from "../clinical-form-definitions/antenatal-contact-1-exemplar";
import { fieldVisible, visibleFieldsForSection } from "../evaluate-visibility";
import type { ClinicalFormPatientContext, ClinicalFormRuntimeContext } from "../types";
import { evaluateNumericVital } from "../vitals-reference/evaluate";
import { formValuesToQuestionnaireResponseItems } from "../fhir-questionnaire-mapping/to-questionnaire-response";

function makeRuntime(overrides?: {
  patient?: Partial<ClinicalFormPatientContext>;
  encounterType?: string;
  providerRoles?: string[];
}): ClinicalFormRuntimeContext {
  return {
    patient: {
      patientId: "p1",
      ageMonths: 22 * 12,
      ageBand: "ADULT",
      sex: "FEMALE",
      pregnant: true,
      programmes: ["ANC"],
      knownConditionCodes: [],
      ...overrides?.patient,
    },
    encounterType: overrides?.encounterType ?? "ANTENATAL",
    providerRoles: overrides?.providerRoles ?? ["MIDWIFE"],
  };
}

describe("patient-aware visibility", () => {
  it("hides obstetric core fields for male patients", () => {
    const male = makeRuntime({ patient: { sex: "MALE" }, encounterType: "ANTENATAL" });
    const gravida = ANTENATAL_CONTACT_1_FORM.sections.flatMap((s) => s.fields).find((f) => f.id === "gravida")!;
    expect(fieldVisible(gravida.visibility, male)).toBe(false);
  });

  it("shows obstetric fields for female ANC", () => {
    const female = makeRuntime({ patient: { sex: "FEMALE" } });
    const gravida = ANTENATAL_CONTACT_1_FORM.sections.flatMap((s) => s.fields).find((f) => f.id === "gravida")!;
    expect(fieldVisible(gravida.visibility, female)).toBe(true);
  });

  it("excludes neonatal band from obstetric fields", () => {
    const neonate = makeRuntime({ patient: { sex: "FEMALE", ageBand: "NEONATAL", ageMonths: 0 } });
    const gravida = ANTENATAL_CONTACT_1_FORM.sections.flatMap((s) => s.fields).find((f) => f.id === "gravida")!;
    expect(fieldVisible(gravida.visibility, neonate)).toBe(false);
  });
});

describe("role-aware visibility", () => {
  it("hides field when required role missing", () => {
    const field = {
      id: "x",
      linkId: "x",
      label: "X",
      kind: "text" as const,
      visibility: { requireAnyRole: ["MIDWIFE"] },
    };
    const rt = makeRuntime({ providerRoles: ["CITIZEN"] });
    expect(fieldVisible(field.visibility, rt)).toBe(false);
  });
});

describe("coded dropdown / section visibility", () => {
  it("includes presenting complaint in visible section", () => {
    const hist = ANTENATAL_CONTACT_1_FORM.sections.find((s) => s.id === "history")!;
    const vis = visibleFieldsForSection(hist.fields, makeRuntime({}));
    expect(vis.some((f) => f.id === "presenting_complaint")).toBe(true);
  });
});

describe("vitals flagging", () => {
  it("flags implausible adult systolic", () => {
    const r = evaluateNumericVital("systolic_bp", 40, "ADULT");
    expect(r.some((x) => x.code === "OUT_OF_PLAUSIBLE")).toBe(true);
  });

  it("allows normal adult heart rate", () => {
    const r = evaluateNumericVital("heart_rate", 72, "ADULT");
    expect(r.some((x) => x.code === "WITHIN_NORMAL")).toBe(true);
  });
});

describe("FHIR QuestionnaireResponse mapping", () => {
  it("maps coded selections to valueCoding answers", () => {
    const items = formValuesToQuestionnaireResponseItems(ANTENATAL_CONTACT_1_FORM, {
      presenting_complaint: "booking",
      syphilis_screen: "done_negative",
    });
    const pc = items.find((i) => i.linkId === "presenting-complaint");
    expect(pc?.answer?.[0]?.valueCoding?.code).toBeTruthy();
  });
});

describe("DAK exemplar rendering contract", () => {
  it("has required ANC sections and audit metadata", () => {
    expect(ANTENATAL_CONTACT_1_FORM.programme).toBe("ANC");
    expect(ANTENATAL_CONTACT_1_FORM.sections.length).toBeGreaterThanOrEqual(3);
    expect(ANTENATAL_CONTACT_1_FORM.audit.sensitivity).toBe("SENSITIVE");
  });
});
