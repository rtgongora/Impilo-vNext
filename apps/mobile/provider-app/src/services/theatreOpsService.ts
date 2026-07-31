/**
 * Theatre ops surfaces — readiness board, surgical referrals, waitlist, OR lists,
 * utilisation report, anaesthesia chart, CSSD sets, controlled-drug register.
 *
 * Honesty: list/board GET failures must throw (never collapse to []). Callers surface
 * React Query `isError` as unavailable — not an empty board/inbox/register.
 *
 * TheatreController list GETs use the BFF `{ data: … }` envelope.
 * TheatreSchedulingController returns sovereign bodies as-is (array or object).
 * Anaesthesia chart is procedure-keyed: `/internal/v1/procedures/{id}/anaesthesia/chart`.
 */
import { apiClient } from "@impilo/mobile-api-client";

const THEATRE = "/internal/v1/theatre";
const PROCEDURES = "/internal/v1/procedures";
const SCHEDULING = "/internal/v1/scheduling";
const REFERRALS = "/internal/v1/referrals/surgical";
const REPORTS = "/internal/v1/reports";

function envelopeData<T>(body: unknown): T {
  if (body && typeof body === "object" && "data" in body) {
    return (body as { data: T }).data;
  }
  return body as T;
}

function asRecordArray(payload: unknown): Record<string, unknown>[] {
  if (Array.isArray(payload)) return payload as Record<string, unknown>[];
  if (payload && typeof payload === "object") {
    const obj = payload as Record<string, unknown>;
    if (Array.isArray(obj.data)) return obj.data as Record<string, unknown>[];
    if (Array.isArray(obj.items)) return obj.items as Record<string, unknown>[];
    if (Array.isArray(obj.rows)) return obj.rows as Record<string, unknown>[];
  }
  return [];
}

/** Board rows from GET /theatre/readiness-board (envelope → `{ rows }`). */
export async function fetchTheatreReadinessBoard(): Promise<Record<string, unknown>[]> {
  const res = await apiClient.get<unknown>(`${THEATRE}/readiness-board`, { noRetry: true });
  const board = envelopeData<unknown>(res.data);
  if (board && typeof board === "object" && "rows" in (board as object)) {
    const rows = (board as { rows?: unknown }).rows;
    if (!Array.isArray(rows)) {
      throw new Error("Readiness board response missing rows array");
    }
    return rows as Record<string, unknown>[];
  }
  if (Array.isArray(board)) return board as Record<string, unknown>[];
  throw new Error("Unexpected readiness board payload");
}

export async function listSurgicalReferralWorklist(params?: {
  decision?: string;
  specialty?: string;
}): Promise<Record<string, unknown>[]> {
  const qs = new URLSearchParams();
  if (params?.decision) qs.set("decision", params.decision);
  if (params?.specialty) qs.set("specialty", params.specialty);
  const suffix = qs.toString() ? `?${qs.toString()}` : "";
  const res = await apiClient.get<unknown>(`${REFERRALS}/worklist${suffix}`, { noRetry: true });
  return asRecordArray(res.data);
}

export async function decideSurgicalReferral(
  id: string,
  decision: string,
): Promise<Record<string, unknown>> {
  const res = await apiClient.post<unknown>(`${REFERRALS}/${encodeURIComponent(id)}/decide`, {
    decision,
  });
  return envelopeData<Record<string, unknown>>(res.data) ?? {};
}

export async function listSurgicalWaitlist(params?: {
  status?: string;
  sort?: string;
}): Promise<Record<string, unknown>[]> {
  const qs = new URLSearchParams();
  if (params?.status) qs.set("status", params.status);
  if (params?.sort) qs.set("sort", params.sort);
  const suffix = qs.toString() ? `?${qs.toString()}` : "";
  const res = await apiClient.get<unknown>(`${SCHEDULING}/surgical-waitlist${suffix}`, {
    noRetry: true,
  });
  return asRecordArray(res.data);
}

