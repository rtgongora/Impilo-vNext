/**
 * Encounter Structured Forms Service — patient/cadre/setting-aware resolver + response lifecycle.
 *
 * Backend: experience-bff /internal/v1/encounters/{id}/forms/* and /internal/v1/form-responses/*
 * (which proxy canonical PCT). Responses are version-locked, extractable and audited server-side.
 */

import { apiClient } from "@impilo/mobile-api-client";

export interface FormObligation {
  formKey: string;
  formSchemaId: string;
  formVersion: number;
  formSchemaVersionId: string;
  name: string;
  obligation: string;
  requiredWorkflow?: string | null;
  requiresCountersign: boolean;
  reason?: string | null;
}

export interface FormResolution {
  mandatory: FormObligation[];
  recommended: FormObligation[];
  optional: FormObligation[];
  prohibited: FormObligation[];
  countersignRequired: FormObligation[];
  auditRef?: string;
}

export interface FormCatalogEntry {
  formKey: string;
  name: string;
  version: number;
  formSchemaVersionId: string;
  requiresCountersign: boolean;
  sensitivity?: string;
  definitionJson: string;
}

export interface FormResponse {
  responseId: string;
  formKey: string;
  formVersion: number;
  status: string;
  countersignRequired: boolean;
  [key: string]: unknown;
}

export interface ResolveParams {
  cadre?: string;
  role?: string;
  careSetting?: string;
  context?: string;
  acuity?: number;
  specialty?: string;
  cpid?: string;
  ageMonths?: number;
  sex?: string;
  pregnant?: boolean;
  programmes?: string[];
}

export async function resolveEncounterForms(
  encounterId: string,
  params: ResolveParams,
): Promise<FormResolution> {
  const response = await apiClient.post<{ data: FormResolution }>(
    `/internal/v1/encounters/${encodeURIComponent(encounterId)}/forms/resolve`,
    params,
  );
  return response.data.data;
}

export async function getFormCatalog(): Promise<FormCatalogEntry[]> {
  const response = await apiClient.get<{ data: FormCatalogEntry[] }>(
    "/internal/v1/extensions/forms/catalog",
  );
  return response.data.data;
}

export async function listEncounterFormResponses(encounterId: string): Promise<FormResponse[]> {
  const response = await apiClient.get<{ data: FormResponse[] }>(
    `/internal/v1/encounters/${encodeURIComponent(encounterId)}/form-responses`,
  );
  return response.data.data;
}

export async function createFormDraft(
  encounterId: string,
  formKey: string,
  answers: Record<string, unknown>,
): Promise<FormResponse> {
  const response = await apiClient.post<{ data: FormResponse }>(
    `/internal/v1/encounters/${encodeURIComponent(encounterId)}/forms/${encodeURIComponent(formKey)}/responses/draft`,
    { answers },
  );
  return response.data.data;
}

export async function submitFormResponse(responseId: string): Promise<FormResponse> {
  const response = await apiClient.post<{ data: FormResponse }>(
    `/internal/v1/form-responses/${encodeURIComponent(responseId)}/submit`,
    {},
  );
  return response.data.data;
}

/** Fill-and-submit: create a version-locked draft then submit it in one call. */
export async function submitEncounterForm(
  encounterId: string,
  formKey: string,
  answers: Record<string, unknown>,
): Promise<FormResponse> {
  const draft = await createFormDraft(encounterId, formKey, answers);
  return submitFormResponse(draft.responseId);
}
