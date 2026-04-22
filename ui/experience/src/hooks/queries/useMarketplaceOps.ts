/**
 * Marketplace ops via Experience BFF.
 *
 * - GET  /internal/v1/commerce/ops/reviews
 * - POST /internal/v1/commerce/ops/reviews/{reviewId}/approve|reject
 * - GET  /internal/v1/commerce/ops/stuck-orders
 * - GET  /internal/v1/commerce/ops/audit
 * - GET  /internal/v1/commerce/ops/vendors
 * - GET  /internal/v1/commerce/ops/vendors/{vendorId}
 * - POST /internal/v1/commerce/ops/vendors/{vendorId}/suspend|reinstate
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type MarketplaceOpsJson = unknown;

function buildQuery(params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value != null && value !== "") query.set(key, String(value));
  }
  const suffix = query.toString();
  return suffix ? `?${suffix}` : "";
}

export function useMarketplaceOpsReviews(params: { page?: number; size?: number }, enabled: boolean) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "ops", "reviews", params.page ?? "", params.size ?? ""],
    queryFn: () =>
      apiClient.get<MarketplaceOpsJson>(
        `/internal/v1/commerce/ops/reviews${buildQuery(params)}`,
      ),
    enabled,
  });
}

export function useMarketplaceOpsStuckOrders(enabled: boolean) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "ops", "stuck-orders"],
    queryFn: () => apiClient.get<MarketplaceOpsJson>("/internal/v1/commerce/ops/stuck-orders"),
    enabled,
  });
}

export function useMarketplaceOpsAudit(params: { page?: number; size?: number }, enabled: boolean) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "ops", "audit", params.page ?? "", params.size ?? ""],
    queryFn: () =>
      apiClient.get<MarketplaceOpsJson>(`/internal/v1/commerce/ops/audit${buildQuery(params)}`),
    enabled,
  });
}

export function useMarketplaceOpsVendors(params: { page?: number; size?: number }, enabled: boolean) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "ops", "vendors", params.page ?? "", params.size ?? ""],
    queryFn: () =>
      apiClient.get<MarketplaceOpsJson>(`/internal/v1/commerce/ops/vendors${buildQuery(params)}`),
    enabled,
  });
}

export function useMarketplaceOpsVendor(vendorId: string | undefined) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "ops", "vendor", vendorId],
    queryFn: () =>
      apiClient.get<MarketplaceOpsJson>(
        `/internal/v1/commerce/ops/vendors/${encodeURIComponent(String(vendorId))}`,
      ),
    enabled: Boolean(vendorId),
  });
}

function invalidateOps(qc: ReturnType<typeof useQueryClient>) {
  void qc.invalidateQueries({ queryKey: ["commerce", "ops"] });
}

export function useApproveMarketplaceReview() {
  const qc = useQueryClient();
  return useMutation<MarketplaceOpsJson, unknown, { reviewId: string; body: unknown }>({
    mutationFn: ({ reviewId, body }) =>
      apiClient.post<MarketplaceOpsJson>(
        `/internal/v1/commerce/ops/reviews/${encodeURIComponent(reviewId)}/approve`,
        body,
      ),
    onSuccess: () => invalidateOps(qc),
  });
}

export function useRejectMarketplaceReview() {
  const qc = useQueryClient();
  return useMutation<MarketplaceOpsJson, unknown, { reviewId: string; body: unknown }>({
    mutationFn: ({ reviewId, body }) =>
      apiClient.post<MarketplaceOpsJson>(
        `/internal/v1/commerce/ops/reviews/${encodeURIComponent(reviewId)}/reject`,
        body,
      ),
    onSuccess: () => invalidateOps(qc),
  });
}

export function useSuspendMarketplaceVendor() {
  const qc = useQueryClient();
  return useMutation<MarketplaceOpsJson, unknown, { vendorId: string; body: unknown }>({
    mutationFn: ({ vendorId, body }) =>
      apiClient.post<MarketplaceOpsJson>(
        `/internal/v1/commerce/ops/vendors/${encodeURIComponent(vendorId)}/suspend`,
        body,
      ),
    onSuccess: () => invalidateOps(qc),
  });
}

export function useReinstateMarketplaceVendor() {
  const qc = useQueryClient();
  return useMutation<MarketplaceOpsJson, unknown, string>({
    mutationFn: (vendorId) =>
      apiClient.post<MarketplaceOpsJson>(
        `/internal/v1/commerce/ops/vendors/${encodeURIComponent(vendorId)}/reinstate`,
      ),
    onSuccess: () => invalidateOps(qc),
  });
}

// ── Logistics orchestration desk (plans/exceptions/proofs) ──────────────────

export function useMarketplaceLogisticsPlans(params: { fulfillmentId?: string }, enabled: boolean) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "logistics", "plans", params.fulfillmentId ?? ""],
    queryFn: () =>
      apiClient.get<MarketplaceOpsJson>(
        `/internal/v1/commerce/logistics/plans${buildQuery(params)}`,
      ),
    enabled,
  });
}

export function useMarketplaceLogisticsExceptions(params: { planId?: string; status?: string }, enabled: boolean) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "logistics", "exceptions", params.planId ?? "", params.status ?? ""],
    queryFn: () =>
      apiClient.get<MarketplaceOpsJson>(
        `/internal/v1/commerce/logistics/exceptions${buildQuery(params)}`,
      ),
    enabled,
  });
}

export function useMarketplaceLogisticsProofs(params: { planId?: string; handoffId?: string }, enabled: boolean) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "logistics", "proofs", params.planId ?? "", params.handoffId ?? ""],
    queryFn: () =>
      apiClient.get<MarketplaceOpsJson>(
        `/internal/v1/commerce/logistics/proofs${buildQuery(params)}`,
      ),
    enabled,
  });
}

export function useMarketplaceLogisticsProviders(params: { activeOnly?: boolean }, enabled: boolean) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "logistics", "providers", params.activeOnly ?? ""],
    queryFn: () =>
      apiClient.get<MarketplaceOpsJson>(
        `/internal/v1/commerce/logistics/providers${buildQuery(params)}`,
      ),
    enabled,
  });
}

export function useMarketplaceLogisticsAssets(params: { providerId?: string; activeOnly?: boolean }, enabled: boolean) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "logistics", "assets", params.providerId ?? "", params.activeOnly ?? ""],
    queryFn: () =>
      apiClient.get<MarketplaceOpsJson>(
        `/internal/v1/commerce/logistics/assets${buildQuery(params)}`,
      ),
    enabled,
  });
}

export function useMarketplaceLogisticsMissions(params: { legId?: string }, enabled: boolean) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "logistics", "missions", params.legId ?? ""],
    queryFn: () =>
      apiClient.get<MarketplaceOpsJson>(
        `/internal/v1/commerce/logistics/missions${buildQuery(params)}`,
      ),
    enabled,
  });
}

export function useMarketplaceLogisticsHandoffs(params: { fulfillmentId?: string; legId?: string }, enabled: boolean) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "logistics", "handoffs", params.fulfillmentId ?? "", params.legId ?? ""],
    queryFn: () =>
      apiClient.get<MarketplaceOpsJson>(
        `/internal/v1/commerce/logistics/handoffs${buildQuery(params)}`,
      ),
    enabled,
  });
}

export function useMarketplaceLogisticsCustody(params: { fulfillmentId?: string }, enabled: boolean) {
  return useQuery<MarketplaceOpsJson>({
    queryKey: ["commerce", "logistics", "custody", params.fulfillmentId ?? ""],
    queryFn: () =>
      apiClient.get<MarketplaceOpsJson>(
        `/internal/v1/commerce/logistics/custody${buildQuery(params)}`,
      ),
    enabled,
  });
}
