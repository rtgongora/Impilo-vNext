/**
 * Queue Service — Queue management, call-next, stats.
 */
import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/mobile/provider";

export async function fetchQueue(facilityId?: string): Promise<unknown[]> {
  const params = new URLSearchParams();
  if (facilityId) params.set("facility_id", facilityId);
  const qs = params.toString();
  const r = await apiClient.get<{ data: unknown[] }>(`/internal/v1/queue/entries${qs ? `?${qs}` : ""}`);
  return r.data.data;
}

export interface QueueDefinition {
  id: string;
  name: string;
  serviceType: string | null;
  status: string | null;
  active: boolean;
}

/**
 * Queue definitions for a facility (owned by TUSO facility service-point/workspace configuration and
 * materialised into PCT for care operations; read-only here). The BFF wraps the PCT payload under
 * `data`; shapes vary, so fields are read defensively without fabricating values.
 */
export async function fetchQueueDefinitions(facilityId: string): Promise<QueueDefinition[]> {
  const r = await apiClient.get<{ data: unknown }>(
    `/internal/v1/queue/definitions?facility_id=${encodeURIComponent(facilityId)}`,
  );
  const raw = r.data?.data;
  const list: Record<string, unknown>[] = Array.isArray(raw)
    ? (raw as Record<string, unknown>[])
    : Array.isArray((raw as { content?: unknown[] })?.content)
      ? ((raw as { content: Record<string, unknown>[] }).content)
      : Array.isArray((raw as { items?: unknown[] })?.items)
        ? ((raw as { items: Record<string, unknown>[] }).items)
        : [];
  const pick = (o: Record<string, unknown>, keys: string[]): string | null => {
    for (const k of keys) {
      const v = o[k];
      if (typeof v === "string" && v) return v;
    }
    return null;
  };
  return list.map((o) => ({
    id: pick(o, ["id", "queueId", "code"]) ?? "",
    name: pick(o, ["name", "queueName", "label", "displayName"]) ?? "Unnamed queue",
    serviceType: pick(o, ["serviceType", "servicePointType", "type"]),
    status: pick(o, ["status", "state"]),
    active: o.active !== false && o.status !== "INACTIVE",
  }));
}
export async function callNext(entryId: string): Promise<unknown> {
  const r = await apiClient.post<{ data: unknown }>(`/internal/v1/queue/entries/${entryId}/call`);
  return r.data.data;
}
export async function completeEntry(id: string): Promise<void> {
  await apiClient.post(`/internal/v1/queue/entries/${id}/complete`);
}
export async function fetchQueueStats(facilityId?: string): Promise<{ waiting: number; inProgress: number; completedToday: number }> {
  const r = await apiClient.post<{ data: Record<string, number> }>("/internal/v1/queue/entries/stats", {
    facilityId,
  });
  return {
    waiting: Number(r.data.data.waiting ?? 0),
    inProgress: Number(r.data.data.inProgress ?? r.data.data.inService ?? r.data.data.called ?? 0),
    completedToday: Number(r.data.data.completedToday ?? r.data.data.completed ?? 0),
  };
}
export interface TriageVitals {
  heart_rate: number | null;
  respiratory_rate: number | null;
  spo2: number | null;
  mental_status: string;
}

export async function recordTriage(body: {
  patientId: string;
  encounterId: string;
  triageLevel: string;
  chiefComplaint: string;
  /** The clinician's acuity on PCT's scale: 1 = resuscitation … 5 = non-urgent. */
  acuityScore: number;
  /**
   * Vitals as structured data. The endpoint has always accepted a `vitals` object; this client used
   * to flatten them into the free-text `notes` field instead, which put measurements the server
   * could have reasoned about into a string nothing parses.
   */
  vitals?: TriageVitals;
  notes?: string;
  triagedBy?: string;
  triagedByName?: string;
}): Promise<{ id: string; acuity?: number }> {
  const r = await apiClient.post<{ data: { id: string; acuity?: number } }>(`${V1}/triage`, {
    patient_id: body.patientId,
    encounter_id: body.encounterId,
    acuity: body.acuityScore,
    chief_complaint: body.chiefComplaint,
    vitals: body.vitals,
    notes: body.notes ?? body.chiefComplaint,
    triaged_by: body.triagedBy ?? "provider-app",
    triaged_by_name: body.triagedByName ?? "Provider App",
  });
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
// fetchSpecialtyWorkspaces removed: it had zero callers and pointed at a hard-coded BFF menu
// that disagreed with the one this app renders. Specialty tool content is owned by the governed
// forms-service catalogue; mobile tool disposition lives in data/specialtyToolRegistry.ts.
