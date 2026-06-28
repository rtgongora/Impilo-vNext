/**
 * Inpatient Service — Care plans, fluid balance, emergency, EWS, admissions,
 * ward rounds, observations, transfers.
 */
import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1";

// Care Plans — outpatient (encounter/journey-scoped) care plans are owned by pct; the provider
// mobile app routes them via the pct-backed mobile route (/clinical/care-plans), NOT the shared
// inpatient /care-plans route (which the web EHR uses and which requires an admission context).
export const fetchCarePlans = async (patientId: string) => (await apiClient.get<{ data: unknown[] }>(`${V1}/clinical/care-plans?patientId=${patientId}`)).data.data;
export const createCarePlan = async (body: Record<string, unknown>) => (await apiClient.post<{ data: { id: string } }>(`${V1}/clinical/care-plans`, body)).data.data;

// Fluid Balance
export const fetchFluidBalance = async (patientId: string, date?: string) => (await apiClient.get<{ data: unknown[]; summary: Record<string, number> }>(`${V1}/fluid-balance?patientId=${patientId}${date ? `&date=${date}` : ""}`)).data;
export const recordFluid = async (body: Record<string, unknown>) => (await apiClient.post<{ data: { id: string } }>(`${V1}/fluid-balance`, body)).data.data;

// Emergency
export const listEmergencyActivations = async () => (await apiClient.get<{ data: unknown[] }>(`${V1}/emergency/activations`)).data.data;
export const activateEmergency = async (body: Record<string, unknown>) => (await apiClient.post<{ data: { id: string } }>(`${V1}/emergency/activate`, body)).data.data;
export const logEmergencyAction = async (id: string, body: Record<string, string>) => apiClient.post(`${V1}/emergency/${id}/action`, body);
export const endEmergency = async (id: string, body: Record<string, string>) => apiClient.post(`${V1}/emergency/${id}/end`, body);
export const recordResuscitation = async (activationId: string, body: Record<string, unknown>) => (await apiClient.post<{ data: { id: string } }>(`${V1}/emergency/${activationId}/resuscitation`, body)).data.data;

// APGAR
export const recordApgar = async (body: Record<string, unknown>) => (await apiClient.post<{ data: { id: string } }>(`${V1}/apgar`, body)).data.data;
export const fetchApgar = async (patientId: string) => (await apiClient.get<{ data: unknown[] }>(`${V1}/apgar?patientId=${patientId}`)).data.data;

// EWS
export const recordEWS = async (body: Record<string, unknown>) => (await apiClient.post<{ data: { id: string; riskLevel: string; escalationRequired: boolean } }>(`${V1}/ews`, body)).data.data;
export const fetchEWS = async (patientId: string) => (await apiClient.get<{ data: unknown[] }>(`${V1}/ews?patientId=${patientId}`)).data.data;

// Admissions
export const createAdmission = async (body: Record<string, unknown>) => (await apiClient.post<{ data: { id: string } }>(`${V1}/admissions`, body)).data.data;
export const listAdmissions = async (patientId?: string) => (await apiClient.get<{ data: unknown[] }>(`${V1}/admissions${patientId ? `?patientId=${patientId}` : ""}`)).data.data;

// Ward Rounds
export const startWardRound = async (body: Record<string, unknown>) => (await apiClient.post<{ data: { id: string } }>(`${V1}/ward-rounds`, body)).data.data;
export const addRoundEntry = async (roundId: string, body: Record<string, unknown>) => (await apiClient.post<{ data: { id: string } }>(`${V1}/ward-rounds/${roundId}/entries`, body)).data.data;
export const listWardRounds = async (wardId: string) => (await apiClient.get<{ data: unknown[] }>(`${V1}/ward-rounds?wardId=${wardId}`)).data.data;

// Observations
export const recordObservation = async (body: Record<string, unknown>) => (await apiClient.post<{ data: { id: string } }>(`${V1}/observations`, body)).data.data;
export const fetchObservations = async (patientId: string) => (await apiClient.get<{ data: unknown[] }>(`${V1}/observations?patientId=${patientId}`)).data.data;

// Transfers
export const requestTransfer = async (body: Record<string, unknown>) => (await apiClient.post<{ data: { id: string } }>(`${V1}/transfers`, body)).data.data;
export const acceptTransfer = async (id: string, body: Record<string, string>) => apiClient.post(`${V1}/transfers/${id}/accept`, body);
export const listTransfers = async (patientId?: string) => (await apiClient.get<{ data: unknown[] }>(`${V1}/transfers${patientId ? `?patientId=${patientId}` : ""}`)).data.data;

// Handover / takeover
export const submitHandover = async (body: Record<string, unknown>) =>
  (await apiClient.post<{ data: { id: string; status: string } }>(`${V1}/inpatient/handover`, body)).data.data;
export const acceptTakeover = async (handoverId: string, body: Record<string, unknown>) =>
  (await apiClient.post<{ data: { id: string; status: string } }>(`${V1}/inpatient/handover/${handoverId}/takeover`, body)).data.data;

// Ward charts
export const fetchWardChartEntries = async (chartType: string, patientId: string) =>
  (await apiClient.get<{ data: unknown[] }>(`${V1}/ward-charts/${chartType}/entries?patientId=${patientId}`)).data.data;
export const recordWardChartEntry = async (chartType: string, body: Record<string, unknown>) =>
  (await apiClient.post<{ data: { id: string } }>(`${V1}/ward-charts/${chartType}/entries`, body)).data.data;

// Ward alerts (provider)
export const listWardAlerts = async (wardId: string) =>
  (await apiClient.get<{ data: unknown[] }>(`${V1}/ward-alerts?wardId=${wardId}`)).data.data;
export const acknowledgeWardAlert = async (alertId: string, body: Record<string, string>) =>
  apiClient.post(`${V1}/ward-alerts/${alertId}/acknowledge`, body);
