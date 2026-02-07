/**
 * Inventory API service layer.
 *
 * All calls route through apiClient which injects trust headers
 * (X-Tenant-Id, X-Actor-Id, X-Correlation-Id, etc.) before they
 * reach Envoy ext_authz -> TSHEPO -> Inventory Service.
 */

import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// Types -- Enumerations
// ---------------------------------------------------------------------------

export type MovementType = "RECEIPT" | "ISSUE" | "TRANSFER" | "ADJUSTMENT" | "WASTAGE" | "RETURN" | "CONSUMPTION";
export type ItemCategory = "PHARMACEUTICAL" | "MEDICAL_SUPPLY" | "LAB_REAGENT" | "EQUIPMENT" | "CLINICAL_SUPPLY" | "GENERAL";
export type CountStatus = "DRAFT" | "IN_PROGRESS" | "SUBMITTED" | "APPROVED" | "REJECTED";
export type RequisitionStatus = "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED" | "FULFILLED" | "PARTIALLY_FULFILLED" | "CANCELLED";
export type HandoverStatus = "PENDING_OUTGOING_SIGN" | "PENDING_INCOMING_SIGN" | "COMPLETED" | "CANCELLED";
export type ReconcileStatus = "PENDING" | "RESOLVED" | "EXPIRED";
export type AdjustmentAction = "ACCEPT_INTERNAL" | "ACCEPT_EXTERNAL" | "MANUAL_ADJUST";
export type DisposalMethod = "INCINERATION" | "CHEMICAL" | "RETURN_TO_SUPPLIER" | "OTHER";
export type StoreType = "MAIN_WAREHOUSE" | "PHARMACY_STORE" | "WARD_CUPBOARD" | "COLD_CHAIN" | "QUARANTINE" | "LAB_STORE";
export type CountType = "FULL" | "CYCLE" | "SPOT";
export type RequisitionPriority = "ROUTINE" | "URGENT" | "EMERGENCY";
export type ReturnType = "RETURN_TO_STOCK" | "RETURN_TO_SUPPLIER";

// ---------------------------------------------------------------------------
// Types -- Item
// ---------------------------------------------------------------------------

