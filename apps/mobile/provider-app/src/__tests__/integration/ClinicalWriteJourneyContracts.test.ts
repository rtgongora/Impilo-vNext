import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import { recordTriage } from "../../services/queueService";
import { recordVital } from "../../services/vitalsService";
import { recordDiagnosis } from "../../services/diagnosisService";
import { createLabOrder } from "../../services/labService";
import { createReferral } from "../../services/referralService";
import { createPrescription } from "../../services/prescriptionService";

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe("clinical write journey canonical routes", () => {
  beforeEach(() => {
    vi.mocked(apiClient.post).mockReset();
  });

  it("uses canonical provider triage route", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: { id: "triage-1" } } } as never);
    await recordTriage({
      patientId: "pat-1",
      encounterId: "enc-1",
      triageLevel: "3",
      chiefComplaint: "Headache",
      acuityScore: 3,
    });
    expect(apiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/provider/triage",
      expect.objectContaining({ patient_id: "pat-1", encounter_id: "enc-1" })
    );
  });

  it("uses canonical clinical write routes for vitals/diagnosis/labs/referrals/prescriptions", async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: { id: "v1", type: "Vital", attributes: { encounter_id: "enc-1", vital_type: "HEART_RATE", value: 80, unit: "bpm", measured_at: "2026-01-01T00:00:00Z", measured_by: "prov-1" } } } } as never)
      .mockResolvedValueOnce({ data: { data: { id: "d1", type: "Diagnosis", attributes: { encounter_id: "enc-1", icd_code: "J06.9", icd_description: "URI", is_primary: true, diagnosed_at: "2026-01-01T00:00:00Z" } } } } as never)
      .mockResolvedValueOnce({ data: { data: { id: "l1", type: "LabOrder", attributes: { encounter_id: "enc-1", patient_id: "pat-1", test_name: "FBC", test_code: "CBC", urgency: "ROUTINE", status: "ORDERED", ordered_at: "2026-01-01T00:00:00Z", ordered_by: "prov-1" } } } } as never)
      .mockResolvedValueOnce({ data: { data: { id: "r1", type: "Referral", attributes: { encounter_id: "enc-1", patient_id: "pat-1", from_facility_id: "fac-1", to_facility_id: "fac-2", to_facility_name: "District Hospital", specialty: "Medicine", reason: "Review", urgency: "ROUTINE", status: "PENDING", created_at: "2026-01-01T00:00:00Z" } } } } as never)
      .mockResolvedValueOnce({ data: { data: { id: "p1", type: "Prescription", attributes: { encounter_id: "enc-1", patient_id: "pat-1", medication: "Amoxicillin", dosage: "500mg", frequency: "TDS", duration: "5 days", quantity: 15, status: "ACTIVE", prescribed_at: "2026-01-01T00:00:00Z", prescribed_by: "prov-1" } } } } as never);

    await recordVital({ encounterId: "enc-1", patientId: "pat-1", type: "HEART_RATE", value: 80, unit: "bpm" });
    await recordDiagnosis({ encounterId: "enc-1", icdCode: "J06.9", icdDescription: "URI", isPrimary: true });
    await createLabOrder({ encounterId: "enc-1", patientId: "pat-1", testName: "FBC", testCode: "CBC", urgency: "ROUTINE" });
    await createReferral({
      encounterId: "enc-1",
      patientId: "pat-1",
      fromFacilityId: "fac-1",
      toFacilityId: "fac-2",
      specialty: "Medicine",
      reason: "Review",
      urgency: "ROUTINE",
    });
    await createPrescription({
      encounterId: "enc-1",
      patientId: "pat-1",
      medication: "Amoxicillin",
      dosage: "500mg",
      frequency: "TDS",
      duration: "5 days",
      quantity: 15,
    });

    const calledRoutes = vi.mocked(apiClient.post).mock.calls.map(([path]) => String(path));
    expect(calledRoutes).toEqual(
      expect.arrayContaining([
        "/internal/v1/mobile/provider/vitals",
        "/internal/v1/mobile/provider/diagnosis",
        "/internal/v1/mobile/provider/labs",
        "/internal/v1/mobile/provider/referrals",
        "/internal/v1/mobile/provider/prescriptions",
      ])
    );
  });
});
