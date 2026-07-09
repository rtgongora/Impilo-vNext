/**
 * Experience UI — IATG Trust Console query hooks
 *
 * Backed by the experience-bff Trust Console composition:
 * - GET  /internal/v1/trust-console/summary
 * - GET  /internal/v1/trust-console/queues/{queue}
 * - GET  /internal/v1/trust-console/decisions/recent
 * - POST /internal/v1/trust-console/queues/{queue}/{itemId}/decision
 *
 * Every queue degrades independently on the BFF side (integrationStatus
 * "pending_backend"); the hooks surface that honestly instead of zeroing.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type TrustConsoleQueueName =
  | "provider-access-requests"
  | "facility-admin-appointments"
  | "org-access-requests"
  | "assurance-upgrades";

export interface TrustConsoleQueueSummary {
  pendingCount: number | null;
  integrationStatus: "live" | "pending_backend";
  friendlyMessage?: string;
}

export interface TrustConsoleSummaryAttributes {
  status: string;
  integrationStatus: "live" | "partial" | string;
  friendlyMessage?: string;
  queues: Record<TrustConsoleQueueName, TrustConsoleQueueSummary>;
}

export interface TrustConsoleQueueAttributes {
  queue: TrustConsoleQueueName;
  items: Record<string, unknown>[];
  count: number | null;
  integrationStatus: "live" | "pending_backend";
  friendlyMessage?: string;
  status?: string;
}

export interface TrustConsoleRecentDecisionsAttributes {
  status: string;
  providerAccessDecisions: {
    items: Record<string, unknown>[];
    integrationStatus: string;
    friendlyMessage?: string;
  };
  governanceAuditEvents: {
    items: Record<string, unknown>[];
    integrationStatus: string;
    friendlyMessage?: string;
  };
}

interface TrustConsoleEnvelope<T> {
  data?: { type: string; attributes: T };
  meta?: { request_id?: string; correlation_id?: string };
}

export interface TrustConsoleDecisionInput {
  queue: TrustConsoleQueueName;
  itemId: string;
  decision: "APPROVED" | "REJECTED" | "NEEDS_MORE_INFORMATION";
  note?: string;
}

export interface TrustConsoleDecisionAttributes {
  status: string;
  integrationStatus?: string;
  friendlyMessage?: string;
  queue?: string;
  itemId?: string;
  decision?: string;
  result?: unknown;
}

export function useTrustConsoleSummary() {
  return useQuery<TrustConsoleSummaryAttributes | undefined>({
    queryKey: ["trust-console", "summary"],
    queryFn: async () => {
      const res = await apiClient.get<TrustConsoleEnvelope<TrustConsoleSummaryAttributes>>(
        "/internal/v1/trust-console/summary",
      );
      return res?.data?.attributes;
    },
  });
}

export function useTrustConsoleQueue(queue: TrustConsoleQueueName) {
  return useQuery<TrustConsoleQueueAttributes | undefined>({
    queryKey: ["trust-console", "queue", queue],
    queryFn: async () => {
      const res = await apiClient.get<TrustConsoleEnvelope<TrustConsoleQueueAttributes>>(
        `/internal/v1/trust-console/queues/${queue}`,
      );
      return res?.data?.attributes;
    },
    enabled: !!queue,
  });
}

export function useTrustConsoleRecentDecisions() {
  return useQuery<TrustConsoleRecentDecisionsAttributes | undefined>({
    queryKey: ["trust-console", "recent-decisions"],
    queryFn: async () => {
      const res = await apiClient.get<TrustConsoleEnvelope<TrustConsoleRecentDecisionsAttributes>>(
        "/internal/v1/trust-console/decisions/recent",
      );
      return res?.data?.attributes;
    },
  });
}

export function useTrustConsoleDecision() {
  const queryClient = useQueryClient();
  return useMutation<TrustConsoleDecisionAttributes | undefined, unknown, TrustConsoleDecisionInput>({
    mutationFn: async ({ queue, itemId, decision, note }) => {
      const res = await apiClient.post<TrustConsoleEnvelope<TrustConsoleDecisionAttributes>>(
        `/internal/v1/trust-console/queues/${queue}/${encodeURIComponent(itemId)}/decision`,
        { decision, ...(note ? { note } : {}) },
      );
      return res?.data?.attributes;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["trust-console"] });
    },
  });
}
