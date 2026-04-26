/**
 * Queue Service — Queue management, call-next, stats.
 */
import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/mobile/provider";

export async function fetchQueue(): Promise<unknown[]> {
  const r = await apiClient.get<{ data: unknown[] }>(`${V1}/queue`);
  return r.data.data;
}
export async function callNext(queueId: string): Promise<unknown> {
  const r = await apiClient.post<{ data: unknown }>(`${V1}/queue/call-next`, { queueId });
  return r.data.data;
}
export async function completeEntry(id: string): Promise<void> {
  await apiClient.post(`${V1}/queue/complete/${id}`);
}
export async function fetchQueueStats(): Promise<{ waiting: number; inProgress: number; completedToday: number }> {
  const r = await apiClient.get<{ data: { waiting: number; inProgress: number; completedToday: number } }>(`${V1}/queue/stats`);
  return r.data.data;
}
export async function recordTriage(body: { patientId: string; encounterId: string; triageLevel: string; chiefComplaint: string; acuityScore: number }): Promise<{ id: string }> {
  const r = await apiClient.post<{ data: { id: string } }>(`${V1}/triage`, body);
  return r.data.data;
}
export async function fetchBeds(): Promise<{ wards: unknown[]; beds: unknown[] }> {
  const r = await apiClient.get<{ data: { wards: unknown[]; beds: unknown[] } }>(`${V1}/beds`);
  return r.data.data;
}
export async function assignBed(bedId: string, patientId: string): Promise<void> {
  await apiClient.post(`${V1}/beds/${bedId}/assign`, { patientId });
}
export async function dischargeBed(bedId: string): Promise<void> {
  await apiClient.post(`${V1}/beds/${bedId}/discharge`);
}
export async function registerPatient(body: Record<string, unknown>): Promise<{ id: string }> {
  const r = await apiClient.post<{ data: { id: string } }>(`${V1}/patients/register`, body);
  return r.data.data;
}

type GeoRow = Record<string, unknown>;

function bffListPayload(envelope: unknown): GeoRow[] {
  if (!envelope || typeof envelope !== "object") return [];
  const inner = (envelope as { data?: unknown }).data;
  return Array.isArray(inner) ? (inner as GeoRow[]) : [];
}

export async function fetchZwDistricts(provinceCode: string): Promise<GeoRow[]> {
  const r = await apiClient.get<{ data: unknown[]; meta?: unknown }>(
    `/internal/v1/registry/geo/zw/districts?provinceCode=${encodeURIComponent(provinceCode)}`,
  );
  return bffListPayload(r.data);
}

export async function fetchZwWards(districtCode: string): Promise<GeoRow[]> {
  const r = await apiClient.get<{ data: unknown[]; meta?: unknown }>(
    `/internal/v1/registry/geo/zw/wards?districtCode=${encodeURIComponent(districtCode)}`,
  );
  return bffListPayload(r.data);
}
export async function fetchPendingDispensing(): Promise<unknown[]> {
  const r = await apiClient.get<{ data: unknown[] }>(`${V1}/pharmacy/pending`);
  return r.data.data;
}
export async function dispense(prescriptionId: string, dispensedBy: string): Promise<void> {
  await apiClient.post(`${V1}/pharmacy/dispense`, { prescriptionId, dispensedBy });
}
export async function verifyFiveRights(body: Record<string, string>): Promise<Record<string, boolean>> {
  const r = await apiClient.post<{ rights: Record<string, boolean> }>(`${V1}/pharmacy/verify-five-rights`, body);
  return r.data.rights;
}
export async function fetchCharges(encounterId?: string): Promise<unknown[]> {
  const params = encounterId ? `?encounterId=${encounterId}` : "";
  const r = await apiClient.get<{ data: unknown[] }>(`${V1}/billing/charges${params}`);
  return r.data.data;
}
export async function captureCharge(body: Record<string, unknown>): Promise<{ id: string }> {
  const r = await apiClient.post<{ data: { id: string } }>(`${V1}/billing/charge`, body);
  return r.data.data;
}
export async function fetchReportSummary(): Promise<Record<string, number>> {
  const r = await apiClient.get<{ data: Record<string, number> }>(`${V1}/reports/summary`);
  return r.data.data;
}
export async function fetchPages(recipientId?: string): Promise<unknown[]> {
  const params = recipientId ? `?recipientId=${recipientId}` : "";
  const r = await apiClient.get<{ data: unknown[] }>(`${V1}/paging${params}`);
  return r.data.data;
}
export async function sendPage(body: Record<string, string>): Promise<{ id: string }> {
  const r = await apiClient.post<{ data: { id: string } }>(`${V1}/paging/send`, body);
  return r.data.data;
}
export async function checkDrugInteractions(medications: string[]): Promise<unknown[]> {
  const r = await apiClient.post<{ data: unknown[] }>(`${V1}/clinical/drug-interactions`, { medications });
  return r.data.data;
}
export async function fetchOrderSets(): Promise<unknown[]> {
  const r = await apiClient.get<{ data: unknown[] }>(`${V1}/clinical/order-sets`);
  return r.data.data;
}
export async function fetchCarePlans(patientId: string): Promise<unknown[]> {
  const r = await apiClient.get<{ data: unknown[] }>(`${V1}/clinical/care-plans?patientId=${patientId}`);
  return r.data.data;
}
export async function createCarePlan(body: Record<string, unknown>): Promise<{ id: string }> {
  const r = await apiClient.post<{ data: { id: string } }>(`${V1}/clinical/care-plans`, body);
  return r.data.data;
}
export async function fetchMAR(patientId: string): Promise<unknown[]> {
  const r = await apiClient.get<{ data: unknown[] }>(`${V1}/clinical/mar?patientId=${patientId}`);
  return r.data.data;
}
export async function administerMedication(body: Record<string, string>): Promise<{ id: string }> {
  const r = await apiClient.post<{ data: { id: string } }>(`${V1}/clinical/mar/administer`, body);
  return r.data.data;
}
export async function evaluateCDS(body: Record<string, unknown>): Promise<unknown[]> {
  const r = await apiClient.post<{ data: unknown[] }>(`${V1}/clinical/cds/evaluate`, body);
  return r.data.data;
}
export async function fetchSpecialtyWorkspaces(): Promise<unknown[]> {
  const r = await apiClient.get<{ data: unknown[] }>(`${V1}/workspaces/specialties`);
  return r.data.data;
}
