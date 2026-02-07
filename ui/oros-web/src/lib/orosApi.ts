/**
 * OROS API service layer.
 *
 * All calls route through apiClient which injects trust headers
 * (X-Tenant-Id, X-Actor-Id, X-Correlation-Id, etc.) before they
 * reach Envoy ext_authz -> TSHEPO -> OROS.
 */

import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// Types -- Enumerations
// ---------------------------------------------------------------------------

export type OrderType = "LAB" | "IMAGING" | "PHARMACY" | "REFERRAL" | "PROCEDURE" | "SUPPLY";
export type OrderStatus = "PLACED" | "ACCEPTED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED" | "REJECTED" | "EXPIRED";
export type OrderPriority = "ROUTINE" | "URGENT" | "STAT" | "ASAP";
export type AdapterMode = "INTERNAL" | "ADAPTER" | "HYBRID";
export type RouteStatus = "PENDING" | "DISPATCHED" | "DELIVERED" | "FAILED" | "RETRYING";
export type ReconcileStatus = "PENDING" | "MATCHED" | "RESOLVED" | "EXPIRED";
export type WorkstepStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "SKIPPED" | "FAILED";
export type ResultType = "NUMERIC" | "TEXT" | "CODED" | "DOCUMENT" | "IMAGE";
export type Interpretation = "NORMAL" | "ABNORMAL" | "CRITICAL_HIGH" | "CRITICAL_LOW" | "HIGH" | "LOW";

// ---------------------------------------------------------------------------
// Types -- Order
// ---------------------------------------------------------------------------

export interface OrderItem {
  id: string;
  catalogCode: string;
  catalogSystem: string | null;
  name: string;
  quantity: number;
  specimenType: string | null;
  bodysite: string | null;
  notes: string | null;
  status: "PENDING" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
}

