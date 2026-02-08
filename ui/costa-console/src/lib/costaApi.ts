/**
 * COSTA Console — Domain API Service Layer
 *
 * All calls route through apiClient which injects trust headers
 * (X-Tenant-Id, X-Actor-Id, X-Correlation-Id, etc.) before they
 * reach Envoy ext_authz -> TSHEPO -> COSTA.
 */

import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// Types — Enumerations
// ---------------------------------------------------------------------------

export type BillStatus =
  | "DRAFT"
  | "ACCUMULATING"
  | "PENDING_APPROVAL"
  | "APPROVED"
  | "FINALIZED"
  | "INVOICED"
  | "PAID"
  | "PARTIALLY_PAID"
  | "REFUNDED"
  | "VOIDED";

export type BillType =
  | "ENCOUNTER"
  | "INPATIENT"
  | "OUTREACH"
  | "MSIKA_ORDER"
  | "INSURANCE_CLAIM";

export type BillLineKind =
  | "SERVICE"
  | "PRODUCT"
  | "FEE"
  | "BEDDAY"
  | "PROCEDURE"
  | "THEATRE"
  | "IMPLANT"
  | "NURSING"
  | "OVERHEAD"
  | "TRANSPORT"
  | "PER_DIEM"
  | "COLD_CHAIN"
  | "DELIVERY"
  | "BUNDLE";

export type CostMethodType = "MICRO" | "ABC" | "TARIFF" | "STANDARD" | "STOCK_AVERAGE";

export type RulesetStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export type PaymentType = "CASH" | "MOBILE_MONEY" | "CARD" | "BANK_TRANSFER" | "INSURANCE_CLAIM" | "VOUCHER";

export type ExemptionCategory =
  | "CHILD"
  | "ELDERLY"
  | "MATERNITY"
  | "CHRONIC"
  | "DISABLED"
  | "VETERAN"
  | "HEALTHCARE_WORKER"
  | "INDIGENT"
  | "EMERGENCY"
  | "GOVERNMENT"
  | "OTHER";

// ---------------------------------------------------------------------------
// Types — Entities
// ---------------------------------------------------------------------------

export interface BillHeader {
  id: string;
  tenantId: string;
  facilityId: string;
  encounterId: string | null;
  msikaOrderId: string | null;
  status: BillStatus;
  billType: BillType;
  currency: string;
  totalCharge: number;
  totalDiscount: number;
  totalNet: number;
  createdAt: string;
  updatedAt: string;
}

export interface BillLine {
  id: number;
  billId: string;
  lineSeq: number;
  msikaCode: string;
  description: string;
  kind: BillLineKind;
  qty: number;
  unitCost: number;
  chargeAmount: number;
  discountAmount: number;
  netAmount: number;
  costMethod: CostMethodType;
  sourceEvent: string | null;
  sourceRef: string | null;
  voided: boolean;
}

export interface BillParty {
  id: number;
  billId: string;
  partyType: string;
  partyRef: string;
  allocatedAmount: number;
}

export interface Tariff {
  id: number;
  tenantId: string;
  tariffCode: string;
  msikaCode: string;
  description: string;
  price: number;
  currency: string;
  facilityCategory: string | null;
  patientCategory: string | null;
  effectiveFrom: string;
  effectiveTo: string | null;
}

export interface Ruleset {
  id: number;
  tenantId: string;
  name: string;
  version: number;
  status: RulesetStatus;
  rules: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  createdAt: string;
}

export interface Invoice {
  id: number;
  billId: string;
  invoiceNumber: string;
  totalDue: number;
  currency: string;
  issuedAt: string;
}

export interface Payment {
  id: number;
  billId: string;
  paymentType: PaymentType;
  amount: number;
  status: string;
  externalRef: string | null;
  createdAt: string;
}

export interface Refund {
  id: number;
  billId: string;
  amount: number;
  reason: string;
  reasonCode: string;
  status: string;
  createdAt: string;
}

export interface AuditTrail {
  bill: BillHeader;
  lines: BillLine[];
  lines_count: number;
  voided_lines: number;
  parties: BillParty[];
  approvals: Array<{ id: number; step: string; status: string; actorId: string; note: string; createdAt: string }>;
  payments: Payment[];
  refunds: Refund[];
  claims: unknown[];
  invoice: Invoice | null;
}

