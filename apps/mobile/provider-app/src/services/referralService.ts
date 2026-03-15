/**
 * Referral Service — Patient referral management.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/referrals/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Referral, ReferralStatus } from "../types";

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
  specialty: string;
  reason: string;
  urgency: "ROUTINE" | "URGENT" | "EMERGENCY";
  notes?: string;
}

export async function createReferral(input: CreateReferralInput): Promise<Referral> {
  const response = await apiClient.post<{ data: ReferralResource }>(
    "/internal/v1/mobile/provider/referrals",
    {
      encounter_id: input.encounterId,
      patient_id: input.patientId,
      from_facility_id: input.fromFacilityId,
      to_facility_id: input.toFacilityId,
      specialty: input.specialty,
      reason: input.reason,
      urgency: input.urgency,
      notes: input.notes,
    }
  );
  return mapReferral(response.data.data);
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
