/**
 * Diagnosis Service — ICD-11 search and diagnosis recording.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/diagnosis/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Diagnosis } from "../types";
import { queueClinicalCreateOnRetryableError } from "./offlineClinicalQueue";

interface DiagnosisResource {
  id: string;
  type: "Diagnosis";
  attributes: {
    encounter_id: string;
    icd_code: string;
    icd_description: string;
    is_primary: boolean;
    notes?: string;
    diagnosed_at: string;
  };
}

function mapDiagnosis(r: DiagnosisResource): Diagnosis {
  return {
    id: r.id,
    encounterId: r.attributes.encounter_id,
    icdCode: r.attributes.icd_code,
    icdDescription: r.attributes.icd_description,
    isPrimary: r.attributes.is_primary,
    notes: r.attributes.notes,
    diagnosedAt: r.attributes.diagnosed_at,
  };
}

export interface ICD11SearchResult {
  code: string;
  description: string;
  chapter: string;
  score: number;
}

export async function searchICD11(query: string): Promise<ICD11SearchResult[]> {
  const response = await apiClient.get<{ data: ICD11SearchResult[] }>(
    `/internal/v1/mobile/provider/diagnosis/icd11/search?q=${encodeURIComponent(query)}`
  );
  return response.data.data;
}

export interface RecordDiagnosisInput {
  encounterId: string;
  icdCode: string;
  icdDescription: string;
  isPrimary: boolean;
  notes?: string;
}

export async function recordDiagnosis(input: RecordDiagnosisInput): Promise<Diagnosis> {
  const payload = {
    encounter_id: input.encounterId,
    icd_code: input.icdCode,
    icd_description: input.icdDescription,
    is_primary: input.isPrimary,
    notes: input.notes,
  };
  try {
    const response = await apiClient.post<{ data: DiagnosisResource }>(
      "/internal/v1/mobile/provider/diagnosis",
      payload
    );
    return mapDiagnosis(response.data.data);
  } catch (error) {
    const offline = await queueClinicalCreateOnRetryableError(error, {
      collection: "clinical_diagnoses",
      path: "/internal/v1/mobile/provider/diagnosis",
      payload,
    });
    if (!offline) throw error;
    return {
      id: offline.recordId,
      encounterId: input.encounterId,
      icdCode: input.icdCode,
      icdDescription: input.icdDescription,
      isPrimary: input.isPrimary,
      notes: input.notes,
      diagnosedAt: new Date().toISOString(),
    };
  }
}

export async function getDiagnosesForEncounter(encounterId: string): Promise<Diagnosis[]> {
  const response = await apiClient.get<{ data: DiagnosisResource[] }>(
    `/internal/v1/mobile/provider/diagnosis?encounter_id=${encounterId}`
  );
  return response.data.data.map(mapDiagnosis);
}

export async function deleteDiagnosis(id: string): Promise<void> {
  await apiClient.delete(`/internal/v1/mobile/provider/diagnosis/${id}`);
}
