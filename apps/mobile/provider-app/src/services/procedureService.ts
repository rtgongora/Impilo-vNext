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

// ── Theatre & perioperative depth (WS#6 theatre seam) ──────────────────────────
// Composed via the BFF, which proxies inpatient-service's /internal/v1/theatre/** module.
const THEATRE = "/internal/v1/theatre";

export const listTheatreQueue = async () =>
  (await apiClient.get<{ data: unknown[] }>(`${THEATRE}/queue`)).data.data;

/** Theatre case detail — case id IS the procedure episode id (BFF GET /cases/{id}). */
export const getTheatreCase = async (caseId: string) =>
  (await apiClient.get<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}`)).data.data;

export const intakeTheatreCase = async (body: Record<string, unknown>) =>
  (await apiClient.post<{ data: { id: string } }>(`${THEATRE}/cases`, body)).data.data;

export const setTheatreTriage = async (caseId: string, body: Record<string, unknown>) =>
  apiClient.post(`${THEATRE}/cases/${caseId}/triage`, body);

export const evaluateTheatreReadiness = async (caseId: string, body: Record<string, unknown> = {}) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}/readiness`, body)).data.data;

export const listTheatreReadiness = async (caseId: string) =>
  (await apiClient.get<{ data: unknown[] }>(`${THEATRE}/cases/${caseId}/readiness`)).data.data;

export const bookTheatreCase = async (caseId: string, body: Record<string, unknown> = {}) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}/book`, body)).data.data;

export const startTheatreCase = async (caseId: string, body: Record<string, unknown> = {}) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}/start`, body)).data.data;

export const draftTheatreNote = async (caseId: string, body: Record<string, unknown>) =>
  (await apiClient.post<{ data: { id: string } }>(`${THEATRE}/cases/${caseId}/note`, body)).data.data;

export const signTheatreNote = async (caseId: string, signedProviderId: string) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}/note/sign`, { signedProviderId })).data.data;

export const getTheatreNote = async (caseId: string) =>
  (await apiClient.get<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}/note`)).data.data;

export const recordTheatrePacuDisposition = async (caseId: string, body: Record<string, unknown>) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}/pacu/disposition`, body)).data.data;

export const cancelTheatreCase = async (caseId: string, reason: string) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}/cancel`, { reason })).data.data;

export const reportTheatreSafetyEvent = async (caseId: string, body: Record<string, unknown>) =>
  (await apiClient.post<{ data: { id: string } }>(`${THEATRE}/cases/${caseId}/safety-events`, body)).data.data;

export const listTheatreSafetyEvents = async (caseId: string) =>
  (await apiClient.get<{ data: unknown[] }>(`${THEATRE}/cases/${caseId}/safety-events`)).data.data;

export const routeTheatreDeath = async (caseId: string, body: Record<string, unknown> = {}) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}/death`, body)).data.data;

// ── Wave M2 bedside OR panels (BFF /internal/v1/theatre/** + inventory implant SoR) ──

/** Confirm marked operative site / laterality (gates lateralised start). */
export const confirmTheatreSiteSide = async (caseId: string, body: Record<string, unknown>) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}/site-side`, body)).data.data;

export const listTheatreSpecimens = async (caseId: string) =>
  (await apiClient.get<{ data: unknown[] }>(`${THEATRE}/cases/${caseId}/specimens`)).data.data;

export const collectTheatreSpecimen = async (
  caseId: string,
  specimenId: string,
  body: Record<string, unknown> = {},
) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(
    `${THEATRE}/cases/${caseId}/specimens/${specimenId}/collect`,
    body,
  )).data.data;

export const confirmTheatreSpecimenLabel = async (caseId: string, specimenId: string) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(
    `${THEATRE}/cases/${caseId}/specimens/${specimenId}/confirm-label`,
    {},
  )).data.data;

export const receiveTheatreSpecimen = async (caseId: string, specimenId: string) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(
    `${THEATRE}/cases/${caseId}/specimens/${specimenId}/receive`,
    {},
  )).data.data;

