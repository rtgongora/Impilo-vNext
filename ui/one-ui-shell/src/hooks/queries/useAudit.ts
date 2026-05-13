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
type AuditAggregateFilter = { aggregateType?: string; aggregateId?: string };

export function useAuditLog(page?: number, filters?: AuditAggregateFilter) {
  return useAuditLogSized(page, undefined, filters);
}

export function useAuditEntry(id: string) {
  return useQuery<AuditEntryResponse>({
    queryKey: ["audit", id],
    queryFn: () => apiClient.get<AuditEntryResponse>(`/internal/v1/admin/audit/${id}`),
    enabled: !!id,
  });
}

export function useAuditLogSized(page?: number, size?: number, filters?: AuditAggregateFilter) {
  const p = page ?? 0;
  const s = size ?? 20;
  const aggregateType = filters?.aggregateType?.trim() ?? "";
  const aggregateId = filters?.aggregateId?.trim() ?? "";
  return useQuery<AuditLogResponse>({
    queryKey: ["audit", { page: p, size: s, aggregateType, aggregateId }],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      searchParams.set("page", String(p));
      searchParams.set("size", String(s));
      if (aggregateType.length > 0) {
        searchParams.set("aggregateType", aggregateType);
      }
      if (aggregateId.length > 0) {
        searchParams.set("aggregateId", aggregateId);
      }
      return apiClient.get<AuditLogResponse>(`/internal/v1/admin/audit?${searchParams.toString()}`);
    },
  });
}
