/**
 * Experience UI — OROS Diagnostics Journey query hooks.
 *
 * Backed by the experience-bff diagnostics proxy (`/internal/v1/diagnostics/*`), which governs and
 * forwards to OROS. No mock data: every hook calls a real BFF endpoint.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/** Mirrors the OROS OrderSummaryDto surfaced through the BFF. */
export interface DiagnosticOrder {
  orderId: string;
  orderType: string;
  status: string;
  priority: string | null;
  patientCpid: string;
  facilityId: string | null;
  placedAt: string | null;
  placedBy: string | null;
  encounterRef: string | null;
  clinicalNotes: string | null;
  updatedAt: string | null;
  requestSource: string | null;
  accessionNumber: string | null;
  referringProviderId: string | null;
  referringProviderName: string | null;
  scheduledAt: string | null;
  imagingState: string | null;
}

export interface DiagnosticOrderFilters {
  client?: string;
  requester?: string;
  status?: string;
  type?: string;
}

type OrdersResponse = ApiResponse<DiagnosticOrder[]>;
type ReconcileSummaryResponse = ApiResponse<Record<string, number>>;

function buildQuery(filters: DiagnosticOrderFilters): string {
  const params = new URLSearchParams();
  if (filters.client) params.set("client", filters.client);
  if (filters.requester) params.set("requester", filters.requester);
  if (filters.status) params.set("status", filters.status);
  if (filters.type) params.set("type", filters.type);
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

/** Diagnostic order tracking list. */
export function useDiagnosticsOrders(filters: DiagnosticOrderFilters = {}) {
  return useQuery<OrdersResponse>({
    queryKey: [
      "diagnostics-orders",
      filters.client ?? null,
      filters.requester ?? null,
      filters.status ?? null,
      filters.type ?? null,
    ],
    queryFn: () =>
      apiClient.get<OrdersResponse>(`/internal/v1/diagnostics/orders${buildQuery(filters)}`),
    staleTime: 15_000,
  });
}

/** Requester results inbox — orders with results ready to review. */
export function useResultsInbox(requester?: string) {
  return useQuery<OrdersResponse>({
    queryKey: ["diagnostics-results-inbox", requester ?? null],
    queryFn: () => {
      const qs = requester ? `?requester=${encodeURIComponent(requester)}` : "";
      return apiClient.get<OrdersResponse>(`/internal/v1/diagnostics/results-inbox${qs}`);
    },
    staleTime: 15_000,
  });
}

/** Operational reconciliation summary (stuck-order bucket counts + unacked critical). */
export function useDiagnosticsReconcileSummary() {
  return useQuery<ReconcileSummaryResponse>({
    queryKey: ["diagnostics-reconcile-summary"],
    queryFn: () =>
      apiClient.get<ReconcileSummaryResponse>("/internal/v1/diagnostics/reconcile-summary"),
    staleTime: 30_000,
  });
}
