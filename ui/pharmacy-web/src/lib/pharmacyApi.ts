/**
 * Pharmacy API service layer.
 *
 * All calls route through apiClient which injects trust headers
 * (X-Tenant-Id, X-Actor-Id, X-Correlation-Id, etc.) before they
 * reach Envoy ext_authz -> TSHEPO -> Pharmacy Service.
 */

import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// Types -- Enumerations
// ---------------------------------------------------------------------------

export type DispenseStatus =
  | "PENDING"
  | "ACCEPTED"
  | "PICKING"
  | "PARTIAL"
  | "COMPLETED"
  | "BACKORDERED"
  | "REVERSED"
  | "CANCELLED";

export type DispensePriority = "ROUTINE" | "URGENT" | "STAT" | "ASAP";

export type ItemStatus =
  | "PENDING"
  | "PICKED"
  | "SUBSTITUTED"
  | "BACKORDERED"
  | "RETURNED";

export type PickupMethod = "OTP" | "QR" | "BIOMETRIC";

export type ReconcileStockStatus = "PENDING" | "RESOLVED" | "EXPIRED";

// ---------------------------------------------------------------------------
// Types -- Dispense Order
// ---------------------------------------------------------------------------

export interface DispenseOrderSummary {
  id: string;
  tenantId: string;
  facilityId: string;
  orosOrderId: string;
  orosOrderNumber: string;
  patientCpid: string;
  priority: DispensePriority;
  status: DispenseStatus;
  itemCount: number;
  dispensedCount: number;
  pharmacistId: string | null;
  pharmacistName: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  acceptedAt: string | null;
  completedAt: string | null;
}