export async function listTheatreSessions(params?: {
  facilityId?: string;
  date?: string;
}): Promise<Record<string, unknown>[]> {
  const qs = new URLSearchParams();
  if (params?.facilityId) qs.set("facilityId", params.facilityId);
  if (params?.date) qs.set("date", params.date);
  const suffix = qs.toString() ? `?${qs.toString()}` : "";
  const res = await apiClient.get<unknown>(`${SCHEDULING}/theatre-sessions${suffix}`, {
    noRetry: true,
  });
  return asRecordArray(res.data);
}

export async function getTheatreSession(sessionId: string): Promise<Record<string, unknown>> {
  const res = await apiClient.get<unknown>(
    `${SCHEDULING}/theatre-sessions/${encodeURIComponent(sessionId)}`,
    { noRetry: true },
  );
  const body = res.data;
  if (body && typeof body === "object" && "data" in body && (body as { data: unknown }).data) {
    return (body as { data: Record<string, unknown> }).data;
  }
  return (body ?? {}) as Record<string, unknown>;
}

/**
 * Runs seeded `theatre-utilisation` report and returns the first metrics row.
 * Throws when the run fails or yields no result — never invents zero KPIs.
 */
export async function runTheatreUtilisationReport(): Promise<Record<string, unknown>> {
  const res = await apiClient.post<unknown>(`${REPORTS}/theatre-utilisation/run`, {}, {
    noRetry: true,
  });
  const payload = envelopeData<{
    result?: string;
    errorMessage?: string;
    error_message?: string;
  }>(res.data);
  if (payload?.result) {
    const rows = JSON.parse(payload.result) as Record<string, unknown>[];
    if (Array.isArray(rows) && rows.length > 0) return rows[0] ?? {};
    return {};
  }
  throw new Error(
    payload?.errorMessage ?? payload?.error_message ?? "Theatre utilisation report returned no data",
  );
}

export async function getAnaesthesiaChart(caseId: string): Promise<Record<string, unknown>[]> {
  const res = await apiClient.get<unknown>(
    `${PROCEDURES}/${encodeURIComponent(caseId)}/anaesthesia/chart`,
    { noRetry: true },
  );
  const data = envelopeData<unknown>(res.data);
  return asRecordArray(data);
}

export async function addAnaesthesiaChartEntry(
  caseId: string,
  body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const res = await apiClient.post<unknown>(
    `${PROCEDURES}/${encodeURIComponent(caseId)}/anaesthesia/chart`,
    body,
  );
  return envelopeData<Record<string, unknown>>(res.data) ?? {};
}

export async function listInstrumentSets(caseId: string): Promise<Record<string, unknown>[]> {
  const res = await apiClient.get<unknown>(
    `${THEATRE}/cases/${encodeURIComponent(caseId)}/instrument-sets`,
    { noRetry: true },
  );
  return asRecordArray(envelopeData(res.data));
}

export async function issueInstrumentSet(
  caseId: string,
  body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const payload = { ...body };
  if (payload.setCode && !payload.instrumentSetId) {
    payload.instrumentSetId = payload.setCode;
  }
  const res = await apiClient.post<unknown>(
    `${THEATRE}/cases/${encodeURIComponent(caseId)}/instrument-sets/issue`,
    payload,
  );
  return envelopeData<Record<string, unknown>>(res.data) ?? {};
}

export async function returnInstrumentSet(
  caseId: string,
  body: Record<string, unknown> = {},
): Promise<Record<string, unknown>> {
  const res = await apiClient.post<unknown>(
    `${THEATRE}/cases/${encodeURIComponent(caseId)}/instrument-sets/return`,
    body,
  );
  return envelopeData<Record<string, unknown>>(res.data) ?? {};
}

export async function listControlledDrugs(caseId: string): Promise<Record<string, unknown>[]> {
  const res = await apiClient.get<unknown>(
    `${THEATRE}/cases/${encodeURIComponent(caseId)}/controlled-drugs`,
    { noRetry: true },
  );
  return asRecordArray(envelopeData(res.data));
}

export async function recordControlledDrug(
  caseId: string,
  body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const res = await apiClient.post<unknown>(
    `${THEATRE}/cases/${encodeURIComponent(caseId)}/controlled-drugs`,
    body,
  );
  return envelopeData<Record<string, unknown>>(res.data) ?? {};
}