export interface Order {
  id: string;
  tenantId: string;
  orderNumber: string;
  type: OrderType;
  status: OrderStatus;
  priority: OrderPriority;
  patientCpid: string;
  facilityId: string;
  departmentId: string | null;
  placerProviderId: string | null;
  encounterId: string | null;
  journeyId: string | null;
  clinicalNotes: string | null;
  items: OrderItem[];
  resultCount: number;
  acknowledged: boolean;
  hasCriticalResult: boolean;
  cancelReason: string | null;
  placedAt: string;
  acceptedAt: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PlaceOrderRequest {
  type: OrderType;
  priority: OrderPriority;
  patientCpid: string;
  facilityId: string;
  departmentId?: string;
  encounterId?: string;
  journeyId?: string;
  clinicalNotes?: string;
  items: OrderItemRequest[];
  scheduledAt?: string;
}

export interface OrderItemRequest {
  catalogCode: string;
  catalogSystem?: string;
  name: string;
  quantity?: number;
  specimenType?: string;
  bodysite?: string;
  notes?: string;
}

export interface CancelOrderRequest {
  reason: string;
}

// ---------------------------------------------------------------------------
// Types -- Worklist
// ---------------------------------------------------------------------------

export interface WorklistItem {
  id: string;
  tenantId: string;
  orderId: string;
  orderNumber: string;
  orderType: OrderType;
  priority: OrderPriority;
  status: "PENDING" | "ACCEPTED" | "IN_PROGRESS" | "COMPLETED" | "REJECTED";
  patientCpid: string;
  departmentId: string;
  departmentName: string;
  assignedTo: string | null;
  itemCount: number;
  placedAt: string;
  acceptedAt: string | null;
  rejectedAt: string | null;
  rejectionReason: string | null;
  createdAt: string;
}

// ---------------------------------------------------------------------------
// Types -- Workstep
// ---------------------------------------------------------------------------

export interface Workstep {
  id: string;
  orderId: string;
  stepNumber: number;
  name: string;
  status: WorkstepStatus;
  assignedTo: string | null;
  notes: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

// ---------------------------------------------------------------------------
// Types -- Result
// ---------------------------------------------------------------------------

export interface ResultAttachment {
  fileName: string;
  contentType: string;
  url: string;
  sizeBytes: number;
}

export interface Result {
  id: string;
  orderId: string;
  orderItemId: string | null;
  resultType: ResultType;
  code: string | null;
  codeSystem: string | null;
  displayName: string | null;
  value: string;
  unit: string | null;
  referenceRange: string | null;
  interpretation: Interpretation | null;
  isCritical: boolean;
  criticalFlaggedAt: string | null;
  criticalFlaggedBy: string | null;
  criticalReason: string | null;
  notes: string | null;
  performedAt: string | null;
  performedBy: string | null;
  verifiedBy: string | null;
  documentUrl: string | null;
  attachments: ResultAttachment[];
  acknowledged: boolean;
  acknowledgedAt: string | null;
  acknowledgedBy: string | null;
  postedAt: string;
  createdAt: string;
}

export interface PostResultRequest {
  resultType: ResultType;
  orderItemId?: string;
  code?: string;
  codeSystem?: string;
  displayName?: string;
  value: string;
  unit?: string;
  referenceRange?: string;
  interpretation?: Interpretation;
  isCritical?: boolean;
  notes?: string;
  performedAt?: string;
  performedBy?: string;
  verifiedBy?: string;
}

// ---------------------------------------------------------------------------
// Types -- Acknowledgement
// ---------------------------------------------------------------------------

export interface Ack {
  id: string;
  orderId: string;
  resultIds: string[];
  acknowledgedBy: string;
  acknowledgedByName: string | null;
  notes: string | null;
  acknowledgedAt: string;
}

export interface AcknowledgeRequest {
  resultIds: string[];
  notes?: string;
}

// ---------------------------------------------------------------------------
// Types -- Route
// ---------------------------------------------------------------------------

export interface Route {
  id: string;
  orderId: string;
  orderNumber: string;
  targetType: "INTERNAL" | "LIS" | "RIS" | "PHARMACY" | "EXTERNAL";
  targetIdentifier: string;
  status: RouteStatus;
  attemptCount: number;
  maxAttempts: number;
  lastAttemptAt: string | null;
  lastError: string | null;
  deliveredAt: string | null;
  createdAt: string;
}

// ---------------------------------------------------------------------------
// Types -- Reconciliation
// ---------------------------------------------------------------------------

export interface ReconcileCandidate {
  orderId: string;
  orderNumber: string;
  patientCpid: string;
  orderType: OrderType;
  confidence: number;
  matchReasons: string[];
}

export interface ReconcileItem {
  id: string;
  tenantId: string;
  type: "UNMATCHED_RESULT" | "ORPHANED_ORDER";
  status: ReconcileStatus;
  sourceIdentifier: string;
  patientCpid: string;
  orderType: OrderType;
  resultSummary: string;
  candidateOrders: ReconcileCandidate[];
  confidenceScore: number;
  matchedOrderId: string | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
  resolutionNotes: string | null;
  receivedAt: string;
  createdAt: string;
}

export interface MatchReconcileRequest {
  orderId: string;
  notes?: string;
}

export interface ResolveReconcileRequest {
  resolution: "DUPLICATE" | "INVALID" | "ORPHANED" | "OTHER";
  notes?: string;
}

// ---------------------------------------------------------------------------
// Types -- Catalog
// ---------------------------------------------------------------------------

export interface CatalogItem {
  id: string;
  tenantId: string;
  code: string;
  codeSystem: string | null;
  name: string;
  description: string | null;
  orderType: OrderType;
  category: string | null;
  departmentId: string | null;
  departmentName: string | null;
  specimenType: string | null;
  turnaroundMinutes: number | null;
  active: boolean;
  price: number | null;
  currency: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CatalogImportItem {
  code: string;
  codeSystem?: string;
  name: string;
  description?: string;
  orderType: OrderType;
  category?: string;
  departmentId?: string;
  specimenType?: string;
  turnaroundMinutes?: number;
  active?: boolean;
  price?: number;
  currency?: string;
}

export interface CatalogImportResult {
  imported: number;
  updated: number;
  skipped: number;
  errors: string[];
}

// ---------------------------------------------------------------------------
// Types -- Capability
// ---------------------------------------------------------------------------

export interface RoutingRule {
  orderType: OrderType;
  targetType: "INTERNAL" | "LIS" | "RIS" | "PHARMACY" | "EXTERNAL";
  targetIdentifier: string;
  priority: number;
}

export interface Capability {
  id: string;
  tenantId: string;
  facilityId: string;
  facilityName: string | null;
  adapterMode: AdapterMode;
  supportedOrderTypes: OrderType[];
  routingRules: RoutingRule[];
  defaultDepartmentId: string | null;
  autoAcceptOrders: boolean;
  criticalResultNotification: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UpsertCapabilityRequest {
  facilityId: string;
  adapterMode: AdapterMode;
  supportedOrderTypes: OrderType[];
  routingRules?: RoutingRule[];
  defaultDepartmentId?: string;
  autoAcceptOrders?: boolean;
  criticalResultNotification?: boolean;
}

// ---------------------------------------------------------------------------
// API Functions
// ---------------------------------------------------------------------------

const V1 = "/v1";

export const orosApi = {
  // -- Orders --
  listOrders: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<Order[]>(`${V1}/orders${qs}`);
  },

