/**
 * Experience UI — Clinical Procedures Pipeline catalogue and appropriateness query hooks.
 *
 * Backed by the experience-bff procedures proxy (`/internal/v1/procedures/**`), which forwards
 * to procedures-service. Read/evaluate only — procedures-service is engine-not-store, so there
 * are no mutation hooks here and there will not be while that boundary holds.
 *
 * EMPTY VS UNKNOWN VS UNAVAILABLE. `apiClient` throws on a non-2xx response, so a downstream
 * failure surfaces as `isError`/`error` on the query, never as a resolved empty result. Every
 * hook here returns that state distinctly rather than defaulting a failed fetch to `[]` — the
 * failure mode this file exists to avoid is documented at `useCatalogueSearch`, because it has
 * already happened once in this estate: a BFF 502 rendered as "no conditions" for every patient
 * on the adult problem list because a client fell through `data ?? []` on error.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface CatalogueSummary {
  definitionCode: string;
  version: number;
  clinicalName: string;
  category: string;
  owningSpecialty: string;
  purpose: string;
  permittedSettings: string[];
  lateralityApplicability: string;
  requiresSiteSideVerification: boolean;
  expectedDurationMin: number | null;
  ziboCode: string | null;
}

/**
 * `catalogueSize` reports how many published definitions exist irrespective of the current
 * filter — the field that lets a caller distinguish "your filter matched nothing" from "the
 * catalogue is empty" or, upstream of both, "the catalogue could not be read at all". See
 * `useCatalogueSearch`'s consumer contract below for how the three are told apart.
 */
export interface CatalogueListResponse {
  items: CatalogueSummary[];
  matched: number;
  catalogueSize: number;
}

export interface RequirementView {
  requirementKind: string;
  requirementCode: string;
  requirementLabel: string;
  obligation: string;
  conditionExpression: string | null;
  ownerRole: string;
  resolverService: string | null;
  overridableInEmergency: boolean;
  onResolverUnavailable: string;
}

export interface CatalogueDetail {
  definitionCode: string;
  version: number;
  clinicalName: string;
  synonyms: string[];
  category: string;
  owningSpecialty: string;
  purpose: string;
  permittedSettings: string[];
  anatomicalSite: string | null;
  lateralityApplicability: string;
  ageMinDays: number | null;
  ageMaxDays: number | null;
  pregnancyApplicability: string;
  expectedDurationMin: number | null;
  recoveryRequired: boolean;
  consentType: string | null;
  ziboCode: string | null;
  snomedCtCode: string | null;
  status: string;
  approvingAuthority: string | null;
  sourceCitation: string | null;
  requirements: RequirementView[];
}

export interface Detection {
  code: string;
  disposition: "CLARIFY" | "PROPOSE_ALTERNATIVE" | "BLOCK";
  severity: string;
  message: string;
  suggestedAction: string;
}

export interface AppropriatenessVerdict {
  outcome: "APPROPRIATE" | "PROCEED_WITH_CLARIFICATION" | "BLOCKED";
  detectionCount: number;
  detections: Detection[];
}

export interface AppropriatenessRequest {
  definitionCode: string;
  setting?: string | null;
  anatomicalSite?: string | null;
  side?: string | null;
  requestedFor?: string | null;
  patientAgeDays?: number | null;
  pregnant?: boolean | null;
  onAnticoagulant?: boolean | null;
  patientIdentityConfirmed?: boolean | null;
  openRequestExists?: boolean | null;
  lastCompletedOn?: string | null;
  otherOpenProcedureCodes?: string[] | null;
  completedProcedureCodes?: string[] | null;
  facilitySupportsProcedure?: boolean | null;
  requiredSpecialistAvailable?: boolean | null;
  requiredEquipmentAvailable?: boolean | null;
}

export interface CompetenceVerdict {
  providerId: string;
  definitionCode: string;
  capacity: "INDEPENDENT" | "SUPERVISED" | "NOT_PERMITTED" | "UNKNOWN";
  mayProceedAlone: boolean;
  supervisingProviderPublicId: string | null;
  countersignatureRequired: boolean;
  reasons: string[];
}

function buildQuery(params: Record<string, string | undefined>): string {
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v) qs.set(k, v);
  }
  const s = qs.toString();
  return s ? `?${s}` : "";
}

/**
 * Catalogue search.
 *
 * Consumer contract (this is the point of the hook, not a comment to skim): render three
 * distinct states, never collapse them —
 *   - `isError` → "the catalogue could not be reached" (an error banner, never an empty list)
 *   - `!isError && data.catalogueSize === 0` → "the catalogue is genuinely empty"
 *   - `!isError && data.catalogueSize > 0 && data.matched === 0` → "no results for this filter"
 * A component that renders `data?.items ?? []` without checking `isError` first reintroduces
 * the exact bug this hook is written to prevent.
 */
export function useCatalogueSearch(filters: { specialty?: string; category?: string; q?: string }) {
  return useQuery<CatalogueListResponse>({
    queryKey: ["procedures", "catalogue", filters],
    queryFn: () =>
      apiClient.get<CatalogueListResponse>(
        `/internal/v1/procedures/catalogue${buildQuery(filters)}`,
      ),
  });
}

export function useCatalogueDetail(definitionCode: string | null) {
  return useQuery<CatalogueDetail>({
    queryKey: ["procedures", "catalogue-detail", definitionCode],
    queryFn: () =>
      apiClient.get<CatalogueDetail>(
        `/internal/v1/procedures/catalogue-detail${buildQuery({ code: definitionCode ?? undefined })}`,
      ),
    enabled: !!definitionCode,
  });
}

/**
 * Appropriateness evaluation. Not a `useMutation`: evaluating is read-only (engine-not-store —
 * nothing is written by this call), and `useQuery` gives the empty/unknown/unavailable states
 * for free rather than requiring the caller to track them by hand.
 */
export function useAppropriatenessCheck(request: AppropriatenessRequest | null) {
  return useQuery<AppropriatenessVerdict>({
    queryKey: ["procedures", "appropriateness", request],
    queryFn: () =>
      apiClient.post<AppropriatenessVerdict>(
        "/internal/v1/procedures/appropriateness/evaluate",
        request,
      ),
    enabled: !!request?.definitionCode,
  });
}

export function useCompetence(providerId: string | null, definitionCode: string | null) {
  return useQuery<CompetenceVerdict>({
    queryKey: ["procedures", "competence", providerId, definitionCode],
    queryFn: () =>
      apiClient.get<CompetenceVerdict>(
        `/internal/v1/procedures/competence${buildQuery({
          providerId: providerId ?? undefined,
          definitionCode: definitionCode ?? undefined,
        })}`,
      ),
    enabled: !!providerId && !!definitionCode,
  });
}
