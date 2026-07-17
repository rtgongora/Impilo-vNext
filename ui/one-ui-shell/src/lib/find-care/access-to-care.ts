/**
 * Experience UI — Find-care access-to-care actions (authed lane client).
 *
 * These are the resource-moving steps that follow the anonymous find-care search: an appointment
 * REQUEST, a planned patient-transport REQUEST, and their status reads. They call the CITIZEN-gated
 * BFF lane (`/internal/v1/citizen/access-to-care/**`) — the public find-care page routes an
 * anonymous "Book"/"Request transport" tap to sign-in first, so these run only once a person anchor
 * exists. Nothing here fabricates a slot, confirmation, or dispatch: every receipt is the sovereign
 * source's honest reference + pending status.
 */

import { apiClient } from "@/lib/api-client";

/** Honest receipt for a submitted access-to-care request (reference + pending status only). */
export interface AccessToCareReceipt {
  reference: string | null;
  /** Present on transport receipts — the reference used to read status later. */
  transportRef?: string | null;
  status: string | null;
  message?: string | null;
}

/** Citizen-safe transport status view. */
export interface TransportStatus {
  reference: string | null;
  transportRef: string | null;
  status: string | null;
  updatedAt: string | null;
}

export interface AppointmentRequestInput {
  facilityId: string;
  /** Canonical service token (e.g. MATERNITY) or a service label. */
  service: string;
  /** ISO date/datetime the person prefers. */
  preferredDate: string;
  reason?: string;
}

export interface TransportRequestInput {
  facilityId: string;
  facilityLabel?: string;
  /** Optional referral this transport is tied to. */
  referralId?: string;
  /** Where the person should be collected (free text; no precise PII required). */
  pickupLabel?: string;
  notes?: string;
}

/** Submit a governed appointment request. Resolves to the honest REQUESTED receipt. */
export function requestAppointment(input: AppointmentRequestInput): Promise<AccessToCareReceipt> {
  return apiClient.post<AccessToCareReceipt>(
    "/internal/v1/citizen/access-to-care/appointments/request",
    input,
  );
}

/** Submit a planned patient-transport request. Resolves to the honest pending receipt. */
export function requestTransport(input: TransportRequestInput): Promise<AccessToCareReceipt> {
  return apiClient.post<AccessToCareReceipt>(
    "/internal/v1/citizen/access-to-care/transport/request",
    input,
  );
}

/** Read citizen-safe transport status by the transport reference. */
export function getTransportStatus(transportRef: string): Promise<TransportStatus> {
  return apiClient.get<TransportStatus>(
    `/internal/v1/citizen/access-to-care/transport/${encodeURIComponent(transportRef)}`,
  );
}
