/**
 * Vitals Service — API client for vital sign capture and retrieval.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/vitals/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { VitalReading, VitalType } from "../types";

interface VitalResource {
  id: string;
  type: "Vital";
  attributes: {
    encounter_id: string;
    vital_type: VitalType;
    value: number;
    unit: string;
    measured_at: string;
    measured_by: string;
  };
}

function mapVital(r: VitalResource): VitalReading {
  return {
    id: r.id,
    encounterId: r.attributes.encounter_id,
    type: r.attributes.vital_type,
    value: r.attributes.value,
    unit: r.attributes.unit,
    measuredAt: r.attributes.measured_at,
    measuredBy: r.attributes.measured_by,
  };
}

export interface RecordVitalInput {
  encounterId: string;
  type: VitalType;
  value: number;
  unit: string;
}

export async function recordVital(input: RecordVitalInput): Promise<VitalReading> {
  const response = await apiClient.post<{ data: VitalResource }>(
    "/internal/v1/mobile/provider/vitals",
    {
      encounter_id: input.encounterId,
      vital_type: input.type,
      value: input.value,
      unit: input.unit,
    }
  );
  return mapVital(response.data.data);
}

export async function recordVitalsBatch(
  encounterId: string,
  vitals: Omit<RecordVitalInput, "encounterId">[]
): Promise<VitalReading[]> {
  const response = await apiClient.post<{ data: VitalResource[] }>(
    "/internal/v1/mobile/provider/vitals/batch",
    {
      encounter_id: encounterId,
      vitals: vitals.map((v) => ({
        vital_type: v.type,
        value: v.value,
        unit: v.unit,
      })),
    }
  );
  return response.data.data.map(mapVital);
}

export async function getVitalsForEncounter(encounterId: string): Promise<VitalReading[]> {
  const response = await apiClient.get<{ data: VitalResource[] }>(
    `/internal/v1/mobile/provider/vitals?encounter_id=${encounterId}`
  );
  return response.data.data.map(mapVital);
}

export async function getLatestVitals(patientId: string): Promise<VitalReading[]> {
  const response = await apiClient.get<{ data: VitalResource[] }>(
    `/internal/v1/mobile/provider/vitals/latest?patient_id=${patientId}`
  );
  return response.data.data.map(mapVital);
}

export async function deleteVital(id: string): Promise<void> {
  await apiClient.delete(`/internal/v1/mobile/provider/vitals/${id}`);
}
