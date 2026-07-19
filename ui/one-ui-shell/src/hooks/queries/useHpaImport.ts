/**
 * HPA national facility enrichment — admin read model.
 *
 * Reads the BFF admin lanes (which proxy the tuso HpaImportController): import
 * batches, per-outcome reconciliation counts, and the review queues (identity
 * conflicts / possible-existing / unresolved-site). Read-only; the import itself
 * runs server-side. Degrades honestly (empty on failure).
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

const BASE = "/internal/v1/admin";

/** One HPA import batch (snake_case keys mirror the tuso row). */
export interface HpaImportRun {
  id: number;
  bundle_id: string;
  dry_run: boolean;
  candidates_total: number;
  live_tuso_scanned: number;
  enriched_existing: number;
  created_establishment: number;
  created_unresolved_site: number;
  routed_to_review: number;
  quarantined: number;
  unchanged_idempotent: number;
  status: string;
  started_at: string | null;
  completed_at: string | null;
}

export interface HpaOutcomeCount {
  decision_outcome: string;
  n: number;
}

export interface HpaReviewRow {
  id: number;
  source_record_key: string;
  decision_outcome: string;
  confidence: string;
  matched_facility_id: number | null;
  reason_codes: string | null;
  reviewer_status: string;
}

export function useHpaImportRuns() {
  return useQuery<HpaImportRun[]>({
    queryKey: ["hpa-import", "runs"],
    queryFn: async () => {
      try {
        return (await apiClient.get<ApiResponse<HpaImportRun[]>>(`${BASE}/hpa-import-runs`)).data ?? [];
      } catch {
        return [];
      }
    },
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });
}

export function useHpaOutcomes(runId: number | null) {
  return useQuery<HpaOutcomeCount[]>({
    queryKey: ["hpa-import", "outcomes", runId],
    queryFn: async () => {
      try {
        return (await apiClient.get<ApiResponse<HpaOutcomeCount[]>>(`${BASE}/hpa-import-runs/${runId}/outcomes`))
          .data ?? [];
      } catch {
        return [];
      }
    },
    enabled: runId != null,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });
}

export function useHpaReviewQueue(runId: number | null, outcome?: string) {
  return useQuery<HpaReviewRow[]>({
    queryKey: ["hpa-import", "review", runId, outcome ?? "all"],
    queryFn: async () => {
      try {
        const q = outcome ? `?outcome=${encodeURIComponent(outcome)}` : "";
        return (await apiClient.get<ApiResponse<HpaReviewRow[]>>(`${BASE}/hpa-import-runs/${runId}/review-queue${q}`))
          .data ?? [];
      } catch {
        return [];
      }
    },
    enabled: runId != null,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });
}
