/**
 * Prescription Service — Rx creation and management.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/prescriptions/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Prescription, PrescriptionStatus } from "../types";

interface PrescriptionResource {
  id: string;
  type: "Prescription";
  attributes: {
    encounter_id: string;
    patient_id: string;
    medication: string;
    dosage: string;
    frequency: string;
    duration: string;
    quantity: number;
    instructions?: string;
    status: PrescriptionStatus;
    prescribed_at: string;
    prescribed_by: string;
  };
}

function mapPrescription(r: PrescriptionResource): Prescription {
  return {
    id: r.id,
    encounterId: r.attributes.encounter_id,
    patientId: r.attributes.patient_id,
    medication: r.attributes.medication,
    dosage: r.attributes.dosage,
    frequency: r.attributes.frequency,
    duration: r.attributes.duration,
    quantity: r.attributes.quantity,
    instructions: r.attributes.instructions,
    status: r.attributes.status,
    prescribedAt: r.attributes.prescribed_at,
    prescribedBy: r.attributes.prescribed_by,
  };
}

export interface CreatePrescriptionInput {
  encounterId: string;
  patientId: string;
  medication: string;
  dosage: string;
  frequency: string;
  duration: string;
  quantity: number;
  instructions?: string;
}

export async function createPrescription(input: CreatePrescriptionInput): Promise<Prescription> {
  const response = await apiClient.post<{ data: PrescriptionResource }>(
    "/internal/v1/mobile/provider/prescriptions",
    {
      encounter_id: input.encounterId,
      patient_id: input.patientId,
      medication: input.medication,
      dosage: input.dosage,
      frequency: input.frequency,
      duration: input.duration,
      quantity: input.quantity,
      instructions: input.instructions,
    }
  );
  return mapPrescription(response.data.data);
}

export async function getPrescriptionsForEncounter(encounterId: string): Promise<Prescription[]> {
  const response = await apiClient.get<{ data: PrescriptionResource[] }>(
    `/internal/v1/mobile/provider/prescriptions?encounter_id=${encounterId}`
  );
  return response.data.data.map(mapPrescription);
}

export async function getPrescriptionsForPatient(patientId: string): Promise<Prescription[]> {
  const response = await apiClient.get<{ data: PrescriptionResource[] }>(
    `/internal/v1/mobile/provider/prescriptions?patient_id=${patientId}`
  );
  return response.data.data.map(mapPrescription);
}

export async function cancelPrescription(id: string): Promise<Prescription> {
  const response = await apiClient.post<{ data: PrescriptionResource }>(
    `/internal/v1/mobile/provider/prescriptions/${id}/cancel`
  );
  return mapPrescription(response.data.data);
}
