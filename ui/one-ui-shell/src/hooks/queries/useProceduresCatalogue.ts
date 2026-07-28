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
  /** Specific per-procedure safety-pause code (V002/V003) — resolve via useSafetyPauseTemplate. */
  safetyPauseTemplate: string | null;
  /**
   * Specific per-procedure aftercare code (V002/V003) — resolve via useAftercareTemplate FIRST.
   * Many procedures declare this with no matching template row yet (a real, named catalogue-
   * depth debt, not a bug) — fall back to `defaultAftercareTemplateCode` only when this either
   * is null or fails to resolve, never silently prefer the coarser one.
   */
  aftercareTemplate: string | null;
  /** Wave P7 — resolve via useSedationLevel. Null when this procedure declares no default depth. */
  defaultSedationLevelCode: string | null;
  /** Wave P9 — resolve via useRecoverySetting. */
  defaultRecoverySettingCode: string | null;
  /** Wave P9 — the coarse setting-class FALLBACK for aftercareTemplate above, not the primary source. */
  defaultAftercareTemplateCode: string | null;
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

// ── Wave P-R2 — P7 safety-pause/sedation and P9 recovery/aftercare hooks. Same
// empty/unknown/unavailable discipline as everything above: apiClient throws on non-2xx, so
// isError is always the real "could not read this" signal, never a resolved empty value. ──

export interface ConfirmationItemView {
  confirmationItem: string;
  promptText: string;
}

export interface SafetyPauseTemplateView {
  templateCode: string;
  templateName: string;
  applicableSetting: string | null;
  description: string | null;
  status: string;
  approvingAuthority: string | null;
  confirmationItems: ConfirmationItemView[];
}

export interface RescueCapability {
  levelCode: string;
  levelLabel: string;
  monitoringRequired: string;
  providerCompetenceRequired: string;
}

export interface SedationLevelView {
  levelCode: string;
  levelLabel: string;
  depthRank: number;
  monitoringRequired: string;
  providerCompetenceRequired: string;
  typicalRecoveryCriteria: string | null;
  rescueCapability: RescueCapability | null;
}

export interface RecoverySettingView {
  settingCode: string;
  settingLabel: string;
  minimumObservationMinutes: number | null;
  dischargeReadinessCriteria: string;
  monitoringRequired: string;
}

export interface AftercareInstructionView {
  instructionKind: string;
  instructionText: string;
}

export interface AftercareTemplateView {
  templateCode: string;
  templateName: string;
  applicableSetting: string | null;
  description: string | null;
  status: string;
  approvingAuthority: string | null;
  /** ENGINEERING_SEED | RATIFIED — see V006/V007's own honesty note; render ENGINEERING_SEED visibly. */
  contentMaturity: string;
  instructions: AftercareInstructionView[];
  deliveryChannels: string[];
}

export function useSafetyPauseTemplate(templateCode: string | null) {
  return useQuery<SafetyPauseTemplateView>({
    queryKey: ["procedures", "safety-pause-template", templateCode],
    queryFn: () =>
      apiClient.get<SafetyPauseTemplateView>(
        `/internal/v1/procedures/safety-pause-templates${buildQuery({ code: templateCode ?? undefined })}`,
      ),
    enabled: !!templateCode,
  });
}

export function useSedationLevels() {
  return useQuery<SedationLevelView[]>({
    queryKey: ["procedures", "sedation-levels"],
    queryFn: () => apiClient.get<SedationLevelView[]>("/internal/v1/procedures/sedation-levels"),
  });
}

export function useSedationLevel(levelCode: string | null) {
  return useQuery<SedationLevelView>({
    queryKey: ["procedures", "sedation-level", levelCode],
    queryFn: () =>
      apiClient.get<SedationLevelView>(
        `/internal/v1/procedures/sedation-level-detail${buildQuery({ code: levelCode ?? undefined })}`,
      ),
    enabled: !!levelCode,
  });
}

export function useRecoverySetting(settingCode: string | null) {
  return useQuery<RecoverySettingView>({
    queryKey: ["procedures", "recovery-setting", settingCode],
    queryFn: () =>
      apiClient.get<RecoverySettingView>(
        `/internal/v1/procedures/recovery-setting-detail${buildQuery({ code: settingCode ?? undefined })}`,
      ),
    enabled: !!settingCode,
  });
}

/**
 * Resolves an aftercare template by code — call with the SPECIFIC per-procedure code
 * (`CatalogueDetail.aftercareTemplate`) first; if that is null or this query errors as
 * not-found, the caller falls back to `defaultAftercareTemplateCode`. See the procedures
 * detail page for the two-step resolution this hook is deliberately too narrow to do itself —
 * a hook that silently tried both would hide from the caller which one actually answered.
 */
export function useAftercareTemplate(templateCode: string | null) {
  return useQuery<AftercareTemplateView>({
    queryKey: ["procedures", "aftercare-template", templateCode],
    queryFn: () =>
      apiClient.get<AftercareTemplateView>(
        `/internal/v1/procedures/aftercare-templates${buildQuery({ code: templateCode ?? undefined })}`,
      ),
    enabled: !!templateCode,
    retry: false,
  });
}

// ── Wave SB-3 — P14 analytics indicator catalogue. Indicator DEFINITIONS and their
// computation status, not computed numbers — computationStatus/gapReason are the honest
// "this number does not exist yet" fields and must be rendered, not hidden. Same
// empty/unknown/unavailable discipline as everything above. ──

export interface IndicatorView {
  indicatorCode: string;
  indicatorName: string;
  numeratorDescription: string;
  denominatorDescription: string;
  /** COMPUTED | PARTIAL | NOT_YET_INSTRUMENTED — render honestly; PARTIAL/NOT_YET are not failures. */
  computationStatus: string;
  executableVia: string | null;
  /** Why this indicator cannot be computed yet — the named gap, shown verbatim. */
  gapReason: string | null;
  owningService: string | null;
  delegatedOutOfScope: boolean;
  sourceCitation: string | null;
}

export interface IndicatorSummary {
  total: number;
  computed: number;
  partial: number;
  notYetInstrumented: number;
  indicators: IndicatorView[];
}

export function useAnalyticsIndicators() {
  return useQuery<IndicatorSummary>({
    queryKey: ["procedures", "analytics-indicators"],
    queryFn: () =>
      apiClient.get<IndicatorSummary>("/internal/v1/procedures/analytics/indicators"),
  });
}

export function useAnalyticsIndicator(indicatorCode: string | null) {
  return useQuery<IndicatorView>({
    queryKey: ["procedures", "analytics-indicator", indicatorCode],
    queryFn: () =>
      apiClient.get<IndicatorView>(
        `/internal/v1/procedures/analytics/indicator${buildQuery({ code: indicatorCode ?? undefined })}`,
      ),
    enabled: !!indicatorCode,
  });
}
