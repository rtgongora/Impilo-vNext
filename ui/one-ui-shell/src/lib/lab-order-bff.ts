/**
 * Maps UI lab-order payloads to Experience BFF snake_case contract.
 * @see services/experience-bff/.../LabOrdersController.CreateLabOrderRequest
 */

export interface LabOrderUiPayload {
  patientId: string;
  encounterId: string;
  testName: string;
  testCode: string;
  category: string;
  priority: string;
  clinicalNotes?: string | null;
  facilityId: string;
  orderedBy?: string;
  orderedByName?: string;
  patientCpid?: string;
  pctEncounterRef?: string;
}

export function toLabOrderBffBody(payload: LabOrderUiPayload): Record<string, unknown> {
  const orderedBy = payload.orderedBy?.trim() ?? "";
  if (!orderedBy) {
    throw new Error("ordered_by is required for lab order submission");
  }
  if (!payload.testName?.trim()) {
    throw new Error("test_name is required for lab order submission");
  }

  return {
    patient_id: payload.patientId,
    encounter_id: payload.encounterId,
    test_name: payload.testName.trim(),
    test_code: payload.testCode?.trim() || payload.testName.trim(),
    category: payload.category || "LABORATORY",
    priority: payload.priority || "ROUTINE",
    clinical_notes: payload.clinicalNotes ?? undefined,
    ordered_by: orderedBy,
    ordered_by_name: payload.orderedByName?.trim() || orderedBy,
    facility_id: payload.facilityId,
    patient_cpid: payload.patientCpid?.trim() || undefined,
    pct_encounter_ref: payload.pctEncounterRef?.trim() || payload.encounterId || undefined,
  };
}
