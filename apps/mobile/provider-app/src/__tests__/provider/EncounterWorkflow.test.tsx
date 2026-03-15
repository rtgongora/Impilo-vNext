/**
 * Encounter Workflow Tests — Full encounter lifecycle verification.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { encounterStore } from "../../stores/encounterStore";
import type { Encounter, VitalReading, Diagnosis, Prescription, LabOrder, Referral } from "../../types";

describe("Encounter Store — Workflow", () => {
  const mockEncounter: Encounter = {
    id: "enc-1",
    patientId: "pat-1",
    facilityId: "fac-1",
    encounterType: "OUTPATIENT",
    status: "IN_PROGRESS",
    startedAt: "2026-03-15T08:00:00Z",
    createdAt: "2026-03-15T08:00:00Z",
    updatedAt: "2026-03-15T08:00:00Z",
  };

  const mockVital: VitalReading = {
    id: "vit-1",
    encounterId: "enc-1",
    type: "BLOOD_PRESSURE",
    value: 120,
    unit: "mmHg",
    measuredAt: "2026-03-15T08:05:00Z",
    measuredBy: "provider-1",
  };

  const mockDiagnosis: Diagnosis = {
    id: "dx-1",
    encounterId: "enc-1",
    icdCode: "J06.9",
    icdDescription: "Acute upper respiratory infection, unspecified",
    isPrimary: true,
    diagnosedAt: "2026-03-15T08:10:00Z",
  };

  const mockPrescription: Prescription = {
    id: "rx-1",
    encounterId: "enc-1",
    patientId: "pat-1",
    medication: "Amoxicillin",
    dosage: "500mg",
    frequency: "TDS",
    duration: "7 days",
    quantity: 21,
    status: "ACTIVE",
    prescribedAt: "2026-03-15T08:15:00Z",
    prescribedBy: "provider-1",
  };

  const mockLabOrder: LabOrder = {
    id: "lab-1",
    encounterId: "enc-1",
    patientId: "pat-1",
    testName: "Full Blood Count",
    testCode: "FBC",
    urgency: "ROUTINE",
    status: "ORDERED",
    orderedAt: "2026-03-15T08:20:00Z",
    orderedBy: "provider-1",
  };

  const mockReferral: Referral = {
    id: "ref-1",
    encounterId: "enc-1",
    patientId: "pat-1",
    fromFacilityId: "fac-1",
    toFacilityId: "fac-2",
    toFacilityName: "District Hospital",
    specialty: "Surgery",
    reason: "Appendicitis",
    urgency: "URGENT",
    status: "PENDING",
    createdAt: "2026-03-15T08:25:00Z",
  };

  beforeEach(() => {
    encounterStore.getState().clearEncounter();
  });

  it("starts with no active encounter", () => {
    const state = encounterStore.getState();
    expect(state.activeEncounter).toBeNull();
    expect(state.vitals).toHaveLength(0);
    expect(state.isDirty).toBe(false);
  });

  it("sets active encounter and resets collections", () => {
    encounterStore.getState().setActiveEncounter(mockEncounter);
    const state = encounterStore.getState();
    expect(state.activeEncounter?.id).toBe("enc-1");
    expect(state.isDirty).toBe(false);
  });

  it("adds and removes vitals", () => {
    encounterStore.getState().setActiveEncounter(mockEncounter);
    encounterStore.getState().addVital(mockVital);
    expect(encounterStore.getState().vitals).toHaveLength(1);
    expect(encounterStore.getState().isDirty).toBe(true);

    encounterStore.getState().removeVital("vit-1");
    expect(encounterStore.getState().vitals).toHaveLength(0);
  });

  it("adds and removes diagnoses", () => {
    encounterStore.getState().setActiveEncounter(mockEncounter);
    encounterStore.getState().addDiagnosis(mockDiagnosis);
    expect(encounterStore.getState().diagnoses).toHaveLength(1);
    expect(encounterStore.getState().diagnoses[0].icdCode).toBe("J06.9");

    encounterStore.getState().removeDiagnosis("dx-1");
    expect(encounterStore.getState().diagnoses).toHaveLength(0);
  });

  it("adds and removes prescriptions", () => {
    encounterStore.getState().setActiveEncounter(mockEncounter);
    encounterStore.getState().addPrescription(mockPrescription);
    expect(encounterStore.getState().prescriptions).toHaveLength(1);
    expect(encounterStore.getState().prescriptions[0].medication).toBe("Amoxicillin");

    encounterStore.getState().removePrescription("rx-1");
    expect(encounterStore.getState().prescriptions).toHaveLength(0);
  });

  it("adds and removes lab orders", () => {
    encounterStore.getState().setActiveEncounter(mockEncounter);
    encounterStore.getState().addLabOrder(mockLabOrder);
    expect(encounterStore.getState().labOrders).toHaveLength(1);

    encounterStore.getState().removeLabOrder("lab-1");
    expect(encounterStore.getState().labOrders).toHaveLength(0);
  });

  it("adds and removes referrals", () => {
    encounterStore.getState().setActiveEncounter(mockEncounter);
    encounterStore.getState().addReferral(mockReferral);
    expect(encounterStore.getState().referrals).toHaveLength(1);

    encounterStore.getState().removeReferral("ref-1");
    expect(encounterStore.getState().referrals).toHaveLength(0);
  });

  it("completes full encounter workflow: vitals → dx → rx → labs → referral", () => {
    encounterStore.getState().setActiveEncounter(mockEncounter);
    encounterStore.getState().addVital(mockVital);
    encounterStore.getState().addDiagnosis(mockDiagnosis);
    encounterStore.getState().addPrescription(mockPrescription);
    encounterStore.getState().addLabOrder(mockLabOrder);
    encounterStore.getState().addReferral(mockReferral);

    const state = encounterStore.getState();
    expect(state.vitals).toHaveLength(1);
    expect(state.diagnoses).toHaveLength(1);
    expect(state.prescriptions).toHaveLength(1);
    expect(state.labOrders).toHaveLength(1);
    expect(state.referrals).toHaveLength(1);
    expect(state.isDirty).toBe(true);
  });

  it("clears encounter and all collections", () => {
    encounterStore.getState().setActiveEncounter(mockEncounter);
    encounterStore.getState().addVital(mockVital);
    encounterStore.getState().addDiagnosis(mockDiagnosis);
    encounterStore.getState().clearEncounter();

    const state = encounterStore.getState();
    expect(state.activeEncounter).toBeNull();
    expect(state.vitals).toHaveLength(0);
    expect(state.diagnoses).toHaveLength(0);
    expect(state.isDirty).toBe(false);
  });
});
