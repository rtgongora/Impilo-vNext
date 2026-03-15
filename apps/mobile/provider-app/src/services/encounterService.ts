/**
 * Encounter Service — API client for encounter lifecycle.
 *
 * Backend: experience-bff /internal/v1/encounters/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Encounter, EncounterStatus } from "../types";

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
  chiefComplaint?: string;
}

export async function createEncounter(input: CreateEncounterInput): Promise<Encounter> {
  const response = await apiClient.post<{ data: EncounterResource }>(
    "/internal/v1/encounters",
    {
      patient_id: input.patientId,
      facility_id: input.facilityId,
      encounter_type: input.encounterType,
      chief_complaint: input.chiefComplaint,
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
  const response = await apiClient.patch<{ data: EncounterResource }>(
    `/internal/v1/encounters/${encounterId}`,
    { notes }
  );
  return mapEncounter(response.data.data);
}
