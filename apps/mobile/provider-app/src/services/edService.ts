import { apiClient } from "@impilo/mobile-api-client";
import { queueClinicalCreateOnRetryableError } from "./offlineClinicalQueue";
import { refreshEmergencyOutboxBadge } from "./emergencyOutboxService";

const BASE = "/internal/v1/ed";

function unwrap<T>(res: unknown): T {
  const body = res as { data?: T } | T;
  return body && typeof body === "object" && "data" in (body as object)
    ? (body as { data: T }).data
    : (body as T);
}

async function postWithQueue(path: string, payload: Record<string, unknown>, collection = "ed_writes") {
  try {
    return await apiClient.post(path, payload);
  } catch (error) {
    const offline = await queueClinicalCreateOnRetryableError(error, {
      collection,
      path,
      payload,
    });
    if (offline) {
      void refreshEmergencyOutboxBadge();
      return { data: { queued: true, recordId: offline.recordId } };
    }
    throw error;
  }
}

export const listEdVisits = async (facilityId?: string) => {
  const qs = facilityId ? `?facilityId=${facilityId}` : "";
  const res = await apiClient.get(`${BASE}/visits${qs}`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
};

export const openEdVisit = async (body: Record<string, unknown>) => {
  const res = await postWithQueue(`${BASE}/visits`, body);
  return res;
};

export const getEdVisit = async (visitId: string) =>
  apiClient.get<{ data: Record<string, unknown> }>(`${BASE}/visits/${visitId}`);

export const edTriage = async (visitId: string, body: Record<string, unknown>) =>
  postWithQueue(`${BASE}/visits/${visitId}/triage`, body);

export const edAssignZone = async (visitId: string, body: Record<string, unknown>) =>
  postWithQueue(`${BASE}/visits/${visitId}/zone`, body);

export const edBindProtocol = async (visitId: string, body: Record<string, unknown>) =>
  postWithQueue(`${BASE}/visits/${visitId}/protocol`, body);

export const edPageTeam = async (visitId: string, body: Record<string, unknown>) =>
  postWithQueue(`${BASE}/visits/${visitId}/page`, body);

export const edActivateTrauma = async (visitId: string, body: Record<string, unknown>) =>
  postWithQueue(`${BASE}/visits/${visitId}/trauma/activate`, body);

export const edTraumaSurvey = async (visitId: string, body: Record<string, unknown>) =>
  postWithQueue(`${BASE}/visits/${visitId}/trauma/survey`, body);

export const edDisposition = async (visitId: string, body: Record<string, unknown>) =>
  postWithQueue(`${BASE}/visits/${visitId}/disposition`, body);

export const edStartEncounter = async (visitId: string) =>
  postWithQueue(`${BASE}/visits/${visitId}/encounter`, {});

export const scoreEdTriage = async (body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/triage/score`, body);

export const edTriageDiscriminators = async () =>
  apiClient.get(`${BASE}/triage/discriminators`);

export const listEdDiagnostics = async (visitId: string) => {
  const res = await apiClient.get(`${BASE}/visits/${visitId}/diagnostics`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
};

export const orderEdDiagnostic = async (visitId: string, body: Record<string, unknown>) =>
  postWithQueue(`${BASE}/visits/${visitId}/diagnostics`, body);

export const actOnEdDiagnostic = async (linkId: string, body: Record<string, unknown>) =>
  postWithQueue(`${BASE}/diagnostics/${linkId}/act`, body);

export const closeEdDiagnostic = async (linkId: string, body: Record<string, unknown>) =>
  postWithQueue(`${BASE}/diagnostics/${linkId}/close`, body);

export const reconcileCriticalDiagnostics = async (body: Record<string, unknown>) =>
  postWithQueue(`${BASE}/diagnostics/reconcile-critical`, body);

export const listTraumaTeam = async (traumaId: string) => {
  const res = await apiClient.get(`${BASE}/trauma/${traumaId}/team`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
};

export const ackTraumaTeamMember = async (traumaId: string, memberId: string, ackBy?: string) =>
  postWithQueue(`${BASE}/trauma/${traumaId}/team/${memberId}/ack`, { ackBy: ackBy ?? "" });

export const escalateTraumaTeam = async (traumaId: string, timeoutSeconds = 0) =>
  postWithQueue(`${BASE}/trauma/${traumaId}/team/escalate`, { timeoutSeconds });
