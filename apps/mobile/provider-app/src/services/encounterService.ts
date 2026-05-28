/**
 * Encounter Service — API client for encounter lifecycle.
 *
 * Backend: experience-bff /internal/v1/encounters/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Encounter, EncounterStatus } from "../types";
import { queueClinicalCreateOnRetryableError } from "./offlineClinicalQueue";

interface EncounterResource {
  id: string;
  type: "Encounter";
  attributes: {
    facility_id: string;
    patient_id: string;
    shift_id?: string;
    encounter_type: string;
    status: EncounterStatus;
    chief_complaint?: string;
    diagnosis?: string;
    notes?: string;
    vitals?: unknown;
    started_at: string;
    ended_at?: string;
    created_at: string;
    updated_at: string;
  };
}

interface EncounterListResponse {
  data: EncounterResource[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

function mapEncounter(r: EncounterResource): Encounter {
  return {
    id: r.id,
    journeyId: String((r.attributes as Record<string, unknown>).journey_id ?? (r.attributes as Record<string, unknown>).journeyId ?? ""),
    patientId: r.attributes.patient_id,
    facilityId: r.attributes.facility_id,
    shiftId: r.attributes.shift_id,
    encounterType: r.attributes.encounter_type,
    status: r.attributes.status,
    chiefComplaint: r.attributes.chief_complaint,
    diagnosis: r.attributes.diagnosis,
    notes: r.attributes.notes,
    vitals: Array.isArray(r.attributes.vitals) ? r.attributes.vitals as Encounter["vitals"] : [],
    startedAt: r.attributes.started_at,
    endedAt: r.attributes.ended_at,
    createdAt: r.attributes.created_at,
    updatedAt: r.attributes.updated_at,
  };
}

export async function listEncounters(
  patientId?: string,
  page = 0,
  size = 20
): Promise<{ encounters: Encounter[]; totalElements: number }> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (patientId) params.set("patient_id", patientId);
  const response = await apiClient.get<EncounterListResponse>(
    `/internal/v1/encounters?${params.toString()}`
  );
  return {
    encounters: response.data.data.map(mapEncounter),
    totalElements: response.data.meta.page.total_elements,
  };
}

export async function getEncounter(id: string): Promise<Encounter> {
  const response = await apiClient.get<{ data: EncounterResource }>(
    `/internal/v1/encounters/${id}`
  );
  return mapEncounter(response.data.data);
}

export interface CreateEncounterInput {
  patientId: string;
  facilityId: string;
  encounterType: string;
  pctJourneyId: string;
  chiefComplaint?: string;
}

export async function createEncounter(input: CreateEncounterInput): Promise<Encounter> {
  const response = await apiClient.post<{ data: EncounterResource }>(
    "/internal/v1/mobile/provider/encounters",
    {
      patient_id: input.patientId,
      facility_id: input.facilityId,
      encounter_type: input.encounterType,
      pct_journey_id: input.pctJourneyId,
      chief_complaint: input.chiefComplaint,
      encounter_context: "MOBILE_PROVIDER",
      entry_point: "PROVIDER_APP",
      modality: "IN_PERSON",
      care_setting: "OUTPATIENT",
      priority: "ROUTINE",
    }
  );
  return mapEncounter(response.data.data);
}

export async function closeEncounter(
  id: string,
  diagnosis?: string
): Promise<Encounter> {
  const response = await apiClient.post<{ data: EncounterResource }>(
    `/internal/v1/encounters/${id}/close`,
    diagnosis ? { diagnosis } : undefined
  );
  return mapEncounter(response.data.data);
}

export async function addEncounterNotes(
  encounterId: string,
  notes: string
): Promise<Encounter> {
  const payload = { notes };
  try {
    const response = await apiClient.patch<{ data: EncounterResource }>(
      `/internal/v1/encounters/${encounterId}`,
      payload
    );
    return mapEncounter(response.data.data);
  } catch (error) {
    const offline = await queueClinicalCreateOnRetryableError(error, {
      collection: "clinical_encounter_notes",
      path: `/internal/v1/encounters/${encounterId}`,
      payload,
      method: "PATCH",
      operationType: "UPDATE",
      recordId: `encounter-${encounterId}`,
    });
    if (!offline) throw error;
    return {
      id: encounterId,
      journeyId: "",
      patientId: "",
      facilityId: "",
      encounterType: "GENERAL",
      status: "IN_PROGRESS" as EncounterStatus,
      notes,
      vitals: [],
      startedAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
  }
}
