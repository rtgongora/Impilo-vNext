/**
 * Experience UI — Audit Log Query Hooks
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface AuditEntryResource {
  id: string;
  type: "audit_entry";
  attributes: {
    action: string;
    actorId: string;
    actorType: string;
    resourceType: string;
    resourceId: string;
    timestamp: string;
    details: Record<string, unknown>;
    [key: string]: unknown;
  };
}

type AuditLogResponse = ApiResponse<AuditEntryResource[]>;
type AuditEntryResponse = ApiResponse<AuditEntryResource>;

export function useAuditLog(page?: number) {
  return useAuditLogSized(page, undefined);
}

export function useAuditEntry(id: string) {
  return useQuery<AuditEntryResponse>({
    queryKey: ["audit", id],
    queryFn: () => apiClient.get<AuditEntryResponse>(`/internal/v1/admin/audit/${id}`),
    enabled: !!id,
  });
}

export function useAuditLogSized(page?: number, size?: number) {
  const p = page ?? 0;
  const s = size ?? 20;
  return useQuery<AuditLogResponse>({
    queryKey: ["audit", { page: p, size: s }],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      searchParams.set("page", String(p));
      searchParams.set("size", String(s));
      return apiClient.get<AuditLogResponse>(`/internal/v1/admin/audit?${searchParams.toString()}`);
    },
  });
}
