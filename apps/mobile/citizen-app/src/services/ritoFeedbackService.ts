/**
 * Rito Feedback Service — Citizen client-voice (Quality, Safety & Client Voice).
 *
 * Backend: experience-bff RitoPersonaController
 *   POST /internal/v1/mobile/rito/cases            — submit feedback
 *   GET  /internal/v1/mobile/rito/cases/{id}        — track a case
 *   GET  /internal/v1/mobile/rito/cases/{id}/timeline — case lifecycle events
 *
 * The persona controller forwards the raw downstream rito-quality-safety-service
 * payloads (a CaseEntity / list of CaseEventEntity), so responses are NOT wrapped
 * in an ApiEnvelope — apiClient returns the case object directly on `response.data`.
 */

import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/mobile/rito/cases";

/** Citizen-submittable case types (client-voice pillar). */
export type RitoCaseType = "COMPLAINT" | "COMPLIMENT" | "SUGGESTION" | "SAFETY_CONCERN";

export interface SubmitFeedbackInput {
  caseType: RitoCaseType;
  title: string;
  description: string;
  anonymous: boolean;
}

/**
 * Rito case as serialized by rito-quality-safety-service CaseEntity.
 * Fields are camelCase (Jackson default). Optional fields may be absent
 * for freshly-created cases, so all but identity/status are optional.
 */
export interface RitoCase {
  id: string;
  caseReference: string;
  caseType: string;
  pillar?: string;
  category?: string | null;
  title?: string | null;
  description?: string | null;
  severity?: string;
  status: string;
  source?: string;
  anonymous?: boolean;
  resolutionSummary?: string | null;
  closureOutcome?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/** Rito case timeline event as serialized by CaseEventEntity. */
export interface RitoCaseEvent {
  id: string;
  caseId: string;
  eventType: string;
  fromStatus?: string | null;
  toStatus?: string | null;
  actorType?: string | null;
  note?: string | null;
  occurredAt: string;
}

export async function submitFeedback(input: SubmitFeedbackInput): Promise<RitoCase> {
  const response = await apiClient.post<RitoCase>(V1, {
    caseType: input.caseType,
    title: input.title,
    description: input.description,
    anonymous: input.anonymous,
    source: "MOBILE_APP",
  });
  return response.data;
}

export async function trackCase(caseId: string): Promise<RitoCase> {
  const response = await apiClient.get<RitoCase>(`${V1}/${encodeURIComponent(caseId)}`);
  return response.data;
}

export async function caseTimeline(caseId: string): Promise<RitoCaseEvent[]> {
  const response = await apiClient.get<RitoCaseEvent[]>(
    `${V1}/${encodeURIComponent(caseId)}/timeline`,
  );
  return response.data ?? [];
}
