/**
 * Inventory Service — Stock and dispatch management for supervisor mode.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/inventory/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type {
  StockItem,
  DispatchRecord,
  InventoryItem,
  StockAlert,
  Requisition,
  RequisitionUrgency,
} from "../types";

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

/* ── On-Hand Inventory ──────────────────────────────────────────────── */

interface InventoryItemResource {
  id: string;
  name: string;
  category: string;
  quantity: number;
  unit: string;
  status: string;
  reorder_level: number;
  facility_id: string;
  last_updated_at?: string;
}

function mapInventoryItem(r: InventoryItemResource): InventoryItem {
  return {
    id: r.id,
    name: r.name,
    category: r.category,
    quantity: r.quantity,
    unit: r.unit,
    status: r.status as InventoryItem["status"],
    reorderLevel: r.reorder_level,
    facilityId: r.facility_id,
    lastUpdatedAt: r.last_updated_at,
  };
}

export async function fetchInventoryOnHand(): Promise<InventoryItem[]> {
  const response = await apiClient.get<{ data: InventoryItemResource[] }>(
    "/internal/v1/mobile/provider/inventory/on-hand"
  );
  return response.data.data.map(mapInventoryItem);
}

/* ── Stock Alerts ───────────────────────────────────────────────────── */

interface StockAlertResource {
  id: string;
  item_id: string;
  item_name: string;
  alert_type: string;
  severity: string;
  current_quantity: number;
  reorder_level: number;
  created_at: string;
}

function mapStockAlert(r: StockAlertResource): StockAlert {
  return {
    id: r.id,
    itemId: r.item_id,
    itemName: r.item_name,
    alertType: r.alert_type as StockAlert["alertType"],
    severity: r.severity as StockAlert["severity"],
    currentQuantity: r.current_quantity,
    reorderLevel: r.reorder_level,
    createdAt: r.created_at,
  };
}

export async function fetchStockAlertsList(): Promise<StockAlert[]> {
  const response = await apiClient.get<{ data: StockAlertResource[] }>(
    "/internal/v1/mobile/provider/inventory/alerts"
  );
  return response.data.data.map(mapStockAlert);
}

/* ── Requisitions ───────────────────────────────────────────────────── */

interface RequisitionPayload {
  itemId: string;
  itemName: string;
  quantity: number;
  urgency: RequisitionUrgency;
  notes?: string;
}

interface RequisitionResource {
  id: string;
  item_id: string;
  item_name: string;
  quantity: number;
  urgency: string;
  notes?: string;
  status: string;
  requested_by: string;
  created_at: string;
}

function mapRequisition(r: RequisitionResource): Requisition {
  return {
    id: r.id,
    itemId: r.item_id,
    itemName: r.item_name,
    quantity: r.quantity,
    urgency: r.urgency as Requisition["urgency"],
    notes: r.notes,
    status: r.status as Requisition["status"],
    requestedBy: r.requested_by,
    createdAt: r.created_at,
  };
}

export async function createRequisition(
  payload: RequisitionPayload
): Promise<Requisition> {
  const response = await apiClient.post<{ data: RequisitionResource }>(
    "/internal/v1/mobile/provider/inventory/requisitions",
    {
      item_id: payload.itemId,
      item_name: payload.itemName,
      quantity: payload.quantity,
      urgency: payload.urgency,
      notes: payload.notes,
    }
  );
  return mapRequisition(response.data.data);
}
