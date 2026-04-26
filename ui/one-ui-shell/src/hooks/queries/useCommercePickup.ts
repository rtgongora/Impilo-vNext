/**
 * Pickup handoff via Experience BFF.
 *
 * - POST /internal/v1/commerce/orders/{orderId}/pickup/issue
 * - POST /internal/v1/commerce/pickup/claim
 */

import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type CommercePickupJson = unknown;

export function useCommerceIssuePickup() {
  return useMutation<CommercePickupJson, unknown, string>({
    mutationFn: (orderId) =>
      apiClient.post<CommercePickupJson>(
        `/internal/v1/commerce/orders/${encodeURIComponent(orderId)}/pickup/issue`,
      ),
  });
}

export function useCommerceClaimPickup() {
  return useMutation<CommercePickupJson, unknown, unknown>({
    mutationFn: (body) => apiClient.post<CommercePickupJson>("/internal/v1/commerce/pickup/claim", body),
  });
}
