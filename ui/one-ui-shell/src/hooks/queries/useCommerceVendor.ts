/**
 * Trusted Experience-operator workflows for MSIKA vendor queue (BFF forwards to MSIKA Flow).
 *
 * - GET  /internal/v1/commerce/vendor/{vendorId}/orders
 * - POST /internal/v1/commerce/vendor/{vendorId}/orders/{orderId}/accept
 * - POST /internal/v1/commerce/vendor/orders/{orderId}/mark-ready
 * - POST /internal/v1/commerce/vendor/orders/{orderId}/mark-delivered
 * - POST /internal/v1/commerce/vendor/rx/{orderId}/substitution/propose
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type VendorCommerceJson = unknown;

function invalidateVendorOrderLists(qc: ReturnType<typeof useQueryClient>) {
  void qc.invalidateQueries({ queryKey: ["commerce", "vendor"] });
}

export function useVendorOrders(vendorId: string | undefined) {
  return useQuery<VendorCommerceJson>({
    queryKey: ["commerce", "vendor", vendorId, "orders"],
    queryFn: () =>
      apiClient.get<VendorCommerceJson>(
        `/internal/v1/commerce/vendor/${encodeURIComponent(String(vendorId))}/orders`,
      ),
    enabled: Boolean(vendorId),
  });
}

export function useVendorAcceptOrder() {
  const qc = useQueryClient();
  return useMutation<VendorCommerceJson, unknown, { vendorId: string; orderId: string }>({
    mutationFn: ({ vendorId, orderId }) =>
      apiClient.post<VendorCommerceJson>(
        `/internal/v1/commerce/vendor/${encodeURIComponent(vendorId)}/orders/${encodeURIComponent(orderId)}/accept`,
      ),
    onSuccess: () => invalidateVendorOrderLists(qc),
  });
}

export function useVendorMarkReady() {
  const qc = useQueryClient();
  return useMutation<VendorCommerceJson, unknown, { orderId: string }>({
    mutationFn: ({ orderId }) =>
      apiClient.post<VendorCommerceJson>(
        `/internal/v1/commerce/vendor/orders/${encodeURIComponent(orderId)}/mark-ready`,
      ),
    onSuccess: () => invalidateVendorOrderLists(qc),
  });
}

export function useVendorMarkDelivered() {
  const qc = useQueryClient();
  return useMutation<VendorCommerceJson, unknown, { orderId: string }>({
    mutationFn: ({ orderId }) =>
      apiClient.post<VendorCommerceJson>(
        `/internal/v1/commerce/vendor/orders/${encodeURIComponent(orderId)}/mark-delivered`,
      ),
    onSuccess: () => invalidateVendorOrderLists(qc),
  });
}

export function useVendorProposeRxSubstitution() {
  const qc = useQueryClient();
  return useMutation<VendorCommerceJson, unknown, { orderId: string; body: unknown }>({
    mutationFn: ({ orderId, body }) =>
      apiClient.post<VendorCommerceJson>(
        `/internal/v1/commerce/vendor/rx/${encodeURIComponent(orderId)}/substitution/propose`,
        body,
      ),
    onSuccess: () => invalidateVendorOrderLists(qc),
  });
}

// ── Authenticated vendor binding (M8) ─────────────────────────────────────────

/**
 * Resolve the caller's own vendor profile from the JWT actor.
 * Backed by BFF GET /internal/v1/commerce/vendor/me → msika-flow /v1/vendors/by-actor/{actorId}.
 * A 404 (no vendor bound to this actor) resolves to `null` rather than throwing.
 */
export function useVendorIdentity() {
  return useQuery<{ vendorId: string; raw: unknown } | null>({
    queryKey: ["commerce", "vendor", "me"],
    queryFn: async () => {
      try {
        const payload = await apiClient.get<unknown>("/internal/v1/commerce/vendor/me");
        const vendorId = extractVendorId(payload);
        return vendorId ? { vendorId, raw: payload } : null;
      } catch (error) {
        if ((error as { status?: number })?.status === 404) return null;
        throw error;
      }
    },
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}

/** Tolerant scan for a vendorId in the vendor-me envelope ({success,data} or nested data). */
export function extractVendorId(payload: unknown, depth = 0): string | undefined {
  if (payload == null || typeof payload !== "object" || depth > 4) return undefined;
  const record = payload as Record<string, unknown>;
  for (const key of ["vendorId", "id"]) {
    const value = record[key];
    if (typeof value === "string" && value) return value;
  }
  return extractVendorId(record.data, depth + 1);
}
