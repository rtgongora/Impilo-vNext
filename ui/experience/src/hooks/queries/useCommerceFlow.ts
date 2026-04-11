/**
 * Experience UI — Commerce flow (MSIKA Flow) hooks.
 *
 * Backed by Experience BFF:
 * - POST /internal/v1/commerce/cart/validate
 * - POST /internal/v1/commerce/orders
 * - GET  /internal/v1/commerce/orders/{orderId}
 * - POST /internal/v1/commerce/orders/{orderId}/{cancel|validate|price|pay}
 * - GET  /internal/v1/commerce/orders/{orderId}/tracking
 *
 * The controller forwards upstream JSON as-is; responses are treated as unknown JSON.
 */

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type CommerceJson = unknown;

export type CommerceCartValidatePayload = {
  items: Array<{ msikaCoreCode: string; qty: number }>;
  channel?: string;
};

export function useCommerceValidateCart() {
  return useMutation<CommerceJson, unknown, CommerceCartValidatePayload>({
    mutationFn: (payload) => apiClient.post<CommerceJson>("/internal/v1/commerce/cart/validate", payload),
  });
}

export type CommerceCreateOrderPayload = {
  orderType: string;
  patientCpid?: string;
  facilityId?: string;
  vendorId?: string;
  idempotencyKey?: string;
  lines: Array<{
    msikaCoreCode: string;
    kind?: string;
    qty: number;
    unitPrice: number;
    fulfillmentMode?: string;
  }>;
};

export function useCommerceCreateOrder() {
  return useMutation<CommerceJson, unknown, CommerceCreateOrderPayload>({
    mutationFn: (payload) => apiClient.post<CommerceJson>("/internal/v1/commerce/orders", payload),
  });
}

export function useCommerceOrder(orderId: string | undefined) {
  return useQuery<CommerceJson>({
    queryKey: ["commerce", "orders", orderId],
    queryFn: () => apiClient.get<CommerceJson>(`/internal/v1/commerce/orders/${encodeURIComponent(String(orderId))}`),
    enabled: Boolean(orderId),
  });
}

export function useCommerceOrderTracking(orderId: string | undefined) {
  return useQuery<CommerceJson>({
    queryKey: ["commerce", "orders", orderId, "tracking"],
    queryFn: () => apiClient.get<CommerceJson>(`/internal/v1/commerce/orders/${encodeURIComponent(String(orderId))}/tracking`),
    enabled: Boolean(orderId),
  });
}

function actionPath(orderId: string, action: "cancel" | "validate" | "price" | "pay") {
  return `/internal/v1/commerce/orders/${encodeURIComponent(orderId)}/${action}`;
}

export function useCommerceOrderAction() {
  return useMutation<CommerceJson, unknown, { orderId: string; action: "cancel" | "validate" | "price" | "pay" }>({
    mutationFn: ({ orderId, action }) => apiClient.post<CommerceJson>(actionPath(orderId, action)),
  });
}

// ── Cart operations (absorbs msika-flow-portal:/cart) ────────────────

export function useCommerceCart() {
  return useQuery({
    queryKey: ["commerce-cart"],
    queryFn: () => apiClient.get<{ data: Array<{ id: string; name: string; quantity: number; unitPrice: number }> }>("/internal/v1/commerce/cart"),
  });
}

export function useValidateCart() {
  return useMutation({
    mutationFn: () => apiClient.post<CommerceJson>("/internal/v1/commerce/cart/validate"),
  });
}

export function useCheckoutCart() {
  return useMutation({
    mutationFn: () => apiClient.post<CommerceJson>("/internal/v1/commerce/orders"),
  });
}

