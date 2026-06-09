import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/ed";

export const listEdVisits = async (facilityId?: string) => {
  const qs = facilityId ? `?facilityId=${facilityId}` : "";
  return (await apiClient.get<{ data: Record<string, unknown>[] }>(`${BASE}/visits${qs}`)).data.data;
};

export const openEdVisit = async (body: Record<string, unknown>) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(`${BASE}/visits`, body)).data;

export const getEdVisit = async (visitId: string) =>
  apiClient.get<{ data: Record<string, unknown> }>(`${BASE}/visits/${visitId}`);

export const edTriage = async (visitId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/visits/${visitId}/triage`, body);

export const edActivateTrauma = async (visitId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/visits/${visitId}/trauma/activate`, body);

export const edTraumaSurvey = async (visitId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/visits/${visitId}/trauma/survey`, body);

export const edDisposition = async (visitId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/visits/${visitId}/disposition`, body);

export const edStartEncounter = async (visitId: string) =>
  apiClient.post(`${BASE}/visits/${visitId}/encounter`, {});

export const scoreEdTriage = async (body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/triage/score`, body);

export const edTriageDiscriminators = async () =>
  apiClient.get(`${BASE}/triage/discriminators`);
