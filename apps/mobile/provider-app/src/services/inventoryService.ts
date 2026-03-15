/**
 * Inventory Service — Stock and dispatch management for supervisor mode.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/inventory/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { StockItem, DispatchRecord } from "../types";

interface StockResource {
  id: string;
  type: "StockItem";
  attributes: {
    item_name: string;
    item_code: string;
    category: string;
    current_quantity: number;
    reorder_level: number;
    unit: string;
    level: string;
    facility_id: string;
    last_restocked_at?: string;
    expiry_date?: string;
  };
}

function mapStockItem(r: StockResource): StockItem {
  return {
    id: r.id,
    itemName: r.attributes.item_name,
    itemCode: r.attributes.item_code,
    category: r.attributes.category,
    currentQuantity: r.attributes.current_quantity,
    reorderLevel: r.attributes.reorder_level,
    unit: r.attributes.unit,
    level: r.attributes.level as StockItem["level"],
    facilityId: r.attributes.facility_id,
    lastRestockedAt: r.attributes.last_restocked_at,
    expiryDate: r.attributes.expiry_date,
  };
}

export async function getStockItems(
  facilityId: string,
  category?: string
): Promise<StockItem[]> {
  const params = new URLSearchParams({ facility_id: facilityId });
  if (category) params.set("category", category);
  const response = await apiClient.get<{ data: StockResource[] }>(
    `/internal/v1/mobile/provider/inventory/stock?${params.toString()}`
  );
  return response.data.data.map(mapStockItem);
}

export async function getStockAlerts(facilityId: string): Promise<StockItem[]> {
  const response = await apiClient.get<{ data: StockResource[] }>(
    `/internal/v1/mobile/provider/inventory/stock/alerts?facility_id=${facilityId}`
  );
  return response.data.data.map(mapStockItem);
}

interface DispatchResource {
  id: string;
  type: "Dispatch";
  attributes: {
    item_id: string;
    item_name: string;
    quantity: number;
    from_facility_id: string;
    to_facility_id: string;
    to_facility_name: string;
    status: string;
    dispatched_at?: string;
    delivered_at?: string;
    created_at: string;
  };
}

function mapDispatch(r: DispatchResource): DispatchRecord {
  return {
    id: r.id,
    itemId: r.attributes.item_id,
    itemName: r.attributes.item_name,
    quantity: r.attributes.quantity,
    fromFacilityId: r.attributes.from_facility_id,
    toFacilityId: r.attributes.to_facility_id,
    toFacilityName: r.attributes.to_facility_name,
    status: r.attributes.status as DispatchRecord["status"],
    dispatchedAt: r.attributes.dispatched_at,
    deliveredAt: r.attributes.delivered_at,
    createdAt: r.attributes.created_at,
  };
}

export async function getDispatches(
  facilityId: string,
  direction: "inbound" | "outbound" = "inbound"
): Promise<DispatchRecord[]> {
  const response = await apiClient.get<{ data: DispatchResource[] }>(
    `/internal/v1/mobile/provider/inventory/dispatches?facility_id=${facilityId}&direction=${direction}`
  );
  return response.data.data.map(mapDispatch);
}

export async function confirmDelivery(dispatchId: string): Promise<DispatchRecord> {
  const response = await apiClient.post<{ data: DispatchResource }>(
    `/internal/v1/mobile/provider/inventory/dispatches/${dispatchId}/confirm`
  );
  return mapDispatch(response.data.data);
}
