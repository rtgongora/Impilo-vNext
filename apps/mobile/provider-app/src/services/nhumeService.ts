/**
 * Nhume provider-app service.
 *
 * Used by the courier mode in the provider app (drivers, CHW delivery agents,
 * facility runners, drone/robot operators). Calls go through the provider BFF
 * surface for Nhume; the BFF handles Trust-Layer header injection and
 * delegates to services/nhume-service.
 */

import { apiClient } from "@impilo/mobile-api-client";

const NHUME_BASE = "/internal/v1/mobile/provider/nhume";

export interface NhumeCourierDelivery {
  delivery_id: string;
  reference: string;
  status: string;
  priority?: string;
  delivery_type?: string;
  origin_label?: string;
  destination_label?: string;
  recipient_label?: string;
  sla_due_at?: string;
  cold_chain_required?: boolean;
  chain_of_custody_required?: boolean;
}

export interface NhumeProofPayload {
  method: "OTP" | "QR_CODE_SCAN" | "RECIPIENT_SIGNATURE" | "FACILITY_STAMP" | "PHOTO" | "GEOFENCE" | "BIOMETRIC" | "PROVIDER_CONFIRMATION" | "NOMPILO" | "WEBHOOK" | "DRONE_LANDING" | "ROBOT_BAY" | "AV_LOCKER";
  evidence_ref?: string;
  otp_code?: string;
  recipient_name?: string;
  notes?: string;
  captured_lat?: number;
  captured_lng?: number;
}

export interface NhumeCustodyPayload {
  event_kind: "PREPARED" | "PACKED" | "SEALED" | "RELEASED" | "PICKED_UP" | "TRANSFERRED" | "ARRIVED_HUB" | "DEPARTED_HUB" | "DELIVERED" | "RETURNED" | "DAMAGED" | "LOST" | "TEMP_BREACH" | "DISPUTED" | "RECONCILED";
  custody_holder_ref?: string;
  custody_location_label?: string;
  seal_id?: string;
  temperature_c?: number;
  evidence_ref?: string;
  exception?: boolean;
  notes?: string;
}

export interface NhumeLocationPayload {
  latitude: number;
  longitude: number;
  accuracy_m?: number;
  heading?: number;
  speed_mps?: number;
  source?: string;
}

export async function listAssignedDeliveries(): Promise<NhumeCourierDelivery[]> {
  try {
    const res = await apiClient.get<{ data: NhumeCourierDelivery[] }>(`${NHUME_BASE}/assigned`);
    return res.data.data ?? [];
  } catch {
    return [];
  }
}

export async function acceptDelivery(id: string): Promise<boolean> {
  try {
    await apiClient.post(`${NHUME_BASE}/deliveries/${encodeURIComponent(id)}/accept`, {});
    return true;
  } catch {
    return false;
  }
}

export async function declineDelivery(id: string, reason: string): Promise<boolean> {
  try {
    await apiClient.post(`${NHUME_BASE}/deliveries/${encodeURIComponent(id)}/decline`, { reason });
    return true;
  } catch {
    return false;
  }
}

export async function startPickup(id: string): Promise<boolean> {
  try {
    await apiClient.post(`${NHUME_BASE}/deliveries/${encodeURIComponent(id)}/pickup`, {});
    return true;
  } catch {
    return false;
  }
}

export async function startTransit(id: string): Promise<boolean> {
  try {
    await apiClient.post(`${NHUME_BASE}/deliveries/${encodeURIComponent(id)}/start`, {});
    return true;
  } catch {
    return false;
  }
}

export async function recordLocation(id: string, body: NhumeLocationPayload): Promise<boolean> {
  try {
    await apiClient.post(`${NHUME_BASE}/deliveries/${encodeURIComponent(id)}/location`, body);
    return true;
  } catch {
    return false;
  }
}

export async function captureProof(id: string, body: NhumeProofPayload): Promise<boolean> {
  try {
    await apiClient.post(`${NHUME_BASE}/deliveries/${encodeURIComponent(id)}/proof`, body);
    return true;
  } catch {
    return false;
  }
}

export async function recordCustody(id: string, body: NhumeCustodyPayload): Promise<boolean> {
  try {
    await apiClient.post(`${NHUME_BASE}/deliveries/${encodeURIComponent(id)}/custody`, body);
    return true;
  } catch {
    return false;
  }
}

export async function reportFailure(id: string, reason: string): Promise<boolean> {
  try {
    await apiClient.post(`${NHUME_BASE}/deliveries/${encodeURIComponent(id)}/fail`, { reason });
    return true;
  } catch {
    return false;
  }
}
