import { apiClient } from "@impilo/mobile-api-client";

const RESUS = "/internal/v1/ed/resuscitation";

function unwrap<T>(res: unknown): T {
  const body = res as { data?: T } | T;
  return body && typeof body === "object" && "data" in (body as object)
    ? (body as { data: T }).data
    : (body as T);
}

export async function activateResuscitation(body: Record<string, unknown>) {
  const res = await apiClient.post(`${RESUS}/activate`, body);
  return unwrap<{ id: string }>(res.data);
}

export async function getResuscitation(activationId: string) {
  const res = await apiClient.get(`${RESUS}/${activationId}`);
  return unwrap<Record<string, unknown>>(res.data);
}

export async function logResusAction(activationId: string, body: Record<string, unknown>) {
  return apiClient.post(`${RESUS}/${activationId}/action`, body);
}

export async function recordResuscitation(activationId: string, body: Record<string, unknown>) {
  return apiClient.post(`${RESUS}/${activationId}/record`, body);
}

export async function startResusPhase(activationId: string, body: Record<string, unknown>) {
  const res = await apiClient.post(`${RESUS}/${activationId}/phases`, body);
  return unwrap<{ id: string }>(res.data);
}

export async function endResusPhase(activationId: string, phaseId: string, body: Record<string, unknown>) {
  return apiClient.post(`${RESUS}/${activationId}/phases/${phaseId}/end`, body);
}

export async function listResusPhases(activationId: string) {
  const res = await apiClient.get(`${RESUS}/${activationId}/phases`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
}

export async function addCprCycle(activationId: string, body: Record<string, unknown>) {
  return apiClient.post(`${RESUS}/${activationId}/cpr-cycles`, body);
}

export async function listCprCycles(activationId: string) {
  const res = await apiClient.get(`${RESUS}/${activationId}/cpr-cycles`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
}

export async function addResusMedication(activationId: string, body: Record<string, unknown>) {
  return apiClient.post(`${RESUS}/${activationId}/medications`, body);
}

export async function listResusMedications(activationId: string) {
  const res = await apiClient.get(`${RESUS}/${activationId}/medications`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
}

export async function endResuscitation(activationId: string, body: Record<string, unknown>) {
  return apiClient.post(`${RESUS}/${activationId}/end`, body);
}
