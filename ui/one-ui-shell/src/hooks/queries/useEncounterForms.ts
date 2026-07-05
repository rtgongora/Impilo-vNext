/**
 * Experience UI — Patient Encounter Structured Forms hooks.
 *
 * Resolver + response lifecycle wired to the BFF encounter-forms API (which proxies canonical PCT).
 * The resolver is patient/cadre/setting-aware; responses are version-locked, extractable and audited
 * server-side — these hooks never fabricate clinical state.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

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
  formSchemaId: string;
  formKey: string;
  name: string;
  version: number;
  formSchemaVersionId: string;
  requiredWorkflow?: string | null;
  obligationDefault?: string;
  requiresCountersign: boolean;
  sensitivity?: string;
  definitionJson: string;
}

export interface FormResponseResource {
  responseId: string;
  formKey: string;
  formVersion: number;
  formSchemaVersionId: string;
  status: string;
  countersignRequired: boolean;
  answers?: unknown;
  questionnaireResponse?: string | null;
  [key: string]: unknown;
}

export interface ResolveParams {
  cadre?: string;
  role?: string;
  scope?: string;
  visitType?: string;
  acuity?: number;
  context?: string;
  accessState?: string;
  careSetting?: string;
  careStage?: string;
  specialty?: string;
  ageMonths?: number;
  sex?: string;
  pregnant?: boolean;
  programmes?: string[];
  conditionCodes?: string[];
  cpid?: string;
}

const RESPONSES_KEY = (encounterId: string) => ["encounter-form-responses", encounterId] as const;
const EXTRACTIONS_KEY = (encounterId: string) => ["encounter-form-extractions", encounterId] as const;

/** Resolve the mandatory/recommended/optional/prohibited/countersign form set for an encounter. */
export function useEncounterFormResolution(
  encounterId: string | undefined,
  params: ResolveParams,
  enabled = true,
) {
  return useQuery({
    queryKey: ["encounter-forms-resolve", encounterId, params],
    enabled: Boolean(encounterId) && enabled,
    queryFn: () =>
      apiClient.post<ApiResponse<FormResolution>>(
        `/internal/v1/encounters/${encodeURIComponent(encounterId!)}/forms/resolve`,
        params,
      ),
  });
}

/** The clinical form catalog (definitions incl. DAK JSON), keyed by formKey for lookup. */
export function useFormCatalog() {
  return useQuery({
    queryKey: ["forms-catalog"],
    queryFn: () => apiClient.get<ApiResponse<FormCatalogEntry[]>>("/internal/v1/extensions/forms/catalog"),
  });
}

/** One extraction provenance row — where a form answer was routed and its honest status. */
export interface FormExtractedResource {
  extractedId: string;
  responseId: string;
  resourceType: string;
  routeTarget: string;
  /** CONFIRMED | ROUTED | PENDING | FAILED */
  status: string;
  externalRef?: string | null;
  localRef?: string | null;
  failureReason?: string | null;
  resourcePayload?: string | null;
  createdAt?: string;
}

/**
 * Extraction provenance for the encounter: problems/care-plans written in PCT, OROS orders
 * (incl. medication requests) ROUTED or FAILED, BUTANO observations PENDING the SHR bridge.
 */
export function useEncounterFormExtractions(encounterId: string | undefined) {
  return useQuery({
    queryKey: EXTRACTIONS_KEY(encounterId ?? ""),
    enabled: Boolean(encounterId),
    queryFn: () =>
      apiClient.get<ApiResponse<FormExtractedResource[]>>(
        `/internal/v1/encounters/${encodeURIComponent(encounterId!)}/form-extractions`,
      ),
  });
}

export function useEncounterFormResponses(encounterId: string | undefined) {
  return useQuery({
    queryKey: RESPONSES_KEY(encounterId ?? ""),
    enabled: Boolean(encounterId),
    queryFn: () =>
      apiClient.get<ApiResponse<FormResponseResource[]>>(
        `/internal/v1/encounters/${encodeURIComponent(encounterId!)}/form-responses`,
      ),
  });
}

export function useCreateFormDraft(encounterId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ formKey, answers }: { formKey: string; answers?: Record<string, unknown> }) =>
      apiClient.post<ApiResponse<FormResponseResource>>(
        `/internal/v1/encounters/${encodeURIComponent(encounterId)}/forms/${encodeURIComponent(formKey)}/responses/draft`,
        { answers: answers ?? {} },
      ),
    onSuccess: () => void qc.invalidateQueries({ queryKey: RESPONSES_KEY(encounterId) }),
  });
}

export function useUpdateFormAnswers() {
  return useMutation({
    mutationFn: ({ responseId, answers }: { responseId: string; answers: Record<string, unknown> }) =>
      apiClient.put<ApiResponse<FormResponseResource>>(
        `/internal/v1/form-responses/${encodeURIComponent(responseId)}`,
        { answers },
      ),
  });
}

export function useSubmitFormResponse(encounterId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ responseId, attestation }: { responseId: string; attestation?: string }) =>
      apiClient.post<ApiResponse<FormResponseResource>>(
        `/internal/v1/form-responses/${encodeURIComponent(responseId)}/submit`,
        { attestation },
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: RESPONSES_KEY(encounterId) });
      void qc.invalidateQueries({ queryKey: EXTRACTIONS_KEY(encounterId) });
    },
  });
}

export function useCountersignFormResponse(encounterId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ responseId, attestation }: { responseId: string; attestation?: string }) =>
      apiClient.post<ApiResponse<FormResponseResource>>(
        `/internal/v1/form-responses/${encodeURIComponent(responseId)}/countersign`,
        { attestation },
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: RESPONSES_KEY(encounterId) });
      void qc.invalidateQueries({ queryKey: EXTRACTIONS_KEY(encounterId) });
    },
  });
}

/** Create a draft then submit in one call — the common "fill and submit" path. */
export function useSubmitEncounterForm(encounterId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ formKey, answers }: { formKey: string; answers: Record<string, unknown> }) => {
      const draft = await apiClient.post<ApiResponse<FormResponseResource>>(
        `/internal/v1/encounters/${encodeURIComponent(encounterId)}/forms/${encodeURIComponent(formKey)}/responses/draft`,
        { answers },
      );
      const responseId = draft.data.responseId;
      await apiClient.put<ApiResponse<FormResponseResource>>(
        `/internal/v1/form-responses/${encodeURIComponent(responseId)}`,
        { answers },
      );
      return apiClient.post<ApiResponse<FormResponseResource>>(
        `/internal/v1/form-responses/${encodeURIComponent(responseId)}/submit`,
        {},
      );
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: RESPONSES_KEY(encounterId) });
      void qc.invalidateQueries({ queryKey: EXTRACTIONS_KEY(encounterId) });
    },
  });
}
