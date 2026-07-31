/**
 * MPDSR review service — provider mobile read view of maternal/perinatal death reviews.
 *
 * Backend: experience-bff MpdsrReviewBffController
 *   GET /internal/v1/rito/mpdsr/reviews        — QI/ops worklist
 *   GET /internal/v1/rito/mpdsr/reviews/{id}   — review + committee/findings/actions/readiness
 *
 * The BFF forwards raw rito-quality-safety-service payloads (Jackson camelCase entities), so
 * responses are NOT wrapped in an ApiEnvelope. The committee lifecycle (meetings, findings,
 * actions, close) stays on the web ops surface — mobile is a confidential read for committee
 * members in the field, mirroring the same firewall doctrine: readiness tasks carry no review
 * narrative or death identifiers.
 */

import { apiClient } from "@impilo/mobile-api-client";

export interface MpdsrReview {
  id: string;
  deathCaseId?: string | null;
  status?: string | null;
  reviewLevel?: string | null;
  facilityId?: string | null;
  triggerFlags?: string | null;
  openedAt?: string | null;
}

export interface MpdsrFinding {
  id: string;
  delayType?: string | null;
  modifiableFactor?: string | null;
}

export interface MpdsrAction {
  id: string;
  title?: string | null;
  status?: string | null;
  dueAt?: string | null;
}

export interface MpdsrReadinessTask {
  id: string;
  taskReference?: string | null;
  status?: string | null;
}

export interface MpdsrReviewDetail {
  review?: MpdsrReview;
  meetings?: unknown[];
  findings?: MpdsrFinding[];
  actions?: MpdsrAction[];
  readinessTasks?: MpdsrReadinessTask[];
}

function asArray<T>(payload: unknown): T[] {
  if (Array.isArray(payload)) return payload as T[];
  if (payload && typeof payload === "object") {
    const inner = (payload as { data?: unknown }).data;
    if (Array.isArray(inner)) return inner as T[];
  }
  return [];
}

export async function fetchMpdsrReviews(status?: string): Promise<MpdsrReview[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await apiClient.get<unknown>(`/internal/v1/rito/mpdsr/reviews${query}`);
  return asArray<MpdsrReview>(response.data);
}

export async function fetchMpdsrReview(id: string): Promise<MpdsrReviewDetail> {
  const response = await apiClient.get<MpdsrReviewDetail>(
    `/internal/v1/rito/mpdsr/reviews/${encodeURIComponent(id)}`,
  );
  return response.data ?? {};
}