export interface DispenseItem {
  id: string;
  dispenseOrderId: string;
  drugCode: string;
  drugName: string;
  drugForm: string | null;
  drugStrength: string | null;
  quantityRequested: number;
  quantityDispensed: number;
  quantityRemaining: number;
  status: ItemStatus;
  batchNumber: string | null;
  expiryDate: string | null;
  barcode: string | null;
  substitutedFromCode: string | null;
  substitutedFromName: string | null;
  substitutionReason: string | null;
  fefoSuggestion: FefoSuggestion | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DispenseOrderDetail {
  id: string;
  tenantId: string;
  facilityId: string;
  orosOrderId: string;
  orosOrderNumber: string;
  patientCpid: string;
  priority: DispensePriority;
  status: DispenseStatus;
  items: DispenseItem[];
  pharmacistId: string | null;
  pharmacistName: string | null;
  pickupProof: PickupProof | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  acceptedAt: string | null;
  completedAt: string | null;
}

// ---------------------------------------------------------------------------
// Types -- Pick
// ---------------------------------------------------------------------------

export interface PickItemData {
  itemId: string;
  quantityPicked: number;
  batchNumber: string;
  expiryDate: string;
  barcode?: string;
}

export interface PickRequest {
  items: PickItemData[];
  notes?: string;
}

// ---------------------------------------------------------------------------
// Types -- Backorder
// ---------------------------------------------------------------------------

export interface BackorderRequest {
  notes: string;
  expectedRestockDate?: string;
}

// ---------------------------------------------------------------------------
// Types -- Substitution
// ---------------------------------------------------------------------------

export interface SubstituteRequest {
  replacementDrugCode: string;
  replacementDrugName: string;
  replacementDrugForm?: string;
  replacementDrugStrength?: string;
  reason: string;
  quantity: number;
  batchNumber: string;
  expiryDate: string;
}

export interface SubstitutionOption {
  drugCode: string;
  drugName: string;
  drugForm: string;
  drugStrength: string;
  stockOnHand: number;
  nearestExpiry: string | null;
  therapeuticEquivalence: string;
}

// ---------------------------------------------------------------------------
// Types -- Barcode
// ---------------------------------------------------------------------------

export interface BarcodeResult {
  drugCode: string;
  drugName: string;
  drugForm: string;
  drugStrength: string;
  batchNumber: string;
  expiryDate: string;
  manufacturer: string | null;
  gtin: string | null;
}

// ---------------------------------------------------------------------------
// Types -- FEFO
// ---------------------------------------------------------------------------

export interface FefoSuggestion {
  batchNumber: string;
  expiryDate: string;
  quantityAvailable: number;
  location: string | null;
}

// ---------------------------------------------------------------------------
// Types -- Pickup Proof
// ---------------------------------------------------------------------------

export interface PickupCreateRequest {
  method: PickupMethod;
  delegatedTo?: {
    name: string;
    idNumber: string;
    relationship: string;
  };
}

export interface PickupClaimRequest {
  token: string;
  fingerprint?: string;
}

export interface PickupProof {
  id: string;
  dispenseOrderId: string;
  method: PickupMethod;
  token: string;
  claimed: boolean;
  claimedAt: string | null;
  claimedBy: string | null;
  delegatedTo: {
    name: string;
    idNumber: string;
    relationship: string;
  } | null;
  createdAt: string;
  expiresAt: string;
}

// ---------------------------------------------------------------------------
// Types -- Return / Wastage / Reversal
// ---------------------------------------------------------------------------

export interface ReturnItemData {
  itemId: string;
  quantityReturned: number;
  reason: string;
  condition: "INTACT" | "DAMAGED" | "EXPIRED";
}

export interface ReturnRequest {
  items: ReturnItemData[];
  notes?: string;
}

export interface WastageItemData {
  itemId: string;
  quantityWasted: number;
  reason: string;
  disposalMethod: "INCINERATION" | "CHEMICAL" | "RETURN_TO_SUPPLIER" | "OTHER";
}

export interface WastageRequest {
  items: WastageItemData[];
  notes?: string;
  witnessId?: string;
}

export interface ReversalRequest {
  reason: string;
  authorizedBy?: string;
}

// ---------------------------------------------------------------------------
// Types -- Stock Reconciliation
// ---------------------------------------------------------------------------

export interface ReconcileSummary {
  id: string;
  tenantId: string;
  facilityId: string;
  itemCode: string;
  itemName: string;
  internalQuantity: number;
  externalQuantity: number;
  variance: number;
  confidenceScore: number;
  status: ReconcileStockStatus;
  source: string;
  resolvedBy: string | null;
  resolvedAt: string | null;
  resolutionNotes: string | null;
  createdAt: string;
}

export interface ResolveRequest {
  notes: string;
  adjustmentAction?: "ACCEPT_INTERNAL" | "ACCEPT_EXTERNAL" | "MANUAL_ADJUST";
  adjustedQuantity?: number;
}

// ---------------------------------------------------------------------------
// Types -- Stock Position
// ---------------------------------------------------------------------------

export interface StockPosition {
  drugCode: string;
  drugName: string;
  batchNumber: string;
  expiryDate: string;
  quantityOnHand: number;
  quantityReserved: number;
  quantityAvailable: number;
  location: string | null;
}

// ---------------------------------------------------------------------------
// Types -- Paged Response
// ---------------------------------------------------------------------------

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---------------------------------------------------------------------------
// API Functions
// ---------------------------------------------------------------------------

const V1 = "/v1";

export const pharmacyApi = {
  // -- Worklists --
  getWorklist: (facilityId: string, params?: Record<string, string>) => {
    const qp = new URLSearchParams(params);
    if (facilityId) qp.set("facilityId", facilityId);
    const qs = qp.toString() ? `?${qp.toString()}` : "";
    return apiClient.get<DispenseOrderSummary[]>(`${V1}/dispense-orders${qs}`);
  },

  // -- Dispense Orders --
  getDispenseOrder: (id: string) =>
    apiClient.get<DispenseOrderDetail>(`${V1}/dispense-orders/${encodeURIComponent(id)}`),

  getPatientDispenseOrders: (cpid: string, params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<DispenseOrderSummary[]>(
      `${V1}/dispense-orders/patient/${encodeURIComponent(cpid)}${qs}`,
    );
  },

  acceptOrder: (id: string) =>
    apiClient.post<DispenseOrderDetail>(`${V1}/dispense-orders/${encodeURIComponent(id)}/accept`),

  pickItems: (id: string, picks: PickRequest) =>
    apiClient.post<DispenseOrderDetail>(`${V1}/dispense-orders/${encodeURIComponent(id)}/pick`, picks),

  partialDispense: (id: string) =>
    apiClient.post<DispenseOrderDetail>(`${V1}/dispense-orders/${encodeURIComponent(id)}/partial`),

  completeDispense: (id: string) =>
    apiClient.post<DispenseOrderDetail>(`${V1}/dispense-orders/${encodeURIComponent(id)}/complete`),

  backorder: (id: string, data: BackorderRequest) =>
    apiClient.post<DispenseOrderDetail>(`${V1}/dispense-orders/${encodeURIComponent(id)}/backorder`, data),

  // -- Barcode --
  lookupBarcode: (code: string) =>
    apiClient.get<BarcodeResult>(`${V1}/items/lookup-by-barcode?code=${encodeURIComponent(code)}`),

  // -- Substitution --
  getSubstitutionOptions: (itemId: string) =>
    apiClient.get<SubstitutionOption[]>(
      `${V1}/dispense-items/${encodeURIComponent(itemId)}/substitution-options`,
    ),

  substituteItem: (itemId: string, body: SubstituteRequest) =>
    apiClient.post<DispenseItem>(
      `${V1}/dispense-items/${encodeURIComponent(itemId)}/substitute`,
      body,
    ),

  // -- Pickup --
  createPickup: (orderId: string, data: PickupCreateRequest) =>
    apiClient.post<PickupProof>(
      `${V1}/dispense-orders/${encodeURIComponent(orderId)}/pickup/create`,
      data,
    ),

  claimPickup: (data: PickupClaimRequest) =>
    apiClient.post<PickupProof>(`${V1}/pickup/claim`, data),

  // -- Returns / Wastage / Reversal --
  processReturn: (orderId: string, data: ReturnRequest) =>
    apiClient.post<DispenseOrderDetail>(
      `${V1}/dispense-orders/${encodeURIComponent(orderId)}/return`,
      data,
    ),

  processWastage: (orderId: string, data: WastageRequest) =>
    apiClient.post<DispenseOrderDetail>(
      `${V1}/dispense-orders/${encodeURIComponent(orderId)}/wastage`,
      data,
    ),

  processReversal: (orderId: string, data: ReversalRequest) =>
    apiClient.post<DispenseOrderDetail>(
      `${V1}/dispense-orders/${encodeURIComponent(orderId)}/reversal`,
      data,
    ),

  // -- Stock Reconciliation --
  getReconcilePending: (page: number = 0, size: number = 20) =>
    apiClient.get<PagedResponse<ReconcileSummary>>(
      `${V1}/reconcile/stock/pending?page=${page}&size=${size}`,
    ),

  resolveReconcile: (recId: string, data: ResolveRequest) =>
    apiClient.post<ReconcileSummary>(
      `${V1}/reconcile/stock/${encodeURIComponent(recId)}/resolve`,
      data,
    ),
};
