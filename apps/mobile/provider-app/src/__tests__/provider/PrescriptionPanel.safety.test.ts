import { describe, expect, it } from "vitest";
import { evaluatePrescriptionSafety } from "../../screens/provider/PrescriptionPanel";
import type { Prescription } from "../../types";

describe("evaluatePrescriptionSafety", () => {
  const activeRx: Prescription = {
    id: "rx-1",
    encounterId: "enc-1",
    patientId: "pat-1",
    medication: "Amoxicillin",
    dosage: "500mg",
    frequency: "TDS",
    duration: "5 days",
    quantity: 15,
    status: "ACTIVE",
    prescribedAt: "2026-01-01T00:00:00Z",
    prescribedBy: "prov-1",
  };

  it("blocks duplicate active therapy", () => {
    const out = evaluatePrescriptionSafety({
      existingPrescriptions: [activeRx],
      medication: "amoxicillin",
      dosage: "500mg",
      frequency: "TDS",
      duration: "5 days",
      quantity: 15,
      interactionRows: [],
    });
    expect(out.blockReason).toMatch(/already exists/i);
  });

  it("blocks severe interactions", () => {
    const out = evaluatePrescriptionSafety({
      existingPrescriptions: [],
      medication: "Drug A",
      dosage: "10mg",
      frequency: "BD",
      duration: "7 days",
      quantity: 14,
      interactionRows: [{ severity: "HIGH", message: "Contraindicated combination" }],
    });
    expect(out.blockReason).toMatch(/severe|contraindicated/i);
  });

  it("returns warning list for moderate interactions", () => {
    const out = evaluatePrescriptionSafety({
      existingPrescriptions: [],
      medication: "Drug A",
      dosage: "10mg",
      frequency: "BD",
      duration: "7 days",
      quantity: 14,
      interactionRows: [{ severity: "MEDIUM", message: "Monitor QT interval" }],
    });
    expect(out.blockReason).toBeNull();
    expect(out.warnings).toContain("Monitor QT interval");
  });
});
