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
