/**
 * Vitals Service — API client for vital sign capture and retrieval.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/vitals/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { VitalReading, VitalType } from "../types";
import { queueClinicalCreateOnRetryableError } from "./offlineClinicalQueue";

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
  const attributes = (r.attributes ?? (r as unknown)) as Record<string, unknown>;
  return {
    id: r.id ?? String(attributes.id ?? `vital-${Date.now()}`),
    encounterId: String(attributes.encounter_id ?? attributes.encounterId ?? ""),
    type: String(attributes.vital_type ?? attributes.vitalType ?? "HEART_RATE") as VitalType,
    value: Number(attributes.value ?? attributes.heart_rate ?? attributes.temperature ?? attributes.systolic ?? 0),
    unit: String(attributes.unit ?? ""),
    measuredAt: String(attributes.measured_at ?? attributes.recordedAt ?? attributes.created_at ?? new Date().toISOString()),
    measuredBy: String(attributes.measured_by ?? attributes.recorded_by ?? "provider-app"),
  };
}

export interface RecordVitalInput {
  encounterId: string;
  patientId: string;
  type: VitalType;
  value: number;
  unit: string;
}

export async function recordVital(input: RecordVitalInput): Promise<VitalReading> {
  const payload = {
    encounter_id: input.encounterId,
    patient_id: input.patientId,
    vital_type: input.type,
    value: input.value,
    unit: input.unit,
  };
  try {
    const response = await apiClient.post<{ data: VitalResource }>(
      "/internal/v1/mobile/provider/vitals",
      payload
    );
    return mapVital(response.data.data);
  } catch (error) {
    const offline = await queueClinicalCreateOnRetryableError(error, {
      collection: "clinical_vitals",
      path: "/internal/v1/mobile/provider/vitals",
      payload,
    });
    if (!offline) throw error;
    return {
      id: offline.recordId,
      encounterId: input.encounterId,
      type: input.type,
      value: input.value,
      unit: input.unit,
      measuredAt: new Date().toISOString(),
      measuredBy: "provider-app",
    };
  }
}

export async function recordVitalsBatch(
  encounterId: string,
  patientId: string,
  vitals: Omit<RecordVitalInput, "encounterId" | "patientId">[]
): Promise<VitalReading[]> {
  const response = await apiClient.post<{ data: VitalResource[] }>(
    "/internal/v1/mobile/provider/vitals/batch",
    {
      vitals: vitals.map((v) => ({
        encounter_id: encounterId,
        patient_id: patientId,
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
