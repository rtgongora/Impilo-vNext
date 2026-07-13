/**
 * Registrar credentials casework for a provider (VARAPI via BFF, R2/G11):
 * qualifications (list/create/verify) and practice contexts (list/create/authorize/revoke/renew).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/registry/provider-credentials";

export interface ProviderQualification {
  id: number;
  title?: string;
  qualificationName?: string;
  institutionName?: string;
  awardingBody?: string;
  awardDate?: string;
  verificationStatus?: string;
  [key: string]: unknown;
}

export interface ProviderPracticeContext {
  id: number;
  contextType?: string;
  status?: string;
  authorisationBasis?: string;
  endDate?: string;
  [key: string]: unknown;
}

function arr<T>(payload: unknown): T[] {
  if (Array.isArray(payload)) return payload as T[];
  if (payload && typeof payload === "object" && Array.isArray((payload as { data?: unknown }).data)) {
    return (payload as { data: T[] }).data;
  }
  return [];
}

function unwrap<T>(res: unknown): T[] {
  return arr<T>((res as { data?: unknown })?.data ?? res);
}

// ── Qualifications ────────────────────────────────────────────────────────────

export function useProviderQualifications(providerPublicId?: string) {
  return useQuery<ProviderQualification[]>({
    queryKey: ["provider-qualifications", providerPublicId ?? null],
    queryFn: async () =>
      unwrap<ProviderQualification>(
        await apiClient.get(`${BASE}/provider/${encodeURIComponent(providerPublicId!)}/qualifications`),
      ),
    enabled: !!providerPublicId,
    staleTime: 30_000,
  });
}

export function useCreateQualification(providerPublicId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`${BASE}/provider/${encodeURIComponent(providerPublicId!)}/qualifications`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["provider-qualifications", providerPublicId ?? null] }),
  });
}

export function useVerifyQualification(providerPublicId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ qualificationId, body }: { qualificationId: number; body: Record<string, unknown> }) =>
      apiClient.post(`${BASE}/qualifications/${qualificationId}/verify`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["provider-qualifications", providerPublicId ?? null] }),
  });
}

// ── Practice contexts ─────────────────────────────────────────────────────────

export function useProviderPracticeContexts(providerPublicId?: string) {
  return useQuery<ProviderPracticeContext[]>({
    queryKey: ["provider-practice-contexts", providerPublicId ?? null],
    queryFn: async () =>
      unwrap<ProviderPracticeContext>(
        await apiClient.get(`${BASE}/provider/${encodeURIComponent(providerPublicId!)}/practice-contexts`),
      ),
    enabled: !!providerPublicId,
    staleTime: 30_000,
  });
}

export function useCreatePracticeContext(providerPublicId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`${BASE}/provider/${encodeURIComponent(providerPublicId!)}/practice-contexts`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["provider-practice-contexts", providerPublicId ?? null] }),
  });
}

export function usePracticeContextOp(providerPublicId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ contextId, op, body }: { contextId: number; op: string; body?: Record<string, unknown> }) =>
      apiClient.post(`${BASE}/practice-contexts/${contextId}/${op}`, body ?? {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["provider-practice-contexts", providerPublicId ?? null] }),
  });
}
