import { describe, it, expect } from "vitest";
import {
  sectionForPathname,
  buildSectionHref,
  isSectionVisible,
  buildWizardStepHref,
  encounterSectionForWizardStepId,
  wizardStepIndexForPathname,
} from "./encounter-workspace-nav";

const clinical = { isClinical: true, isPrescriber: false, isDispenser: false, isAdmin: false };
const dispenserOnly = { isClinical: false, isPrescriber: false, isDispenser: true, isAdmin: false };
const citizen = { isClinical: false, isPrescriber: false, isDispenser: false, isAdmin: false };

describe("encounter-workspace-nav", () => {
  it("maps chart root to overview", () => {
    expect(sectionForPathname("/ehr/P1", "P1")).toBe("overview");
    expect(sectionForPathname("/ehr/P1/", "P1")).toBe("overview");
  });

  it("maps encounter route to assessment", () => {
    expect(sectionForPathname("/ehr/P1/encounter/E1", "P1")).toBe("assessment");
  });

  it("maps vitals to assessment section for highlighting", () => {
    expect(sectionForPathname("/ehr/P1/vitals", "P1")).toBe("assessment");
  });

  it("maps conditions to problems", () => {
    expect(sectionForPathname("/ehr/P1/conditions", "P1")).toBe("problems_diagnoses");
  });

  it("buildSectionHref adds encounter_id when context has encounter", () => {
    expect(buildSectionHref("orders_results", { patientId: "P1", encounterId: "E1" })).toContain("encounter_id=E1");
    expect(buildSectionHref("overview", { patientId: "P1", encounterId: "E1" })).toBe("/ehr/P1");
  });

  it("gates sections for non-clinical roles", () => {
    expect(isSectionVisible("overview", citizen)).toBe(true);
    expect(isSectionVisible("assessment", citizen)).toBe(false);
    expect(isSectionVisible("orders_results", dispenserOnly)).toBe(true);
  });

  it("wizardStepIndex prefers vitals path for vitals & triage step", () => {
    const idx = wizardStepIndexForPathname("/ehr/P1/vitals", "P1");
    expect(idx).toBe(1);
  });

  it("buildWizardStepHref resolves vitals triage to vitals or encounter", () => {
    expect(buildWizardStepHref("vitals_triage", { patientId: "P1", encounterId: null })).toBe("/ehr/P1/vitals");
    expect(buildWizardStepHref("vitals_triage", { patientId: "P1", encounterId: "E1" })).toBe("/ehr/P1/encounter/E1");
  });

  it("encounterSectionForWizardStepId maps wizard steps to menu sections", () => {
    expect(encounterSectionForWizardStepId("patient_context")).toBe("overview");
    expect(encounterSectionForWizardStepId("care_plan")).toBe("care_management");
    expect(encounterSectionForWizardStepId("vitals_triage")).toBe("assessment");
  });
});
