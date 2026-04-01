import { useQuery } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type { AuditEvent, PagedResponse } from "@/types/vito";

export interface AuditLogParams {
  page?: number;
  size?: number;
  aggregateType?: string;
  eventType?: string;
  actorId?: string;
  from?: string;
  to?: string;
  refetchInterval?: number;
}

export const auditKeys = {
  all: ["audit"] as const,
  list: (params: Omit<AuditLogParams, "refetchInterval">) =>
    [...auditKeys.all, "list", params] as const,
};

export function useAuditLog(params: AuditLogParams = {}) {
  const { refetchInterval, ...queryParams } = params;
  return useQuery({
    queryKey: auditKeys.list(queryParams),
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (queryParams.page !== undefined) searchParams.set("page", String(queryParams.page));
      if (queryParams.size !== undefined) searchParams.set("size", String(queryParams.size));
      if (queryParams.aggregateType) searchParams.set("aggregateType", queryParams.aggregateType);
      if (queryParams.eventType) searchParams.set("eventType", queryParams.eventType);
      if (queryParams.actorId) searchParams.set("actorId", queryParams.actorId);
      if (queryParams.from) searchParams.set("from", queryParams.from);
      if (queryParams.to) searchParams.set("to", queryParams.to);
      const qs = searchParams.toString();
      return vitoApiClient.get<PagedResponse<AuditEvent>>(
        `/v1/internal/audit${qs ? `?${qs}` : ""}`
      );
    },
    staleTime: 30_000,
    refetchInterval: refetchInterval,
  });
}
