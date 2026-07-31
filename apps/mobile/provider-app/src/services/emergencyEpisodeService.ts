import { apiClient } from "@impilo/mobile-api-client";
import { queueClinicalCreateOnRetryableError } from "./offlineClinicalQueue";
import { refreshEmergencyOutboxBadge } from "./emergencyOutboxService";

const BASE = "/internal/v1/emergency-episodes";

export type EmergencyEpisodeRow = Record<string, unknown>;
export type EmergencyHandoverRow = Record<string, unknown>;
export type EmergencyAlertRow = Record<string, unknown>;
export type EmergencyOrderSetRow = Record<string, unknown>;
export type EmergencyMedicationRow = Record<string, unknown>;
export type EmergencyObservationStayRow = Record<string, unknown>;
export type EmergencyIdentityLinkRow = Record<string, unknown>;
export type EmergencyDispositionRow = Record<string, unknown>;

function unwrap<T>(res: unknown): T {
  const body = res as { data?: T } | T;
  return body && typeof body === "object" && "data" in (body as object)
    ? (body as { data: T }).data
    : (body as T);
}

async function postWithQueue(path: string, payload: Record<string, unknown>, collection: string) {
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

export async function listEmergencyEpisodes(facilityId: string): Promise<EmergencyEpisodeRow[]> {
  const res = await apiClient.get(`${BASE}?facilityId=${encodeURIComponent(facilityId)}`);
  return unwrap<EmergencyEpisodeRow[]>(res.data) ?? [];
}

export async function getEmergencyEpisode(episodeId: string): Promise<EmergencyEpisodeRow> {
  const res = await apiClient.get(`${BASE}/${episodeId}`);
  return unwrap<EmergencyEpisodeRow>(res.data);
}

export async function openEmergencyEpisode(body: Record<string, unknown>) {
  return postWithQueue(BASE, body, "emergency_episodes");
}

export async function arriveEmergencyEpisode(episodeId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/${episodeId}/arrive`, body, "emergency_episodes");
}

export async function transitionEmergencyEpisode(episodeId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/${episodeId}/transition`, body, "emergency_episodes");
}

export async function confirmEmergencyLocation(episodeId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/${episodeId}/location`, body, "emergency_episodes");
}

export async function listEmergencyHandovers(episodeId: string): Promise<EmergencyHandoverRow[]> {
  const res = await apiClient.get(`${BASE}/${episodeId}/handovers`);
  return unwrap<EmergencyHandoverRow[]>(res.data) ?? [];
}

export async function requestEmergencyHandover(episodeId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/${episodeId}/handover`, body, "emergency_episodes");
}

export async function acceptEmergencyHandover(handoverId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/handovers/${handoverId}/accept`, body, "emergency_episodes");
}

export async function declineEmergencyHandover(handoverId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/handovers/${handoverId}/decline`, body, "emergency_episodes");
}

export async function expireEmergencyHandover(handoverId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/handovers/${handoverId}/expire`, body, "emergency_episodes");
}

export async function getEmergencyDisposition(episodeId: string): Promise<EmergencyDispositionRow | null> {
  const res = await apiClient.get(`${BASE}/${episodeId}/disposition`);
  return unwrap<EmergencyDispositionRow>(res.data);
}

export async function recordEmergencyDisposition(episodeId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/${episodeId}/disposition`, body, "emergency_episodes");
}

export async function listObservationStays(episodeId: string): Promise<EmergencyObservationStayRow[]> {
  const res = await apiClient.get(`${BASE}/${episodeId}/observation-stays`);
  return unwrap<EmergencyObservationStayRow[]>(res.data) ?? [];
}

export async function startObservationStay(episodeId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/${episodeId}/observation-stays`, body, "emergency_episodes");
}

export async function endObservationStay(stayId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/observation-stays/${stayId}/end`, body, "emergency_episodes");
}

export async function listIdentityLinks(episodeId: string): Promise<EmergencyIdentityLinkRow[]> {
  const res = await apiClient.get(`${BASE}/${episodeId}/identity-link`);
  return unwrap<EmergencyIdentityLinkRow[]>(res.data) ?? [];
}

export async function linkEmergencyIdentity(episodeId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/${episodeId}/identity-link`, body, "emergency_episodes");
}

export async function listOrderSets(episodeId: string): Promise<EmergencyOrderSetRow[]> {
  const res = await apiClient.get(`${BASE}/${episodeId}/order-sets`);
  return unwrap<EmergencyOrderSetRow[]>(res.data) ?? [];
}

export async function invokeOrderSet(episodeId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/${episodeId}/order-sets`, body, "emergency_episodes");
}

export async function orderSetItem(itemId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/order-sets/items/${itemId}/order`, body, "emergency_episodes");
}

export async function declineOrderSetItem(itemId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/order-sets/items/${itemId}/decline`, body, "emergency_episodes");
}

export async function listEpisodeMedications(episodeId: string): Promise<EmergencyMedicationRow[]> {
  const res = await apiClient.get(`${BASE}/${episodeId}/medications`);
  return unwrap<EmergencyMedicationRow[]>(res.data) ?? [];
}

export async function recordEpisodeMedication(episodeId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/${episodeId}/medications`, body, "emergency_episodes");
}

export async function listEpisodeAlerts(episodeId: string): Promise<EmergencyAlertRow[]> {
  const res = await apiClient.get(`${BASE}/${episodeId}/alerts`);
  return unwrap<EmergencyAlertRow[]>(res.data) ?? [];
}

export async function acknowledgeAlert(alertId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/alerts/${alertId}/acknowledge`, body, "emergency_episodes");
}

export async function respondAlert(alertId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/alerts/${alertId}/respond`, body, "emergency_episodes");
}

export async function closeAlert(alertId: string, body: Record<string, unknown>) {
  return postWithQueue(`${BASE}/alerts/${alertId}/close`, body, "emergency_episodes");
}
