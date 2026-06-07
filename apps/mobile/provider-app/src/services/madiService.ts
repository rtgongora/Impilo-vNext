/**
 * MADI provider mobile service — blood orders, transfusions, drives, haemovigilance.
 * BFF: /internal/v1/mobile/provider/madi/**
 */

import { apiClient } from "@impilo/mobile-api-client";

const MADI_BASE = "/internal/v1/mobile/provider/madi";

export interface BloodOrder {
  order_id: string;
  oros_order_ref?: string;
  patient_cpid?: string;
  blood_group?: string;
  component_type?: string;
  units_requested?: number;
  status?: string;
  facility_id?: string;
  ordering_provider?: string;
  created_at?: string;
  updated_at?: string;
}

export interface BloodOrderItem {
  component_type: string;
  blood_group: string;
  quantity?: number;
}

export interface TransfusionEpisode {
  episode_id: string;
  order_id?: string;
  issue_id?: string;
  patient_cpid?: string;
  status?: string;
  started_at?: string;
  completed_at?: string;
  outcome_status?: string;
  outcome_notes?: string;
}

export interface TransfusionObservation {
  observation_id?: string;
  observation_type?: string;
  value_numeric?: number;
  value_text?: string;
  unit?: string;
  observed_at?: string;
}

export interface DonationDriveOperational {
  drive_id: string;
  title?: string;
  drive_type?: string;
  status?: string;
  start_at?: string;
  end_at?: string;
  capacity?: number;
  registered_count?: number;
  collected_count?: number;
}

export interface HaemovigilanceReaction {
  reaction_id?: string;
  episode_id: string;
  reaction_type: string;
  severity: string;
  description?: string;
  reported_at?: string;
}

interface ApiEnvelope<T> {
  data: T;
}

/** List blood orders for the active facility context. */
export async function fetchBloodOrders(params: {
  status?: string;
  patient_cpid?: string;
} = {}): Promise<BloodOrder[]> {
  try {
    const query: string[] = [];
    if (params.status) query.push(`status=${encodeURIComponent(params.status)}`);
    if (params.patient_cpid) query.push(`patient_cpid=${encodeURIComponent(params.patient_cpid)}`);
    const qs = query.length > 0 ? `?${query.join("&")}` : "";
    const response = await apiClient.get<ApiEnvelope<BloodOrder[]>>(`${MADI_BASE}/orders${qs}`);
    return response.data.data ?? [];
  } catch {
    return [];
  }
}

/** Create a blood order from mobile ward/blood-bank workflow. */
export async function createBloodOrder(params: {
  patient_cpid: string;
  blood_group: string;
  component_type: string;
  units_requested?: number;
  oros_order_ref?: string;
  items?: BloodOrderItem[];
}): Promise<BloodOrder | null> {
  try {
    const response = await apiClient.post<ApiEnvelope<BloodOrder>>(`${MADI_BASE}/orders`, params);
    return response.data.data;
  } catch {
    return null;
  }
}

/** Submit an order for crossmatch / fulfilment processing. */
export async function submitBloodOrder(orderId: string): Promise<BloodOrder | null> {
  try {
    const response = await apiClient.post<ApiEnvelope<BloodOrder>>(
      `${MADI_BASE}/orders/${encodeURIComponent(orderId)}/submit`,
      {},
    );
    return response.data.data;
  } catch {
    return null;
  }
}

/** List active or recent transfusion episodes. */
export async function fetchTransfusionEpisodes(params: {
  patient_cpid?: string;
  status?: string;
} = {}): Promise<TransfusionEpisode[]> {
  try {
    const query: string[] = [];
    if (params.patient_cpid) query.push(`patient_cpid=${encodeURIComponent(params.patient_cpid)}`);
    if (params.status) query.push(`status=${encodeURIComponent(params.status)}`);
    const qs = query.length > 0 ? `?${query.join("&")}` : "";
    const response = await apiClient.get<ApiEnvelope<TransfusionEpisode[]>>(`${MADI_BASE}/transfusions${qs}`);
    return response.data.data ?? [];
  } catch {
    return [];
  }
}

/** Start a transfusion episode for an issued unit. */
export async function startTransfusion(params: {
  order_id?: string;
  issue_id?: string;
  patient_cpid: string;
  ward_id?: string;
}): Promise<TransfusionEpisode | null> {
  try {
    const response = await apiClient.post<ApiEnvelope<TransfusionEpisode>>(`${MADI_BASE}/transfusions`, params);
    return response.data.data;
  } catch {
    return null;
  }
}

/** Record a transfusion observation (vitals, reaction signs). */
export async function recordTransfusionObservation(
  episodeId: string,
  params: {
    observation_type: string;
    value_numeric?: number;
    value_text?: string;
    unit?: string;
  },
): Promise<TransfusionObservation | null> {
  try {
    const response = await apiClient.post<ApiEnvelope<TransfusionObservation>>(
      `${MADI_BASE}/transfusions/${encodeURIComponent(episodeId)}/observations`,
      params,
    );
    return response.data.data;
  } catch {
    return null;
  }
}

/** Complete a transfusion episode with outcome. */
export async function completeTransfusion(
  episodeId: string,
  params: { outcome_status: string; outcome_notes?: string },
): Promise<TransfusionEpisode | null> {
  try {
    const response = await apiClient.post<ApiEnvelope<TransfusionEpisode>>(
      `${MADI_BASE}/transfusions/${encodeURIComponent(episodeId)}/complete`,
      params,
    );
    return response.data.data;
  } catch {
    return null;
  }
}

/** List operational donation drives for field capture. */
export async function fetchOperationalDrives(): Promise<DonationDriveOperational[]> {
  try {
    const response = await apiClient.get<ApiEnvelope<DonationDriveOperational[]>>(`${MADI_BASE}/drives`);
    return response.data.data ?? [];
  } catch {
    return [];
  }
}

/** Record screening at a drive (mobile capture). */
export async function screenDonorAtDrive(
  driveId: string,
  params: { donor_id: string; result: string; notes?: string },
): Promise<boolean> {
  try {
    await apiClient.post(`${MADI_BASE}/drives/${encodeURIComponent(driveId)}/screen`, params);
    return true;
  } catch {
    return false;
  }
}

/** Record a blood collection at a drive. */
export async function recordDriveDonation(
  driveId: string,
  params: { donor_id: string; bag_number: string; volume_ml?: number },
): Promise<boolean> {
  try {
    await apiClient.post(`${MADI_BASE}/drives/${encodeURIComponent(driveId)}/donations`, params);
    return true;
  } catch {
    return false;
  }
}

/** Report an adverse transfusion reaction (haemovigilance). */
export async function reportTransfusionReaction(params: {
  episode_id: string;
  reaction_type: string;
  severity: string;
  description?: string;
}): Promise<HaemovigilanceReaction | null> {
  try {
    const response = await apiClient.post<ApiEnvelope<HaemovigilanceReaction>>(
      `${MADI_BASE}/haemovigilance/reactions`,
      params,
    );
    return response.data.data;
  } catch {
    return null;
  }
}