export interface Item {
  id: string;
  tenantId: string;
  itemCode: string;
  name: string;
  description: string | null;
  category: ItemCategory;
  unitOfMeasure: string;
  barcode: string | null;
  manufacturer: string | null;
  reorderPoint: number | null;
  reorderQuantity: number | null;
  maxStockLevel: number | null;
  coldChainRequired: boolean;
  controlledSubstance: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateItemRequest {
  itemCode: string;
  name: string;
  description?: string;
  category: ItemCategory;
  unitOfMeasure: string;
  barcode?: string;
  manufacturer?: string;
  reorderPoint?: number;
  reorderQuantity?: number;
  maxStockLevel?: number;
  coldChainRequired?: boolean;
  controlledSubstance?: boolean;
}

export interface ImportResult {
  imported: number;
  updated: number;
  skipped: number;
  errors: string[];
}

export interface BarcodeResult {
  itemCode: string;
  itemName: string;
  category: ItemCategory | null;
  unitOfMeasure: string | null;
  batchNumber: string | null;
  expiryDate: string | null;
  manufacturer: string | null;
  gtin: string | null;
}

// ---------------------------------------------------------------------------
// Types -- On-Hand
// ---------------------------------------------------------------------------

export interface OnHandPosition {
  id: string;
  tenantId: string;
  facilityId: string;
  storeId: string;
  storeName: string | null;
  binId: string | null;
  binCode: string | null;
  itemCode: string;
  itemName: string;
  category: ItemCategory;
  unitOfMeasure: string | null;
  batchNumber: string;
  expiryDate: string;
  quantityOnHand: number;
  quantityReserved: number;
  quantityAvailable: number;
  reorderPoint: number | null;
  belowReorder: boolean;
  updatedAt: string;
}

export interface StockoutRisk {
  itemCode: string;
  itemName: string;
  category: ItemCategory;
  totalOnHand: number;
  avgDailyConsumption: number;
  daysOfStock: number;
  reorderPoint: number | null;
  facilityId: string;
  storeId: string;
  storeName: string | null;
  isStockedOut: boolean;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---------------------------------------------------------------------------
// Types -- Ledger
// ---------------------------------------------------------------------------

export interface LedgerEvent {
  id: string;
  tenantId: string;
  facilityId: string;
  storeId: string;
  storeName: string | null;
  binId: string | null;
  itemCode: string;
  itemName: string;
  batchNumber: string | null;
  expiryDate: string | null;
  movementType: MovementType;
  quantity: number;
  balanceAfter: number;
  reason: string | null;
  referenceType: string | null;
  referenceId: string | null;
  sourceStoreId: string | null;
  destinationStoreId: string | null;
  disposalMethod: DisposalMethod | null;
  witnessId: string | null;
  actorId: string;
  timestamp: string;
  notes: string | null;
}

export interface ReceiptRequest {
  storeId: string;
  binId?: string;
  itemCode: string;
  batchNumber: string;
  expiryDate: string;
  quantity: number;
  supplierName?: string;
  deliveryNote?: string;
  notes?: string;
}

export interface IssueRequest {
  storeId: string;
  binId?: string;
  itemCode: string;
  batchNumber: string;
  quantity: number;
  issuedTo?: string;
  reason?: string;
  notes?: string;
}

export interface TransferRequest {
  sourceStoreId: string;
  sourceBinId?: string;
  destinationStoreId: string;
  destinationBinId?: string;
  itemCode: string;
  batchNumber: string;
  expiryDate?: string;
  quantity: number;
  reason?: string;
  notes?: string;
}

export interface AdjustRequest {
  storeId: string;
  binId?: string;
  itemCode: string;
  batchNumber: string;
  expiryDate?: string;
  quantity: number;
  reason: string;
  referenceType?: string;
  referenceId?: string;
  notes?: string;
}

export interface WastageRequest {
  storeId: string;
  binId?: string;
  itemCode: string;
  batchNumber: string;
  quantity: number;
  reason: string;
  disposalMethod: DisposalMethod;
  witnessId?: string;
  notes?: string;
}

export interface ReturnRequest {
  storeId: string;
  binId?: string;
  itemCode: string;
  batchNumber: string;
  expiryDate?: string;
  quantity: number;
  reason: string;
  returnType: ReturnType;
  condition?: "INTACT" | "DAMAGED" | "EXPIRED";
  notes?: string;
}

// ---------------------------------------------------------------------------
// Types -- FEFO
// ---------------------------------------------------------------------------

export interface FefoSuggestion {
  batchNumber: string;
  expiryDate: string;
  quantityAvailable: number;
  suggestedPickQty: number;
  binId: string | null;
  binCode: string | null;
}

export interface FefoSuggestRequest {
  storeId: string;
  itemCode: string;
  quantityNeeded: number;
}

// ---------------------------------------------------------------------------
// Types -- Stock Count
// ---------------------------------------------------------------------------

export interface CountLine {
  id: string;
  countId: string;
  itemCode: string;
  itemName: string;
  batchNumber: string;
  expiryDate: string | null;
  binCode: string | null;
  expectedQty: number;
  countedQty: number | null;
  variance: number | null;
  barcodeScan: string | null;
  notes: string | null;
  countedAt: string | null;
  countedBy: string | null;
}

export interface StockCount {
  id: string;
  tenantId: string;
  facilityId: string;
  storeId: string;
  storeName: string | null;
  status: CountStatus;
  countType: CountType;
  lines: CountLine[];
  totalLines: number;
  countedLines: number;
  totalVariance: number;
  createdBy: string;
  approvedBy: string | null;
  rejectedBy: string | null;
  rejectionReason: string | null;
  notes: string | null;
  createdAt: string;
  startedAt: string | null;
  submittedAt: string | null;
  completedAt: string | null;
  updatedAt: string;
}

export interface CreateCountRequest {
  storeId: string;
  countType: CountType;
  itemCodes?: string[];
  notes?: string;
}

export interface RecordCountLineRequest {
  countedQty: number;
  barcodeScan?: string;
  notes?: string;
}

// ---------------------------------------------------------------------------
// Types -- Requisition
// ---------------------------------------------------------------------------

export interface RequisitionLine {
  id: string;
  itemCode: string;
  itemName: string;
  unitOfMeasure: string | null;
  quantityRequested: number;
  quantityApproved: number | null;
  quantityFulfilled: number | null;
  batchNumber: string | null;
  expiryDate: string | null;
  notes: string | null;
}

export interface Requisition {
  id: string;
  tenantId: string;
  requisitionNumber: string;
  requestingFacilityId: string;
  requestingFacilityName: string | null;
  requestingStoreId: string | null;
  supplyingFacilityId: string;
  supplyingFacilityName: string | null;
  supplyingStoreId: string | null;
  status: RequisitionStatus;
  priority: RequisitionPriority;
  lines: RequisitionLine[];
  createdBy: string;
  approvedBy: string | null;
  rejectedBy: string | null;
  rejectionReason: string | null;
  notes: string | null;
  createdAt: string;
  submittedAt: string | null;
  approvedAt: string | null;
  fulfilledAt: string | null;
  updatedAt: string;
}

export interface CreateRequisitionRequest {
  requestingFacilityId: string;
  requestingStoreId?: string;
  supplyingFacilityId: string;
  supplyingStoreId?: string;
  priority?: RequisitionPriority;
  lines: {
    itemCode: string;
    itemName: string;
    quantityRequested: number;
    notes?: string;
  }[];
  notes?: string;
}

export interface FulfillLineRequest {
  lineId: string;
  quantityFulfilled: number;
  batchNumber: string;
  expiryDate: string;
}

// ---------------------------------------------------------------------------
// Types -- Handover
// ---------------------------------------------------------------------------

export interface Handover {
  id: string;
  tenantId: string;
  facilityId: string;
  storeId: string;
  storeName: string | null;
  status: HandoverStatus;
  outgoingActorId: string;
  outgoingActorName: string | null;
  incomingActorId: string;
  incomingActorName: string | null;
  snapshotItemCount: number | null;
  outgoingNotes: string | null;
  incomingNotes: string | null;
  outgoingSignedAt: string | null;
  incomingSignedAt: string | null;
  createdAt: string;
  completedAt: string | null;
}

// ---------------------------------------------------------------------------
// Types -- Reconciliation
// ---------------------------------------------------------------------------

export interface ReconcileItem {
  id: string;
  tenantId: string;
  facilityId: string;
  storeId: string;
  itemCode: string;
  itemName: string;
  batchNumber: string | null;
  internalQuantity: number;
  externalQuantity: number;
  variance: number;
  confidenceScore: number;
  status: ReconcileStatus;
  source: string;
  resolvedBy: string | null;
  resolvedAt: string | null;
  resolutionAction: AdjustmentAction | null;
  resolutionNotes: string | null;
  createdAt: string;
}

// ---------------------------------------------------------------------------
// API Functions
// ---------------------------------------------------------------------------

const V1 = "/v1";

export const inventoryApi = {
  // -- Items --
  createItem: (data: CreateItemRequest) =>
    apiClient.post<Item>(`${V1}/items`, data),

  importItems: (items: CreateItemRequest[]) =>
    apiClient.post<ImportResult>(`${V1}/items/import`, { items }),

  lookupBarcode: (code: string) =>
    apiClient.get<BarcodeResult>(`${V1}/items/lookup-by-barcode?code=${encodeURIComponent(code)}`),

  getItem: (itemCode: string) =>
    apiClient.get<Item>(`${V1}/items/${encodeURIComponent(itemCode)}`),

  // -- On-Hand --
  getOnHand: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<PagedResponse<OnHandPosition>>(`${V1}/onhand${qs}`);
  },

