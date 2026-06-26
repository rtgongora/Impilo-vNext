/**
 * Rito Safety Service — Provider workspace safety reporting (Rito: Quality,
 * Safety & Client Voice).
 *
 * Backend: experience-bff RitoPersonaController
 *   POST /internal/v1/mobile/rito/work/cases   — report a safety incident / near-miss
 *   GET  /internal/v1/work/rito/cases?assigned_to={id} — my assigned cases
 *
 * The persona controller forwards raw rito-quality-safety-service payloads
 * (a CaseEntity / list of CaseEntity), so responses are NOT wrapped in an
 * ApiEnvelope — apiClient returns the case object(s) directly on `response.data`.
 */

import { apiClient } from "@impilo/mobile-api-client";
import { authStore } from "@impilo/mobile-auth";

/**
 * Resolve the signed-in provider's actor id for "my cases" filtering.
 * Mirrors the prescriber-id resolution used elsewhere in the provider app.
 */
export function currentProviderActorId(): string | null {
  const session = authStore.getState().session;
  return session?.providerId ?? session?.actorId ?? null;
}

/** Provider-reportable safety case types. */
export type RitoSafetyCaseType =
  | "SAFETY_INCIDENT"
  | "NEAR_MISS"
  | "SAFETY_CONCERN"
  | "ADVERSE_EVENT";

export type RitoSeverity = "LOW" | "MODERATE" | "HIGH" | "CRITICAL";

export interface ReportIncidentInput {
  caseType: RitoSafetyCaseType;
  title: string;
  description: string;
  severity: RitoSeverity;
}

/**
 * Rito case as serialized by rito-quality-safety-service CaseEntity
 * (Jackson camelCase). Only the fields the provider surfaces are declared;
 * optional fields may be absent for freshly-created cases.
 */
export interface RitoSafetyCase {
  id: string;
  caseReference: string;
  caseType: string;
  category?: string | null;
  title?: string | null;
  description?: string | null;
  severity?: string;
  status: string;
  source?: string;
  assignedToActorId?: string | null;
  assignedToActorType?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export async function reportIncident(input: ReportIncidentInput): Promise<RitoSafetyCase> {
  const response = await apiClient.post<RitoSafetyCase>("/internal/v1/mobile/rito/work/cases", {
    caseType: input.caseType,
    title: input.title,
    description: input.description,
    severity: input.severity,
    source: "PROVIDER_WORKSPACE",
  });
  return response.data;
}

export async function myCases(assignedTo: string): Promise<RitoSafetyCase[]> {
  const response = await apiClient.get<RitoSafetyCase[]>(
    `/internal/v1/work/rito/cases?assigned_to=${encodeURIComponent(assignedTo)}`,
  );
  return response.data ?? [];
}
