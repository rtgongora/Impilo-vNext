/**
 * Registry Administration Hooks — Health OS §1 (Identity & Trust Services)
 *
 * Provides administrative controls for the VITO client registry, including
 * mode management, provisional ID reconciliation, and registry-level deduplication.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

// ── Shared pagination wrapper ─────────────────────────────────────────
interface PagedItems<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

// ── Interfaces ───────────────────────────────────────────────────────

export interface RegistryMode {
  mode: "ONLINE" | "OFFLINE" | "MAINTENANCE";
  updatedAt: string;
  updatedBy: string;
}

export interface ProvisionalId {
  reference: string;
  healthId?: string;
  facilityId: string;
  issuedAt: string;
  status: "PENDING" | "RECONCILED" | "EXPIRED";
  demographics: {
    givenName: string;
    familyName: string;
    dateOfBirth: string;
    sex: string;
  };
}

export interface RegistryDedupCase {
  caseId: string;
  sourceHealthId: string;
  targetHealthId: string;
  score: number;
  status: "OPEN" | "RESOLVED" | "IGNORED";
  createdAt: string;
}

// ── Queries ──────────────────────────────────────────────────────────

/** Get current registry operational mode. */
export function useRegistryMode() {
  return useQuery({
    queryKey: ["vito", "registry", "mode"],
    queryFn: () =>
      apiClient.get<ApiResponse<RegistryMode>>("/internal/v1/vito/registry/mode"),
  });
}

/** List pending provisional IDs requiring reconciliation. */
export function usePendingProvisionalIds(
  facilityId?: string,
  page = 0,
  size = 20
) {
  const params = new URLSearchParams({
    page: page.toString(),
    size: size.toString(),
  });
  if (facilityId) params.append("facilityId", facilityId);

  return useQuery({
    queryKey: ["vito", "registry", "provisional", "pending", facilityId, page, size],
    queryFn: () =>
      apiClient.get<ApiResponse<PagedItems<ProvisionalId>>>(
        `/internal/v1/vito/registry/provisional/pending?${params.toString()}`
      ),
  });
}

/** List pending deduplication cases at the registry level. */
export function usePendingRegistryDedupCases(page = 0, size = 20) {
  return useQuery({
    queryKey: ["vito", "registry", "dedup", "pending", page, size],
    queryFn: () =>
      apiClient.get<ApiResponse<PagedItems<RegistryDedupCase>>>(
        `/internal/v1/vito/registry/dedup/pending?page=${page}&size=${size}`
      ),
  });
}

// ── Mutations ────────────────────────────────────────────────────────

/** Manually issue a provisional ID. */
export function useIssueProvisionalId() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      facilityId: string;
      demographics: ProvisionalId["demographics"];
    }) =>
      apiClient.post<ApiResponse<ProvisionalId>>(
        "/internal/v1/vito/registry/provisional/issue",
        body
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "registry"] });
    },
  });
}

/** Reconcile a provisional ID with a permanent Health ID. */
export function useReconcileProvisionalId() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ ref, healthId }: { ref: string; healthId: string }) =>
      apiClient.post<ApiResponse<void>>(
        `/internal/v1/vito/registry/provisional/${ref}/reconcile`,
        { healthId }
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "registry"] });
    },
  });
}

/** Resolve a registry-level deduplication case. */
export function useResolveRegistryDedupCase() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      caseId,
      resolution,
    }: {
      caseId: string;
      resolution: "MERGE" | "IGNORE";
    }) =>
      apiClient.post<ApiResponse<void>>(
        `/internal/v1/vito/registry/dedup/${caseId}/resolve`,
        { resolution }
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "registry"] });
    },
  });
}

/** Trigger an OpenCR match request. */
export function useOpenCrMatch() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(
        "/internal/v1/vito/registry/opencr/match",
        body
      ),
  });
}
