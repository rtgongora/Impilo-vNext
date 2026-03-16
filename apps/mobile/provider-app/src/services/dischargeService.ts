/**
 * Discharge Service — API client for patient discharge workflow.
 *
 * Backend: experience-bff /internal/v1/encounters/:id/discharge
 */

import { apiClient } from "@impilo/mobile-api-client";

export type DischargeType =
  | "routine"
  | "against_medical_advice"
  | "transfer"
  | "deceased"
  | "administrative";

export interface DischargeInput {
  discharge_type: DischargeType;
  diagnosis: string;
  treatment_summary: string;
  follow_up: string;
  medications_at_discharge: string;
  patient_instructions: string;
}

interface DischargeResource {
  id: string;
  type: "Discharge";
  attributes: {
    encounter_id: string;
    discharge_type: DischargeType;
    diagnosis: string;
    treatment_summary: string;
    follow_up: string;
    medications_at_discharge: string;
    patient_instructions: string;
    status: "pending" | "completed" | "cancelled";
    discharged_at?: string;
    created_at: string;
    updated_at: string;
  };
}

export interface Discharge {
  id: string;
  encounterId: string;
  dischargeType: DischargeType;
  diagnosis: string;
  treatmentSummary: string;
  followUp: string;
  medicationsAtDischarge: string;
  patientInstructions: string;
  status: "pending" | "completed" | "cancelled";
  dischargedAt?: string;
  createdAt: string;
  updatedAt: string;
}

function mapDischarge(r: DischargeResource): Discharge {
  return {
    id: r.id,
    encounterId: r.attributes.encounter_id,
    dischargeType: r.attributes.discharge_type,
    diagnosis: r.attributes.diagnosis,
    treatmentSummary: r.attributes.treatment_summary,
    followUp: r.attributes.follow_up,
    medicationsAtDischarge: r.attributes.medications_at_discharge,
    patientInstructions: r.attributes.patient_instructions,
    status: r.attributes.status,
    dischargedAt: r.attributes.discharged_at,
    createdAt: r.attributes.created_at,
    updatedAt: r.attributes.updated_at,
  };
}

export async function submitDischarge(
  encounterId: string,
  data: DischargeInput
): Promise<Discharge> {
  const response = await apiClient.post<{ data: DischargeResource }>(
    `/internal/v1/encounters/${encounterId}/discharge`,
    data
  );
  return mapDischarge(response.data.data);
}

export async function getDischargeStatus(
  encounterId: string
): Promise<Discharge> {
  const response = await apiClient.get<{ data: DischargeResource }>(
    `/internal/v1/encounters/${encounterId}/discharge`
  );
  return mapDischarge(response.data.data);
}
