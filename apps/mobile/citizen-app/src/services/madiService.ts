/**
 * MADI citizen mobile service — blood donor engagement.
 * BFF: /internal/v1/mobile/citizen/madi/**
 */

import { apiClient } from "@impilo/mobile-api-client";

const MADI_BASE = "/internal/v1/mobile/citizen/madi";

export interface DonorProfile {
  donor_id: string;
  person_cpid?: string;
  blood_group?: string;
  rh_factor?: string;
  status?: string;
  facility_id?: string;
  jurisdiction?: string;
  registered_by?: string;
  created_at?: string;
  updated_at?: string;
}

export interface DonationDriveSummary {
  drive_id: string;
  title?: string;
  description?: string;
  drive_type?: string;
  location_ref?: string;
  ndila_site_ref?: string;
  start_at?: string;
  end_at?: string;
  capacity?: number;
  status?: string;
  distance_km?: number;
}

export interface DonorScreening {
  screening_id?: string;
  drive_id?: string;
  result?: string;
  notes?: string;
  screened_at?: string;
}

export interface DonorHistoryEntry {
  donation_id?: string;
  drive_id?: string;
  bag_number?: string;
  volume_ml?: number;
  collected_at?: string;
  facility_id?: string;
}

export interface DonorHistory {
  donations?: DonorHistoryEntry[];
  screenings?: DonorScreening[];
  deferrals?: Array<{ reason_code?: string; deferred_until?: string; notes?: string }>;
}

export interface DonorEligibility {
  eligible?: boolean;
  next_eligible_date?: string;
  reason?: string;
  last_donation_at?: string;
}

export interface DonorPreferences {
  channel?: string;
  enabled?: boolean;
  locale?: string;
}

export interface DonorFeedbackInput {
  drive_id?: string;
  rating?: number;
  comments?: string;
}

interface ApiEnvelope<T> {
  data: T;
}

/** Register the signed-in citizen as a blood donor. */
export async function registerDonor(params: {
  blood_group: string;
  rh_factor?: string;
}): Promise<DonorProfile | null> {
  try {
    const response = await apiClient.post<ApiEnvelope<DonorProfile>>(`${MADI_BASE}/register`, params);
    return response.data.data;
  } catch {
    return null;
  }
}

/** Fetch the citizen's donor profile (resolved from session CPID). */
export async function fetchDonorProfile(): Promise<DonorProfile | null> {
  try {
    const response = await apiClient.get<ApiEnvelope<DonorProfile>>(`${MADI_BASE}/profile`);
    return response.data.data;
  } catch {
    return null;
  }
}

/** Submit pre-donation eligibility screening. */
export async function submitDonorScreening(params: {
  drive_id?: string;
  result: string;
  notes?: string;
}): Promise<DonorScreening | null> {
  try {
    const response = await apiClient.post<ApiEnvelope<DonorScreening>>(`${MADI_BASE}/screenings`, params);
    return response.data.data;
  } catch {
    return null;
  }
}

/** Find published donation drives near an Ndila site reference. */
export async function fetchDrivesNearMe(params: {
  ndila_site_ref: string;
  radius_km?: number;
}): Promise<DonationDriveSummary[]> {
  try {
    const radius = params.radius_km ?? 25;
    const response = await apiClient.get<ApiEnvelope<DonationDriveSummary[]>>(
      `${MADI_BASE}/drives/near-me?ndila_site_ref=${encodeURIComponent(params.ndila_site_ref)}&radius_km=${radius}`,
    );
    return response.data.data ?? [];
  } catch {
    return [];
  }
}

/** Register attendance for a donation drive. */
export async function registerForDrive(driveId: string, slotId?: string): Promise<boolean> {
  try {
    await apiClient.post(`${MADI_BASE}/drives/${encodeURIComponent(driveId)}/register`, {
      slot_id: slotId,
    });
    return true;
  } catch {
    return false;
  }
}

/** Submit post-donation feedback. */
export async function submitDonorFeedback(input: DonorFeedbackInput): Promise<boolean> {
  try {
    await apiClient.post(`${MADI_BASE}/feedback`, input);
    return true;
  } catch {
    return false;
  }
}

/** Update donor communication preferences. */
export async function updateDonorPreferences(prefs: DonorPreferences): Promise<DonorPreferences | null> {
  try {
    const response = await apiClient.put<ApiEnvelope<DonorPreferences>>(`${MADI_BASE}/preferences`, prefs);
    return response.data.data;
  } catch {
    return null;
  }
}

/** Fetch donation and screening history. */
export async function fetchDonorHistory(): Promise<DonorHistory> {
  try {
    const response = await apiClient.get<ApiEnvelope<DonorHistory>>(`${MADI_BASE}/history`);
    return response.data.data ?? {};
  } catch {
    return {};
  }
}

/** Check when the donor may donate again. */
export async function fetchNextEligibility(): Promise<DonorEligibility | null> {
  try {
    const response = await apiClient.get<ApiEnvelope<DonorEligibility>>(`${MADI_BASE}/next-eligibility`);
    return response.data.data;
  } catch {
    return null;
  }
}
