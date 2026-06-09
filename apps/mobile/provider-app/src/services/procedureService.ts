/**
 * Perioperative procedure episode workflow API.
 */
import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/procedures";

export const listProcedureEpisodes = async (patientId: string) =>
  (await apiClient.get<{ data: unknown[] }>(`${BASE}/episodes?patientId=${patientId}`)).data.data;

export const getProcedureEpisode = async (episodeId: string) =>
  (await apiClient.get<{ data: Record<string, unknown> }>(`${BASE}/episodes/${episodeId}`)).data.data;

export const createProcedureEpisode = async (body: Record<string, unknown>) =>
  (await apiClient.post<{ data: { id: string } }>(`${BASE}/episodes`, { placePreopOrders: true, ...body })).data.data;

export const submitProcedurePreop = async (episodeId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/preop`, body);

export const recordAnaesthesiaScore = async (episodeId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/anaesthesia/scores`, body);

export const recordIntraopVitals = async (episodeId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/intraop/vitals`, body);

export const completeChecklistPhase = async (episodeId: string, phase: string, itemIds: string[]) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/checklist/${phase}`, { itemIds, completedBy: "provider-mobile" });

export const requestProcedureConsent = async (episodeId: string) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/consent`, {});

export const syncProcedureConsent = async (episodeId: string) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/consent/sync`, {});

export const provideProcedureConsentExplanation = async (episodeId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/consent/explanation`, body);

export const verifyProcedureConsentIdentity = async (episodeId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/consent/verify-identity`, body);

export const grantProcedureConsent = async (episodeId: string, body?: Record<string, unknown>) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/consent/grant`, body ?? {});

export const createProcedureConsentRemoteSession = async (episodeId: string) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/consent/remote-session`, { channel: "TOKEN_LINK", ttlMinutes: 30 });

export const verifyProcedureConsentRemoteSession = async (
  episodeId: string,
  sessionId: string,
  token: string,
) => apiClient.post(`${BASE}/episodes/${episodeId}/consent/remote-session/${sessionId}/verify`, { token });

export const grantProcedureConsentRemoteSession = async (episodeId: string, sessionId: string) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/consent/remote-session/${sessionId}/grant`, {});

export const startProcedure = async (episodeId: string) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/start`, {});

export const recordConsumable = async (episodeId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/consumables`, body);

export const recordIntraop = async (episodeId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/intraop/events`, body);

export const enterPacu = async (episodeId: string) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/pacu`, { recordedBy: "provider-mobile" });

export const completePostop = async (episodeId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/postop`, body);

export const completeProcedureEpisode = async (episodeId: string) =>
  apiClient.post(`${BASE}/episodes/${episodeId}/complete`, {});