  getNearExpiry: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<PagedResponse<OnHandPosition>>(`${V1}/onhand/near-expiry${qs}`);
  },

  getStockouts: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<PagedResponse<StockoutRisk>>(`${V1}/onhand/stockouts${qs}`);
  },

  // -- Ledger --
  postReceipt: (data: ReceiptRequest) =>
    apiClient.post<LedgerEvent>(`${V1}/ledger/receipt`, data),

  postIssue: (data: IssueRequest) =>
    apiClient.post<LedgerEvent>(`${V1}/ledger/issue`, data),

  postTransfer: (data: TransferRequest) =>
    apiClient.post<LedgerEvent>(`${V1}/ledger/transfer`, data),

  postAdjust: (data: AdjustRequest) =>
    apiClient.post<LedgerEvent>(`${V1}/ledger/adjust`, data),

  postWastage: (data: WastageRequest) =>
    apiClient.post<LedgerEvent>(`${V1}/ledger/wastage`, data),

  postReturn: (data: ReturnRequest) =>
    apiClient.post<LedgerEvent>(`${V1}/ledger/return`, data),

  queryLedger: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<PagedResponse<LedgerEvent>>(`${V1}/ledger${qs}`);
  },

  // -- FEFO --
  suggestFefo: (data: FefoSuggestRequest) =>
    apiClient.post<FefoSuggestion[]>(`${V1}/fefo/suggest`, data),

  // -- Stock Counts --
  createCount: (data: CreateCountRequest) =>
    apiClient.post<StockCount>(`${V1}/counts`, data),

  getCount: (id: string) =>
    apiClient.get<StockCount>(`${V1}/counts/${encodeURIComponent(id)}`),

  startCount: (id: string) =>
    apiClient.post<StockCount>(`${V1}/counts/${encodeURIComponent(id)}/start`),

  recordCountLine: (id: string, lineId: string, data: RecordCountLineRequest) =>
    apiClient.post<CountLine>(
      `${V1}/counts/${encodeURIComponent(id)}/lines/${encodeURIComponent(lineId)}`,
      data,
    ),

  submitCount: (id: string) =>
    apiClient.post<StockCount>(`${V1}/counts/${encodeURIComponent(id)}/submit`),

  approveCount: (id: string, data?: { notes?: string }) =>
    apiClient.post<StockCount>(`${V1}/counts/${encodeURIComponent(id)}/approve`, data),

  rejectCount: (id: string, data: { reason: string }) =>
    apiClient.post<StockCount>(`${V1}/counts/${encodeURIComponent(id)}/reject`, data),

  // -- Requisitions --
  createRequisition: (data: CreateRequisitionRequest) =>
    apiClient.post<Requisition>(`${V1}/requisitions`, data),

  submitRequisition: (id: string) =>
    apiClient.post<Requisition>(`${V1}/requisitions/${encodeURIComponent(id)}/submit`),

  approveRequisition: (id: string, data?: { lineAdjustments?: { lineId: string; quantityApproved: number }[]; notes?: string }) =>
    apiClient.post<Requisition>(`${V1}/requisitions/${encodeURIComponent(id)}/approve`, data),

  rejectRequisition: (id: string, data: { reason: string }) =>
    apiClient.post<Requisition>(`${V1}/requisitions/${encodeURIComponent(id)}/reject`, data),

  fulfillRequisition: (id: string, data: { lines: FulfillLineRequest[]; notes?: string }) =>
    apiClient.post<Requisition>(`${V1}/requisitions/${encodeURIComponent(id)}/fulfill`, data),

  // -- Handover --
  startHandover: (data: { storeId: string; outgoingActorId: string; incomingActorId: string; notes?: string }) =>
    apiClient.post<Handover>(`${V1}/handover/start`, data),

  signOutgoing: (id: string, data?: { notes?: string }) =>
    apiClient.post<Handover>(`${V1}/handover/${encodeURIComponent(id)}/sign-outgoing`, data),

  signIncoming: (id: string, data?: { notes?: string }) =>
    apiClient.post<Handover>(`${V1}/handover/${encodeURIComponent(id)}/sign-incoming`, data),

  listHandovers: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<PagedResponse<Handover>>(`${V1}/handover${qs}`);
  },

  // -- Reconciliation --
  getReconcilePending: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<PagedResponse<ReconcileItem>>(`${V1}/reconcile/pending${qs}`);
  },

  resolveReconcile: (id: string, data: { action: AdjustmentAction; adjustedQuantity?: number; notes: string }) =>
    apiClient.post<ReconcileItem>(`${V1}/reconcile/${encodeURIComponent(id)}/resolve`, data),
};
