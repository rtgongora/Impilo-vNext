/**
 * Experience UI - Inventory Query Hooks
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface InventoryItemApiResource {
  id: string;
  type: string;
  attributes: {
    facility_id: string;
    product_code: string;
    product_name: string;
    category: string | null;
    quantity_on_hand: number;
    reorder_level: number;
    unit: string;
    status: string;
    last_counted_at: string | null;
    created_at: string;
    updated_at: string;
  };
}

interface InventoryCountApiResource {
  id: string;
  type: string;
  attributes: {
    count_date: string;
    counted_by: string;
    item_count: number;
    discrepancies: number;
    status: string;
    notes?: string | null;
  };
}

interface InventoryMovementApiResource {
  id: string;
  type: string;
  attributes: {
    moved_at: string;
    item_name: string;
    from_location: string | null;
    to_location: string | null;
    quantity: number;
    reason: string | null;
    performed_by: string;
  };
}

interface InventoryRequisitionApiResource {
  id: string;
  type: string;
  attributes: {
    requisition_number: string;
    requested_by: string;
    item_count: number;
    status: string;
    needed_by: string | null;
    notes?: string | null;
    created_at: string;
  };
}

export interface InventoryItem {
  id: string;
  facilityId: string;
  productCode: string;
  productName: string;
  category: string;
  quantityOnHand: number;
  reorderLevel: number;
  unit: string;
  status: string;
  lastCountedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface InventoryCount {
  id: string;
  countDate: string;
  countedBy: string;
  itemCount: number;
  discrepancies: number;
  status: string;
  notes: string;
}

export interface InventoryMovement {
  id: string;
  movedAt: string;
  itemName: string;
  fromLocation: string;
  toLocation: string;
  quantity: number;
  reason: string;
  performedBy: string;
}

export interface InventoryRequisition {
  id: string;
  requisitionNumber: string;
  requestedBy: string;
  itemCount: number;
  status: string;
  neededBy: string | null;
  notes: string;
  createdAt: string;
}

export interface CreateInventoryRequisitionPayload {
  facilityId: string;
  requestedBy: string;
  itemCount: number;
  neededBy?: string;
  notes?: string;
  requisitionNumber?: string;
}

type InventoryItemsResponse = ApiResponse<InventoryItemApiResource[]>;
type InventoryCountsResponse = ApiResponse<InventoryCountApiResource[]>;
type InventoryMovementsResponse = ApiResponse<InventoryMovementApiResource[]>;
type InventoryRequisitionsResponse = ApiResponse<InventoryRequisitionApiResource[]>;
type InventoryRequisitionResponse = ApiResponse<InventoryRequisitionApiResource>;

function normalizeItem(resource: InventoryItemApiResource): InventoryItem {
  return {
    id: resource.id,
    facilityId: resource.attributes.facility_id,
    productCode: resource.attributes.product_code,
    productName: resource.attributes.product_name,
    category: resource.attributes.category ?? "Uncategorized",
    quantityOnHand: resource.attributes.quantity_on_hand,
    reorderLevel: resource.attributes.reorder_level,
    unit: resource.attributes.unit,
    status: resource.attributes.status,
    lastCountedAt: resource.attributes.last_counted_at,
    createdAt: resource.attributes.created_at,
    updatedAt: resource.attributes.updated_at,
  };
}

function normalizeCount(resource: InventoryCountApiResource): InventoryCount {
  return {
    id: resource.id,
    countDate: resource.attributes.count_date,
    countedBy: resource.attributes.counted_by,
    itemCount: resource.attributes.item_count,
    discrepancies: resource.attributes.discrepancies,
    status: resource.attributes.status,
    notes: resource.attributes.notes ?? "",
  };
}

function normalizeMovement(resource: InventoryMovementApiResource): InventoryMovement {
  return {
    id: resource.id,
    movedAt: resource.attributes.moved_at,
    itemName: resource.attributes.item_name,
    fromLocation: resource.attributes.from_location ?? "Unspecified source",
    toLocation: resource.attributes.to_location ?? "Unspecified destination",
    quantity: resource.attributes.quantity,
    reason: resource.attributes.reason ?? "General stock movement",
    performedBy: resource.attributes.performed_by,
  };
}

function normalizeRequisition(resource: InventoryRequisitionApiResource): InventoryRequisition {
  return {
    id: resource.id,
    requisitionNumber: resource.attributes.requisition_number,
    requestedBy: resource.attributes.requested_by,
    itemCount: resource.attributes.item_count,
    status: resource.attributes.status,
    neededBy: resource.attributes.needed_by,
    notes: resource.attributes.notes ?? "",
    createdAt: resource.attributes.created_at,
  };
}

export function useInventoryItems(facilityId: string) {
  return useQuery<InventoryItem[]>({
    queryKey: ["inventory-items", { facilityId }],
    queryFn: async () => {
      const response = await apiClient.get<InventoryItemsResponse>(
        `/internal/v1/inventory/items?facility_id=${encodeURIComponent(facilityId)}`,
      );
      return (response.data ?? []).map(normalizeItem);
    },
    enabled: !!facilityId,
  });
}

export function useInventoryCounts(facilityId: string) {
  return useQuery<InventoryCount[]>({
    queryKey: ["inventory-counts", { facilityId }],
    queryFn: async () => {
      const response = await apiClient.get<InventoryCountsResponse>(
        `/internal/v1/inventory/counts?facility_id=${encodeURIComponent(facilityId)}`,
      );
      return (response.data ?? []).map(normalizeCount);
    },
    enabled: !!facilityId,
  });
}

export function useInventoryMovements(facilityId: string) {
  return useQuery<InventoryMovement[]>({
    queryKey: ["inventory-movements", { facilityId }],
    queryFn: async () => {
      const response = await apiClient.get<InventoryMovementsResponse>(
        `/internal/v1/inventory/movements?facility_id=${encodeURIComponent(facilityId)}`,
      );
      return (response.data ?? []).map(normalizeMovement);
    },
    enabled: !!facilityId,
  });
}

export function useInventoryRequisitions(facilityId: string) {
  return useQuery<InventoryRequisition[]>({
    queryKey: ["inventory-requisitions", { facilityId }],
    queryFn: async () => {
      const response = await apiClient.get<InventoryRequisitionsResponse>(
        `/internal/v1/inventory/requisitions?facility_id=${encodeURIComponent(facilityId)}`,
      );
      return (response.data ?? []).map(normalizeRequisition);
    },
    enabled: !!facilityId,
  });
}

export function useCreateInventoryRequisition() {
  const queryClient = useQueryClient();

  return useMutation<InventoryRequisition, unknown, CreateInventoryRequisitionPayload>({
    mutationFn: async (payload) => {
      const response = await apiClient.post<InventoryRequisitionResponse>("/internal/v1/inventory/requisitions", {
        facility_id: payload.facilityId,
        requested_by: payload.requestedBy,
        item_count: payload.itemCount,
        needed_by: payload.neededBy,
        notes: payload.notes,
        requisition_number: payload.requisitionNumber,
      });
      return normalizeRequisition(response.data);
    },
    onSuccess: (_data, payload) => {
      queryClient.invalidateQueries({ queryKey: ["inventory-requisitions", { facilityId: payload.facilityId }] });
    },
  });
}
