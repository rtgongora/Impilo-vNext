/**
 * Experience UI — Provider / council regulation (Varapi via BFF).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type RegistryEnvelope<T> = { data: T };

export type CouncilApplicationAdvanceAction =
  | "under-admin-review"
  | "awaiting-payment"
  | "fee-paid";

export type CouncilReviewDecision = "APPROVED" | "REJECTED" | "NEEDS_INFO";

export interface CouncilApplicationRow {
  id?: number | string;
  applicationType?: string;
  workflowState?: string;
  reviewState?: string | null;
  feeState?: string;
  notes?: string;
  providerId?: number | string;
  provider_id?: number | string;
  provider?: { id?: number | string };
  [key: string]: unknown;
}

export interface CouncilObligationRow {
  id?: number | string;
  obligationType?: string;
  amount?: number | string;
  currency?: string;
  status?: string;
  mushexIntentId?: string | null;
  mushex_intent_id?: string | null;
  dueDate?: string | null;
  applicationId?: number | string | null;
  [key: string]: unknown;
}

export function useProviderCouncilObligations(providerNumericId: string | undefined) {
  return useQuery({
    queryKey: ["provider-council", "obligations", providerNumericId],
    queryFn: async () => {
      const r = await apiClient.get<RegistryEnvelope<CouncilObligationRow[]>>(
        `/internal/v1/registry/provider-council/obligations?providerId=${encodeURIComponent(providerNumericId ?? "")}`,
      );
      return r.data ?? [];
    },
    enabled: !!providerNumericId,
  });
}

export function useCreateCouncilMusheXIntent(providerNumericId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (obligationId: number) =>
      apiClient.post<RegistryEnvelope<unknown>>(
        `/internal/v1/registry/provider-council/obligations/${obligationId}/mushex-intent`,
        {},
      ),
    onSuccess: () =>
      void qc.invalidateQueries({ queryKey: ["provider-council", "obligations", providerNumericId] }),
  });
}

export function useSyncCouncilObligationPayment(providerNumericId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (obligationId: number) =>
      apiClient.post<RegistryEnvelope<CouncilObligationRow>>(
        `/internal/v1/registry/provider-council/obligations/${obligationId}/sync-payment`,
        {},
      ),
    onSuccess: () =>
      void qc.invalidateQueries({ queryKey: ["provider-council", "obligations", providerNumericId] }),
  });
}

export function useProviderCouncilQueue(councilId: string | undefined, workflowStates?: string) {
  const qs = new URLSearchParams();
  if (councilId) {
    qs.set("councilId", councilId);
  }
  if (workflowStates) {
    qs.set("workflowStates", workflowStates);
  }
  const suffix = qs.toString();
  return useQuery({
    queryKey: ["provider-council", "queue", councilId, workflowStates],
    queryFn: async () => {
      const r = await apiClient.get<RegistryEnvelope<CouncilApplicationRow[]>>(
        `/internal/v1/registry/provider-council/applications/open${suffix ? `?${suffix}` : ""}`,
      );
      return r.data ?? [];
    },
    enabled: !!councilId,
  });
}

export function useFundoCpdCandidates(providerNumericId: string | undefined) {
  return useQuery({
    queryKey: ["provider-council", "fundo-cpd", providerNumericId],
    queryFn: async () => {
      const r = await apiClient.get<RegistryEnvelope<unknown[]>>(
        `/internal/v1/registry/provider-council/fundo-cpd-candidates?providerId=${encodeURIComponent(providerNumericId ?? "")}`,
      );
      return r.data ?? [];
    },
    enabled: !!providerNumericId,
  });
}

export function useAdvanceCouncilApplication(councilId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { applicationId: number; action: CouncilApplicationAdvanceAction }) =>
      apiClient.post<RegistryEnvelope<CouncilApplicationRow>>(
        `/internal/v1/registry/provider-council/applications/${input.applicationId}/${input.action}`,
        {},
      ),
    onSuccess: () =>
      void qc.invalidateQueries({ queryKey: ["provider-council", "queue", councilId] }),
  });
}

export function useRecordCouncilReview(councilId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      applicationId: number;
      councilId: number;
      decision: CouncilReviewDecision;
      reviewType?: string;
      notes?: string;
      resolutionReference?: string;
    }) =>
      apiClient.post<RegistryEnvelope<unknown>>("/internal/v1/registry/provider-council/reviews", {
        applicationId: input.applicationId,
        councilId: input.councilId,
        reviewType: input.reviewType ?? "COMMITTEE",
        decision: input.decision,
        notes: input.notes ?? "",
        resolutionReference: input.resolutionReference ?? "",
      }),
    onSuccess: () =>
      void qc.invalidateQueries({ queryKey: ["provider-council", "queue", councilId] }),
  });
}

export function useAcceptFundoCpd(providerNumericId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (candidateId: number) =>
      apiClient.post<RegistryEnvelope<unknown>>(
        `/internal/v1/registry/provider-council/fundo-cpd-candidates/${candidateId}/accept`,
        {},
      ),
    onSuccess: () =>
      void qc.invalidateQueries({ queryKey: ["provider-council", "fundo-cpd", providerNumericId] }),
  });
}

export function useRejectFundoCpd(providerNumericId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { candidateId: number; reason?: string }) =>
      apiClient.post<RegistryEnvelope<unknown>>(
        `/internal/v1/registry/provider-council/fundo-cpd-candidates/${input.candidateId}/reject`,
        { reason: input.reason ?? "" },
      ),
    onSuccess: () =>
      void qc.invalidateQueries({ queryKey: ["provider-council", "fundo-cpd", providerNumericId] }),
  });
}

export function councilQueueReviewBucket(
  app: CouncilApplicationRow,
): "pending" | "approved" | "rejected" | "needs-info" {
  const review = String(app.reviewState ?? "").toUpperCase();
  if (review === "APPROVED") return "approved";
  if (review === "REJECTED") return "rejected";
  if (review === "NEEDS_INFO") return "needs-info";
  return "pending";
}
