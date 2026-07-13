/**
 * Registrar compliance + disciplinary casework for a provider (VARAPI via BFF).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/registry/provider-compliance";

export interface ComplianceAction {
  id: number;
  actionType?: string;
  description?: string;
  requirement?: string;
  status?: string;
  dueDate?: string;
  [key: string]: unknown;
}

export interface DisciplinaryCase {
  id: number;
  triggerType?: string;
  status?: string;
  openedDate?: string;
  summary?: string;
  finalDecision?: string;
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

// ── Compliance ──────────────────────────────────────────────────────────────

export function useProviderCompliance(providerPublicId?: string) {
  return useQuery<ComplianceAction[]>({
    queryKey: ["provider-compliance", providerPublicId ?? null],
    queryFn: async () => unwrap<ComplianceAction>(await apiClient.get(`${BASE}/provider/${encodeURIComponent(providerPublicId!)}/compliance`)),
    enabled: !!providerPublicId,
    staleTime: 30_000,
  });
}

export function useCreateComplianceAction(providerPublicId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`${BASE}/provider/${encodeURIComponent(providerPublicId!)}/compliance`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["provider-compliance", providerPublicId ?? null] }),
  });
}

export function useComplianceOp(providerPublicId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ actionId, op, body }: { actionId: number; op: string; body?: Record<string, unknown> }) =>
      apiClient.post(`${BASE}/compliance/${actionId}/${op}`, body ?? {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["provider-compliance", providerPublicId ?? null] }),
  });
}

// ── Disciplinary ────────────────────────────────────────────────────────────

export function useProviderDisciplinary(providerPublicId?: string) {
  return useQuery<DisciplinaryCase[]>({
    queryKey: ["provider-disciplinary", providerPublicId ?? null],
    queryFn: async () => unwrap<DisciplinaryCase>(await apiClient.get(`${BASE}/provider/${encodeURIComponent(providerPublicId!)}/disciplinary`)),
    enabled: !!providerPublicId,
    staleTime: 30_000,
  });
}

export function useOpenDisciplinaryCase(providerPublicId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`${BASE}/provider/${encodeURIComponent(providerPublicId!)}/disciplinary`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["provider-disciplinary", providerPublicId ?? null] }),
  });
}

export function useRecordDisciplinaryAction(providerPublicId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ caseId, body }: { caseId: number; body: Record<string, unknown> }) =>
      apiClient.post(`${BASE}/disciplinary/${caseId}/record-action`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["provider-disciplinary", providerPublicId ?? null] }),
  });
}

export function useCloseDisciplinaryCase(providerPublicId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ caseId, body }: { caseId: number; body: Record<string, unknown> }) =>
      apiClient.post(`${BASE}/disciplinary/${caseId}/close`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["provider-disciplinary", providerPublicId ?? null] }),
  });
}