export const assessTheatreSpecimenAdequacy = async (
  caseId: string,
  specimenId: string,
  body: Record<string, unknown>,
) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(
    `${THEATRE}/cases/${caseId}/specimens/${specimenId}/adequacy`,
    body,
  )).data.data;

export const listTheatreCounts = async (caseId: string) =>
  (await apiClient.get<{ data: unknown[] }>(`${THEATRE}/cases/${caseId}/counts`)).data.data;

export const recordTheatreCount = async (caseId: string, body: Record<string, unknown>) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}/counts`, body)).data.data;

export const listTheatreImplants = async (caseId: string) =>
  (await apiClient.get<{ data: unknown[] }>(`${THEATRE}/cases/${caseId}/implants`)).data.data;

export const recordTheatreImplant = async (caseId: string, body: Record<string, unknown>) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}/implants`, body)).data.data;

/** Inventory SoR — never theatre/implants/recall (that path swallows failures to []). */
const INVENTORY_IMPLANTS = "/internal/v1/inventory/implants";

export const removeTheatreImplant = async (
  patientImplantId: string,
  body: Record<string, unknown> = {},
) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(
    `${INVENTORY_IMPLANTS}/${patientImplantId}/remove`,
    body,
  )).data.data;

export const reviseTheatreImplant = async (
  patientImplantId: string,
  body: Record<string, unknown>,
) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(
    `${INVENTORY_IMPLANTS}/${patientImplantId}/revise`,
    body,
  )).data.data;

export const traceImplantRecall = async (params: { udi?: string; lot?: string } = {}) => {
  const qs = new URLSearchParams();
  if (params.udi) qs.set("udi", params.udi);
  if (params.lot) qs.set("lot", params.lot);
  const suffix = qs.toString() ? `?${qs.toString()}` : "";
  return (await apiClient.get<{ data: unknown[] }>(`${INVENTORY_IMPLANTS}/recall${suffix}`)).data.data;
};

export const listTheatreBlood = async (caseId: string) =>
  (await apiClient.get<{ data: unknown[] }>(`${THEATRE}/cases/${caseId}/blood`)).data.data;

export const theatreBloodAction = async (
  caseId: string,
  action: "request" | "issue" | "administer" | "resolve",
  body: Record<string, unknown> = {},
) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(
    `${THEATRE}/cases/${caseId}/blood/${action}`,
    body,
  )).data.data;

/** Activate a new emergency theatre case (intake path — not in-case mutation). */
export const activateEmergencyTheatreCase = async (body: Record<string, unknown>) =>
  (await apiClient.post<{ data: { id: string } }>(`${THEATRE}/cases/emergency`, body)).data.data;

export const recordTheatreEmergencyConsentException = async (
  caseId: string,
  body: Record<string, unknown>,
) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(
    `${THEATRE}/cases/${caseId}/consent/emergency-exception`,
    body,
  )).data.data;

export const recordReturnToTheatre = async (caseId: string, body: Record<string, unknown>) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(
    `${THEATRE}/cases/${caseId}/return-to-theatre`,
    body,
  )).data.data;

// PACU recovery depth (Aldrete-scored observations + readiness / escalate / discharge)
export const listTheatrePacuObservations = async (caseId: string) =>
  (await apiClient.get<{ data: unknown[] }>(`${THEATRE}/cases/${caseId}/pacu/observations`)).data.data;

export const recordTheatrePacuObservation = async (caseId: string, body: Record<string, unknown>) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(
    `${THEATRE}/cases/${caseId}/pacu/observations`,
    body,
  )).data.data;

export const getTheatrePacuReadiness = async (caseId: string) =>
  (await apiClient.get<{ data: Record<string, unknown> }>(`${THEATRE}/cases/${caseId}/pacu/readiness`)).data.data;

export const escalateTheatrePacu = async (caseId: string, body: Record<string, unknown>) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(
    `${THEATRE}/cases/${caseId}/pacu/escalate`,
    body,
  )).data.data;

export const dischargeTheatrePacu = async (caseId: string, body: Record<string, unknown>) =>
  (await apiClient.post<{ data: Record<string, unknown> }>(
    `${THEATRE}/cases/${caseId}/pacu/discharge`,
    body,
  )).data.data;
