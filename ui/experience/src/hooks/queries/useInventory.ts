/**
 * Experience UI — Inventory Query Hook
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface InventoryItemResource {
  id: string;
  type: "inventory_item";
  attributes: {
    name: string;
    sku: string;
    quantity: number;
    facilityId: string;
    category: string;
    [key: string]: unknown;
  };
}

type InventoryItemsResponse = ApiResponse<InventoryItemResource[]>;

export function useInventoryItems(facilityId: string) {
  return useQuery<InventoryItemsResponse>({
    queryKey: ["inventory-items", { facilityId }],
    queryFn: () =>
      apiClient.get<InventoryItemsResponse>(
        `/internal/v1/inventory/items?facility_id=${encodeURIComponent(facilityId)}`
      ),
    enabled: !!facilityId,
  });
}
