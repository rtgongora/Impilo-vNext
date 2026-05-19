/**
 * Nhume citizen-app service.
 *
 * Talks to the Nhume backend through the BFF mobile surface. Provides a
 * thin wrapper used by the citizen-facing tracking, creation and proof
 * flows. Existing `citizenDeliveryService` continues to power the legacy
 * marketplace request UI; this module adds the new Nhume entry points so
 * the citizen app can request, track and confirm any Nhume delivery.
 */

import { apiClient } from "@impilo/mobile-api-client";

const NHUME_BASE = "/internal/v1/mobile/citizen/nhume";

export interface NhumeCitizenDelivery {
  delivery_id: string;
  reference: string;
  status: string;
  priority?: string;
  origin_label?: string;
  destination_label?: string;
  sla_due_at?: string;
  updated_at?: string;
  delivery_type?: string;
  is_demo?: boolean;
}

export interface NhumeTrackingEvent {
  latitude?: number;
  longitude?: number;
  recorded_at?: string;
  source?: string;
}

export interface NhumeTimelineEvent {
  status: string;
  recorded_at?: string;
  notes?: string;
}

/** Get the citizen's deliveries (privacy-safe view). */
export async function listNhumeDeliveries(): Promise<NhumeCitizenDelivery[]> {
  try {
    const response = await apiClient.get<{ data: NhumeCitizenDelivery[] }>(`${NHUME_BASE}/deliveries`);
    return response.data.data ?? [];
  } catch {
    return [];
  }
}

/** Get a single delivery summary. */
export async function getNhumeDelivery(id: string): Promise<NhumeCitizenDelivery | null> {
  try {
    const response = await apiClient.get<{ data: NhumeCitizenDelivery }>(`${NHUME_BASE}/deliveries/${encodeURIComponent(id)}`);
    return response.data.data;
  } catch {
    return null;
  }
}

/** Get the status timeline. */
export async function getNhumeTimeline(id: string): Promise<NhumeTimelineEvent[]> {
  try {
    const response = await apiClient.get<{ data: NhumeTimelineEvent[] }>(
      `${NHUME_BASE}/deliveries/${encodeURIComponent(id)}/timeline`,
    );
    return response.data.data ?? [];
  } catch {
    return [];
  }
}

/** Get tracking events (recipient sees coarse-grained recent points only). */
export async function getNhumeTracking(id: string): Promise<NhumeTrackingEvent[]> {
  try {
    const response = await apiClient.get<{ data: NhumeTrackingEvent[] }>(
      `${NHUME_BASE}/deliveries/${encodeURIComponent(id)}/tracking`,
    );
    return response.data.data ?? [];
  } catch {
    return [];
  }
}

export interface NhumeDeliveryCreateInput {
  delivery_type: string;
  destination_label: string;
  destination_address?: string;
  pickup_label?: string;
  item_description: string;
  cold_chain?: boolean;
  notes?: string;
  preferred_channel?: "SMS" | "PUSH" | "WHATSAPP" | "EMAIL" | "VOICE" | "IN_APP";
}

/** Citizen-initiated delivery request (medicine refill, sample pickup, etc.). */
export async function createNhumeDelivery(input: NhumeDeliveryCreateInput): Promise<NhumeCitizenDelivery | null> {
  try {
    const response = await apiClient.post<{ data: NhumeCitizenDelivery }>(`${NHUME_BASE}/deliveries`, input);
    return response.data.data;
  } catch {
    return null;
  }
}

/** Confirm receipt by OTP. */
export async function confirmNhumeOtp(id: string, otp: string): Promise<boolean> {
  try {
    await apiClient.post(`${NHUME_BASE}/deliveries/${encodeURIComponent(id)}/proof`, {
      method: "OTP",
      otp_code: otp,
    });
    return true;
  } catch {
    return false;
  }
}

/** Cancel a not-yet-dispatched delivery. */
export async function cancelNhumeDelivery(id: string, reason?: string): Promise<boolean> {
  try {
    await apiClient.post(`${NHUME_BASE}/deliveries/${encodeURIComponent(id)}/cancel`, { reason });
    return true;
  } catch {
    return false;
  }
}
