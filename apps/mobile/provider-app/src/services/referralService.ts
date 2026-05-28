/**
 * Referral Service — Patient referral management.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/referrals/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Referral, ReferralStatus } from "../types";
import { queueClinicalCreateOnRetryableError } from "./offlineClinicalQueue";

interface ReferralResource {
  id: string;
  type: "Referral";
  attributes: {
    encounter_id: string;
    patient_id: string;
    from_facility_id: string;
    to_facility_id: string;
    to_facility_name: string;
    specialty: string;
    reason: string;
    urgency: "ROUTINE" | "URGENT" | "EMERGENCY";
    status: ReferralStatus;
    notes?: string;
    created_at: string;
  };
}

function mapReferral(r: ReferralResource): Referral {
  return {
    id: r.id,
    encounterId: r.attributes.encounter_id,
    patientId: r.attributes.patient_id,
    fromFacilityId: r.attributes.from_facility_id,
    toFacilityId: r.attributes.to_facility_id,
    toFacilityName: r.attributes.to_facility_name,
    specialty: r.attributes.specialty,
    reason: r.attributes.reason,
    urgency: r.attributes.urgency,
    status: r.attributes.status,
    notes: r.attributes.notes,
    createdAt: r.attributes.created_at,
  };
}

export interface CreateReferralInput {
  encounterId: string;
  patientId: string;
  fromFacilityId: string;
  toFacilityId: string;
  toFacilityName?: string;
  specialty: string;
  reason: string;
  urgency: "ROUTINE" | "URGENT" | "EMERGENCY";
  notes?: string;
}

export async function createReferral(input: CreateReferralInput): Promise<Referral> {
  const payload = {
    encounter_id: input.encounterId,
    patient_id: input.patientId,
    from_facility_id: input.fromFacilityId,
    to_facility_id: input.toFacilityId,
    to_facility_name: input.toFacilityName,
    specialty: input.specialty,
    reason: input.reason,
    urgency: input.urgency,
    notes: input.notes,
  };
  try {
    const response = await apiClient.post<{ data: ReferralResource }>(
      "/internal/v1/mobile/provider/referrals",
      payload
    );
    return mapReferral(response.data.data);
  } catch (error) {
    const offline = await queueClinicalCreateOnRetryableError(error, {
      collection: "clinical_referrals",
      path: "/internal/v1/mobile/provider/referrals",
      payload,
    });
    if (!offline) throw error;
    return {
      id: offline.recordId,
      encounterId: input.encounterId,
      patientId: input.patientId,
      fromFacilityId: input.fromFacilityId,
      toFacilityId: input.toFacilityId,
      toFacilityName: "",
      specialty: input.specialty,
      reason: input.reason,
      urgency: input.urgency,
      status: "PENDING" as ReferralStatus,
      notes: input.notes,
      createdAt: new Date().toISOString(),
    };
  }
}

export async function getReferralsForEncounter(encounterId: string): Promise<Referral[]> {
  const response = await apiClient.get<{ data: ReferralResource[] }>(
    `/internal/v1/mobile/provider/referrals?encounter_id=${encounterId}`
  );
  return response.data.data.map(mapReferral);
}

export async function getReferralsForPatient(patientId: string): Promise<Referral[]> {
  const response = await apiClient.get<{ data: ReferralResource[] }>(
    `/internal/v1/mobile/provider/referrals?patient_id=${patientId}`
  );
  return response.data.data.map(mapReferral);
}

export interface ReferralFacilityCandidate {
  id: string;
  name: string;
}

export async function searchReferralFacilities(query: string): Promise<ReferralFacilityCandidate[]> {
  const q = query.trim();
  if (q.length < 2) return [];
  const response = await apiClient.get<{ data: unknown }>(
    `/internal/v1/teleconsult/routing/facilities?q=${encodeURIComponent(q)}&page=0&size=10`
  );
  const root = response.data.data as Record<string, unknown>;
  const rows = Array.isArray(root) ? root : Array.isArray(root.items) ? root.items : Array.isArray(root.data) ? root.data : [];
  return (rows as Array<Record<string, unknown>>)
    .map((row) => ({
      id: String(row.id ?? row.facilityId ?? row.uuid ?? ""),
      name: String(row.name ?? row.facilityName ?? row.displayName ?? ""),
    }))
    .filter((row) => row.id.length > 0 && row.name.length > 0);
}
