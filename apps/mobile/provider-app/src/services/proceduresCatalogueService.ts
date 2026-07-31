/**
 * Clinical Procedures Pipeline catalogue — BFF `/internal/v1/procedures/**` (engine-not-store).
 *
 * Honesty: failed catalogue/search must throw; callers must never paint failure as "no procedures".
 * Responses are forwarded verbatim by ProceduresController (not `{ data }` wrapped).
 */
import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/procedures";

function buildQuery(params: Record<string, string | undefined>): string {
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v != null && v !== "") qs.set(k, v);
  }
  const s = qs.toString();
  return s ? `?${s}` : "";
}

export interface CatalogueSummary {
  definitionCode: string;
  version?: number;
  clinicalName: string;
  category?: string;
  owningSpecialty?: string;
  purpose?: string;
  permittedSettings?: string[];
  lateralityApplicability?: string;
  requiresSiteSideVerification?: boolean;
  expectedDurationMin?: number | null;
  ziboCode?: string | null;
  [key: string]: unknown;
}

export interface CatalogueListResponse {
  items: CatalogueSummary[];
  matched: number;
  catalogueSize: number;
}

export interface CatalogueDetail {
  definitionCode: string;
  clinicalName: string;
  category?: string;
  owningSpecialty?: string;
  purpose?: string;
  safetyPauseTemplate?: string | null;
  aftercareTemplate?: string | null;
  defaultSedationLevelCode?: string | null;
  defaultRecoverySettingCode?: string | null;
  defaultAftercareTemplateCode?: string | null;
  complicationProfile?: string | null;
  ziboCode?: string | null;
  status?: string;
  requirements?: Record<string, unknown>[];
  [key: string]: unknown;
}

export interface AppropriatenessVerdict {
  outcome: string;
  detectionCount?: number;
  detections?: Record<string, unknown>[];
  [key: string]: unknown;
}

export interface CompetenceVerdict {
  outcome?: string;
  competent?: boolean;
  message?: string;
  [key: string]: unknown;
}

export async function searchCatalogue(filters: {
  specialty?: string;
  category?: string;
  q?: string;
}): Promise<CatalogueListResponse> {
  const res = await apiClient.get<CatalogueListResponse>(
    `${BASE}/catalogue${buildQuery(filters)}`,
    { noRetry: true },
  );
  const body = res.data;
  if (!body || typeof body !== "object") {
    throw new Error("Catalogue response missing body");
  }
  return {
    items: Array.isArray(body.items) ? body.items : [],
    matched: typeof body.matched === "number" ? body.matched : 0,
    catalogueSize: typeof body.catalogueSize === "number" ? body.catalogueSize : 0,
  };
}

export async function getCatalogueDetail(code: string): Promise<CatalogueDetail> {
  return (
    await apiClient.get<CatalogueDetail>(
      `${BASE}/catalogue-detail${buildQuery({ code })}`,
      { noRetry: true },
    )
  ).data;
}

export async function evaluateAppropriateness(
  body: Record<string, unknown>,
): Promise<AppropriatenessVerdict> {
  return (
    await apiClient.post<AppropriatenessVerdict>(`${BASE}/appropriateness/evaluate`, body, {
      noRetry: true,
    })
  ).data;
}

export async function resolveCompetence(
  providerId: string,
  definitionCode: string,
): Promise<CompetenceVerdict> {
  return (
    await apiClient.get<CompetenceVerdict>(
      `${BASE}/competence${buildQuery({ providerId, definitionCode })}`,
      { noRetry: true },
    )
  ).data;
}

export async function getSafetyPauseTemplate(code: string): Promise<Record<string, unknown>> {
  return (
    await apiClient.get<Record<string, unknown>>(
      `${BASE}/safety-pause-templates${buildQuery({ code })}`,
      { noRetry: true },
    )
  ).data;
}

export async function listSedationLevels(): Promise<Record<string, unknown>[]> {
  const res = await apiClient.get<unknown>(`${BASE}/sedation-levels`, { noRetry: true });
  return Array.isArray(res.data) ? (res.data as Record<string, unknown>[]) : [];
}

export async function getSedationLevelDetail(code: string): Promise<Record<string, unknown>> {
  return (
    await apiClient.get<Record<string, unknown>>(
      `${BASE}/sedation-level-detail${buildQuery({ code })}`,
      { noRetry: true },
    )
  ).data;
}

export async function listRecoverySettings(): Promise<Record<string, unknown>[]> {
  const res = await apiClient.get<unknown>(`${BASE}/recovery-settings`, { noRetry: true });
  return Array.isArray(res.data) ? (res.data as Record<string, unknown>[]) : [];
}

export async function getRecoverySettingDetail(code: string): Promise<Record<string, unknown>> {
  return (
    await apiClient.get<Record<string, unknown>>(
      `${BASE}/recovery-setting-detail${buildQuery({ code })}`,
      { noRetry: true },
    )
  ).data;
}

export async function getAftercareTemplate(code: string): Promise<Record<string, unknown>> {
  return (
    await apiClient.get<Record<string, unknown>>(
      `${BASE}/aftercare-templates${buildQuery({ code })}`,
      { noRetry: true },
    )
  ).data;
}

export async function listAnalyticsIndicators(): Promise<Record<string, unknown>> {
  return (
    await apiClient.get<Record<string, unknown>>(`${BASE}/analytics/indicators`, { noRetry: true })
  ).data;
}

export async function getAnalyticsIndicator(code: string): Promise<Record<string, unknown>> {
  return (
    await apiClient.get<Record<string, unknown>>(
      `${BASE}/analytics/indicator${buildQuery({ code })}`,
      { noRetry: true },
    )
  ).data;
}

export async function listClavienDindoGrades(): Promise<Record<string, unknown>[]> {
  const res = await apiClient.get<unknown>(`${BASE}/clavien-dindo-grades`, { noRetry: true });
  return Array.isArray(res.data) ? (res.data as Record<string, unknown>[]) : [];
}

export async function getComplicationProfile(code: string): Promise<Record<string, unknown>> {
  return (
    await apiClient.get<Record<string, unknown>>(
      `${BASE}/complication-profiles${buildQuery({ code })}`,
      { noRetry: true },
    )
  ).data;
}
