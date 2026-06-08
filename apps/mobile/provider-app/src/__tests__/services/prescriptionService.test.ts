import { describe, expect, it } from "vitest";
import { normalizePrescriptionList, normalizePrescriptionResource } from "../../services/prescriptionService";

describe("prescriptionService normalization", () => {
  it("maps pharmacy JSON:API resources to app prescriptions", () => {
    const rx = normalizePrescriptionResource({
      id: "rx-1",
      type: "prescription",
      attributes: {
        encounter_id: "enc-1",
        patient_id: "cpid-1",
        medication_name: "Amoxicillin",
        dosage: "500mg",
        frequency: "TDS",
        duration: "7 days",
        quantity: 21,
        status: "ACTIVE",
        prescribed_by: "prov-1",
        created_at: "2026-01-01T00:00:00Z",
      },
    });

    expect(rx?.id).toBe("rx-1");
    expect(rx?.medication).toBe("Amoxicillin");
    expect(rx?.prescribedBy).toBe("prov-1");
  });

  it("normalizes list payloads from BFF data envelope", () => {
    const list = normalizePrescriptionList({
      data: [
        {
          id: "rx-1",
          type: "prescription",
          attributes: { medication_name: "A", dosage: "1", frequency: "OD", duration: "1d", quantity: 1, status: "ACTIVE" },
        },
      ],
    });
    expect(list).toHaveLength(1);
    expect(list[0].medication).toBe("A");
  });
});
