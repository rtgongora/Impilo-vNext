/** Post check-in navigation from BFF appointment meta. */

export interface AppointmentCheckInMeta {
  patient_id?: string;
  journey_id?: string;
  encounter_id?: string;
  core_transaction_id?: string;
}

/**
 * Builds the outpatient spine URL after appointment check-in.
 * Prefers direct encounter route when encounter_id is present.
 */
export function buildPostCheckInRoute(meta: AppointmentCheckInMeta): string | null {
  const patientId = meta.patient_id?.trim();
  const transactionId = meta.core_transaction_id?.trim();
  if (!patientId || !transactionId) {
    return null;
  }

  const params = new URLSearchParams({ transaction_id: transactionId });
  if (meta.journey_id?.trim()) {
    params.set("journey_id", meta.journey_id.trim());
  }
  const encounterId = meta.encounter_id?.trim();
  if (encounterId) {
    return `/ehr/${patientId}/encounter/${encounterId}?${params.toString()}`;
  }
  return `/ehr/${patientId}/encounters?${params.toString()}`;
}