// ---------------------------------------------------------------------------
// Types — Requests
// ---------------------------------------------------------------------------

export interface EstimateLineInput {
  msikaCode: string;
  description?: string;
  kind: BillLineKind;
  qty: number;
  costMethod?: CostMethodType;
  extras?: Record<string, string>;
}

export interface EstimateRequest {
  items: EstimateLineInput[];
  exemptionCategory?: ExemptionCategory;
  insurancePlanId?: number;
  context?: Record<string, unknown>;
}

export interface BillDraftRequest {
  encounterId?: string;
  msikaOrderId?: string;
  billType?: BillType;
}

export interface PostLineRequest {
  msikaCode: string;
  description?: string;
  kind: BillLineKind;
  qty: number;
  costMethod?: CostMethodType;
  sourceEvent?: string;
  sourceRef?: string;
  context?: Record<string, string>;
}

export interface PaymentIntentRequest {
  paymentType: PaymentType;
  amount: number;
}

export interface RefundRequest {
  amount: number;
  reason: string;
  reasonCode: string;
  refundType: string;
}

// ---------------------------------------------------------------------------
// Types — Paged response
// ---------------------------------------------------------------------------

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

const COSTA = "/costa/v1";

export const costaApi = {
  // -- Estimates --
  createEstimate: (data: EstimateRequest) =>
    apiClient.post<unknown>(`${COSTA}/estimate`, data),

  // -- Bills --
  createDraft: (data: BillDraftRequest) =>
    apiClient.post<BillHeader>(`${COSTA}/bills/draft`, data),

  getBill: (id: string) =>
    apiClient.get<{ bill: BillHeader; lines: BillLine[]; parties: BillParty[] }>(
      `${COSTA}/bills/${encodeURIComponent(id)}`
    ),

  postLine: (billId: string, data: PostLineRequest) =>
    apiClient.post<BillLine>(`${COSTA}/bills/${encodeURIComponent(billId)}/lines`, data),

  recompute: (billId: string) =>
    apiClient.post<BillHeader>(`${COSTA}/bills/${encodeURIComponent(billId)}/recompute`),

  submitApproval: (billId: string) =>
    apiClient.post<BillHeader>(`${COSTA}/bills/${encodeURIComponent(billId)}/submit-approval`),

  approve: (billId: string, note?: string) =>
    apiClient.post<BillHeader>(`${COSTA}/bills/${encodeURIComponent(billId)}/approve`, { note }),

  finalize: (billId: string) =>
    apiClient.post<BillHeader>(`${COSTA}/bills/${encodeURIComponent(billId)}/finalize`),

  issueInvoice: (billId: string) =>
    apiClient.post<Invoice>(`${COSTA}/bills/${encodeURIComponent(billId)}/issue-invoice`),

  createPaymentIntent: (billId: string, data: PaymentIntentRequest) =>
    apiClient.post<Payment>(
      `${COSTA}/bills/${encodeURIComponent(billId)}/create-payment-intent`,
      data
    ),

  refund: (billId: string, data: RefundRequest) =>
    apiClient.post<Refund>(`${COSTA}/bills/${encodeURIComponent(billId)}/refund`, data),

  // -- Tariffs --
  listTariffs: (params?: { page?: number; size?: number }) => {
    const qs = params
      ? `?${new URLSearchParams(
          Object.entries(params).map(([k, v]) => [k, String(v)])
        ).toString()}`
      : "";
    return apiClient.get<PagedResponse<Tariff>>(`${COSTA}/tariffs${qs}`);
  },

  // -- Rulesets --
  listRulesets: (params?: { page?: number; size?: number }) => {
    const qs = params
      ? `?${new URLSearchParams(
          Object.entries(params).map(([k, v]) => [k, String(v)])
        ).toString()}`
      : "";
    return apiClient.get<PagedResponse<Ruleset>>(`${COSTA}/rulesets${qs}`);
  },

  publishRuleset: (id: number) =>
    apiClient.post<Ruleset>(`${COSTA}/rulesets/${id}/publish`),

  // -- Audit --
  auditBill: (billId: string) =>
    apiClient.get<AuditTrail>(`${COSTA}/audit/bill/${encodeURIComponent(billId)}`),
};
