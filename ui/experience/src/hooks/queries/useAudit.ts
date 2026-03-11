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
  return useQuery<AuditLogResponse>({
    queryKey: ["audit", { page }],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (page !== undefined) searchParams.set("page", String(page));

      const qs = searchParams.toString();
      const path = `/internal/v1/admin/audit${qs ? `?${qs}` : ""}`;
      return apiClient.get<AuditLogResponse>(path);
    },
  });
}

export function useAuditEntry(id: string) {
  return useQuery<AuditEntryResponse>({
    queryKey: ["audit", id],
    queryFn: () => apiClient.get<AuditEntryResponse>(`/internal/v1/admin/audit/${id}`),
    enabled: !!id,
  });
}
