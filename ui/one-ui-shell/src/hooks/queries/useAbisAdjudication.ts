/**
 * Hooks for the ABIS adjudication console — the duplicate-investigation queue opened by
 * enrol-time dedup and 1:N identify. All actions hit the REAL platform via the BFF → ABIS.
 *
 * Governance: a merge is NEVER automatic. It requires a CONFIRMED_DUPLICATE decision plus a
 * SECOND reviewer (dual control) distinct from the decider — enforced server-side.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/identity/biometric/abis";

export type CaseStatus = "OPEN" | "IN_REVIEW" | "RESOLVED";
export type Decision = "DISTINCT" | "CONFIRMED_DUPLICATE";

export interface AdjudicationCandidate {
  candidateSubjectRef: string;
  score: number;
  summary?: string;
  rank: number;
}

export interface AdjudicationCase {
  id: number;
  subjectRef: string;
  modality: string;
  reason: string;
  status: CaseStatus;
  decision?: Decision | null;
  candidateCount: number;
  topScore?: number | null;
  assignedReviewer?: string | null;
  decidedBy?: string | null;
  decidedAt?: string | null;
  decisionReason?: string | null;
  mergeTargetSubjectRef?: string | null;
  dualSignOffBy?: string | null;
  mergedAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
  candidates?: AdjudicationCandidate[];
}

export interface AdjudicationQueue {
  content: AdjudicationCase[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** The adjudication queue, optionally filtered by status. */
export function useAdjudicationQueue(status?: CaseStatus | "") {
  return useQuery({
    queryKey: ["abis-adjudication-queue", status ?? "ALL"],
    queryFn: () => {
      const q = status ? `?status=${status}` : "";
      return apiClient.get<AdjudicationQueue>(`${BASE}/adjudication/cases${q}`);
    },
  });
}

/** One case with its candidate list + scores. */
export function useAdjudicationCase(caseId: number | null) {
  return useQuery({
    queryKey: ["abis-adjudication-case", caseId],
    queryFn: () => apiClient.get<AdjudicationCase>(`${BASE}/adjudication/cases/${caseId}`),
    enabled: caseId != null,
  });
}

/** Assign a reviewer → IN_REVIEW. */
export function useAssignReviewer(caseId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (reviewer: string) =>
      apiClient.post<AdjudicationCase>(`${BASE}/adjudication/cases/${caseId}/assign`, { reviewer }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["abis-adjudication-case", caseId] });
      qc.invalidateQueries({ queryKey: ["abis-adjudication-queue"] });
    },
  });
}

/** Record a decision → RESOLVED. Never merges. */
export function useRecordDecision(caseId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { decision: Decision; reason?: string }) =>
      apiClient.post<AdjudicationCase>(`${BASE}/adjudication/cases/${caseId}/decision`, input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["abis-adjudication-case", caseId] });
      qc.invalidateQueries({ queryKey: ["abis-adjudication-queue"] });
    },
  });
}

/** Governed, dual-control merge. The signed-in actor is the SECOND reviewer (server-enforced). */
export function useMergeCase(caseId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (mergeTargetSubjectRef: string) =>
      apiClient.post<AdjudicationCase>(`${BASE}/adjudication/cases/${caseId}/merge`, {
        mergeTargetSubjectRef,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["abis-adjudication-case", caseId] });
      qc.invalidateQueries({ queryKey: ["abis-adjudication-queue"] });
    },
  });
}

export interface AbisStats {
  tenantId: string;
  templates: { total: number; active: number; byModalityStatus: Record<string, Record<string, number>> };
  qualityDistribution: Record<string, number>;
  adjudication: Record<string, number>;
  thresholds: unknown;
  calibrationNote: string;
}

/** Operational stats for the console dashboards. */
export function useAbisStats() {
  return useQuery({
    queryKey: ["abis-stats"],
    queryFn: () => apiClient.get<AbisStats>(`${BASE}/stats`),
  });
}