  getOrder: (id: string) =>
    apiClient.get<Order>(`${V1}/orders/${encodeURIComponent(id)}`),

  placeOrder: (data: PlaceOrderRequest) =>
    apiClient.post<Order>(`${V1}/orders`, data),

  cancelOrder: (id: string, data: CancelOrderRequest) =>
    apiClient.post<Order>(`${V1}/orders/${encodeURIComponent(id)}/cancel`, data),

  getPatientOrders: (cpid: string, params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<Order[]>(`${V1}/orders/patient/${encodeURIComponent(cpid)}${qs}`);
  },

  // -- Worklists --
  getWorklists: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<WorklistItem[]>(`${V1}/worklists${qs}`);
  },

  acceptWorklistItem: (id: string, data?: { notes?: string }) =>
    apiClient.post<WorklistItem>(`${V1}/worklists/${encodeURIComponent(id)}/accept`, data),

  rejectWorklistItem: (id: string, data: { reason: string }) =>
    apiClient.post<WorklistItem>(`${V1}/worklists/${encodeURIComponent(id)}/reject`, data),

  // -- Worksteps --
  getWorksteps: (orderId: string) =>
    apiClient.get<Workstep[]>(`${V1}/worksteps?orderId=${encodeURIComponent(orderId)}`),

  startWorkstep: (id: string, data?: { notes?: string }) =>
    apiClient.post<Workstep>(`${V1}/worksteps/${encodeURIComponent(id)}/start`, data),

  completeWorkstep: (id: string, data?: { notes?: string; outputData?: Record<string, unknown> }) =>
    apiClient.post<Workstep>(`${V1}/worksteps/${encodeURIComponent(id)}/complete`, data),

  // -- Results --
  postResult: (orderId: string, data: PostResultRequest) =>
    apiClient.post<Result>(`${V1}/orders/${encodeURIComponent(orderId)}/results`, data),

  getResults: (orderId: string) =>
    apiClient.get<Result[]>(`${V1}/orders/${encodeURIComponent(orderId)}/results`),

  markResultCritical: (orderId: string, resultId: string, data?: { reason?: string }) =>
    apiClient.post<Result>(
      `${V1}/orders/${encodeURIComponent(orderId)}/results/${encodeURIComponent(resultId)}/mark-critical`,
      data,
    ),

  // -- Acknowledgement --
  acknowledgeResults: (orderId: string, data: AcknowledgeRequest) =>
    apiClient.post<Ack>(`${V1}/orders/${encodeURIComponent(orderId)}/ack`, data),

  getAcknowledgements: (orderId: string) =>
    apiClient.get<Ack[]>(`${V1}/orders/${encodeURIComponent(orderId)}/ack`),

  // -- Routes --
  getRoutes: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<Route[]>(`${V1}/routes${qs}`);
  },

  retryRoute: (routeId: string) =>
    apiClient.post<Route>(`${V1}/routes/${encodeURIComponent(routeId)}/retry`),

  // -- Reconciliation --
  getPendingReconciliations: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<ReconcileItem[]>(`${V1}/reconcile/pending${qs}`);
  },

  matchReconciliation: (id: string, data: MatchReconcileRequest) =>
    apiClient.post<ReconcileItem>(`${V1}/reconcile/${encodeURIComponent(id)}/match`, data),

  resolveReconciliation: (id: string, data: ResolveReconcileRequest) =>
    apiClient.post<ReconcileItem>(`${V1}/reconcile/${encodeURIComponent(id)}/resolve`, data),

  // -- Catalog --
  getCatalog: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<CatalogItem[]>(`${V1}/catalog${qs}`);
  },

  importCatalog: (items: CatalogImportItem[]) =>
    apiClient.post<CatalogImportResult>(`${V1}/catalog/import`, { items }),

  lookupCatalogItem: (code: string, system?: string) => {
    const params = new URLSearchParams({ code });
    if (system) params.set("system", system);
    return apiClient.get<CatalogItem>(`${V1}/catalog/lookup?${params.toString()}`);
  },

  // -- Capabilities --
  listCapabilities: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<Capability[]>(`${V1}/internal/capabilities${qs}`);
  },

  upsertCapability: (data: UpsertCapabilityRequest) =>
    apiClient.put<Capability>(`${V1}/internal/capabilities`, data),
};
