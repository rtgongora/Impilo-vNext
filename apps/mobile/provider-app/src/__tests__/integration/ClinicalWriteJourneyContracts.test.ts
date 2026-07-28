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

  it("sends the clinician's acuity unchanged, on PCT's scale", async () => {
    // The regression this pins: the screen used to combine the selected level with an ad-hoc
    // points score via Math.max. PCT's scale is 1=resuscitation … 5=non-urgent, so abnormal
    // vitals RAISED the number and demoted the patient — and a sick enough patient scored above
    // 5, failed @Max(5), and was rejected with a 400 the screen never showed. No record at all
    // for the sickest arrivals.
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: { id: "triage-1", acuity: 1 } } } as never);
    await recordTriage({
      patientId: "pat-1",
      encounterId: "enc-1",
      triageLevel: "1",
      chiefComplaint: "Unresponsive",
      acuityScore: 1,
      vitals: { heart_rate: 132, respiratory_rate: 34, spo2: 86, mental_status: "UNRESPONSIVE" },
    });

    const [, payload] = vi.mocked(apiClient.post).mock.calls[0] as [string, Record<string, unknown>];
    expect(payload.acuity).toBe(1);
    expect(payload.acuity as number).toBeLessThanOrEqual(5);
    // Vitals travel as structured data, not flattened into free text.
    expect(payload.vitals).toEqual({
      heart_rate: 132,
      respiratory_rate: 34,
      spo2: 86,
      mental_status: "UNRESPONSIVE",
    });
  });

  it("never derives an acuity outside the server's accepted range from extreme vitals", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: { id: "triage-2", acuity: 2 } } } as never);
    await recordTriage({
      patientId: "pat-2",
      encounterId: "enc-2",
      triageLevel: "2",
      chiefComplaint: "Severe respiratory distress",
      acuityScore: 2,
      vitals: { heart_rate: 150, respiratory_rate: 40, spo2: 80, mental_status: "PAIN" },
    });
    const [, payload] = vi.mocked(apiClient.post).mock.calls[0] as [string, Record<string, unknown>];
    expect(payload.acuity).toBe(2);
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

    const prescriptionPayload = vi.mocked(apiClient.post).mock.calls.find(
      ([path]) => path === "/internal/v1/mobile/provider/prescriptions"
    )?.[1] as Record<string, unknown> | undefined;
    expect(prescriptionPayload?.medication_name).toBe("Amoxicillin");
    expect(prescriptionPayload?.prescribed_by).toBeTruthy();

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
