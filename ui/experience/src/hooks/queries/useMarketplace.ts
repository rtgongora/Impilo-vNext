/**
 * Experience UI — Marketplace Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface MarketplaceOrderResource {
  id: string;
  type: "marketplace_order";
  attributes: {
    facilityId: string;
    status: string;
    items: Array<{
      productId: string;
      quantity: number;
      unitPrice: number;
    }>;
    totalAmount: number;
    createdAt: string;
    [key: string]: unknown;
  };
}

interface CreateOrderPayload {
  facilityId: string;
  items: Array<{
    productId: string;
    quantity: number;
  }>;
  [key: string]: unknown;
}

type MarketplaceOrdersResponse = ApiResponse<MarketplaceOrderResource[]>;
type MarketplaceOrderResponse = ApiResponse<MarketplaceOrderResource>;

export function useMarketplaceOrders(facilityId: string) {
  return useQuery<MarketplaceOrdersResponse>({
    queryKey: ["marketplace-orders", { facilityId }],
    queryFn: () =>
      apiClient.get<MarketplaceOrdersResponse>(
        `/internal/v1/marketplace/orders?facility_id=${encodeURIComponent(facilityId)}`
      ),
    enabled: !!facilityId,
  });
}

export function useMarketplaceOrder(id: string) {
  return useQuery<MarketplaceOrderResponse>({
    queryKey: ["marketplace-orders", id],
    queryFn: () =>
      apiClient.get<MarketplaceOrderResponse>(`/internal/v1/marketplace/orders/${id}`),
    enabled: !!id,
  });
}

export function useCreateOrder() {
  const queryClient = useQueryClient();

  return useMutation<MarketplaceOrderResponse, unknown, CreateOrderPayload>({
    mutationFn: (payload: CreateOrderPayload) =>
      apiClient.post<MarketplaceOrderResponse>("/internal/v1/marketplace/orders", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["marketplace-orders"] });
    },
  });
}
