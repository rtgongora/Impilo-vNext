/**
 * MUSHEX Finance Console — Domain API Service Layer
 *
 * All calls route through apiClient which injects trust headers
 * (X-Tenant-Id, X-Actor-Id, X-Correlation-Id, etc.) before they
 * reach Envoy ext_authz -> TSHEPO -> MUSHEX.
 */

import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// Types — Enumerations
// ---------------------------------------------------------------------------

export type SettlementStatus = "DRAFT" | "COMPUTING" | "COMPUTED" | "RELEASED" | "FAILED";

export type RefundStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";

export type ReconStatus = "UNMATCHED" | "MATCHED" | "DISPUTED";

export type IntentStatus =
  | "CREATED"
  | "PENDING"
  | "AUTHORIZED"
  | "PAID"
  | "FAILED"
  | "CANCELLED"
  | "REFUND_PENDING"
  | "REFUNDED";

// ---------------------------------------------------------------------------
// Types — Entities
// ---------------------------------------------------------------------------

export interface Settlement {
  settlementId: string;
  tenantId: string;
  periodStart: string;
  periodEnd: string;
  status: SettlementStatus;
  totals: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Refund {
  refundId: string;
  intentId: string;
  amount: number;
  reason: string | null;
  status: RefundStatus;
  adapterRef: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface LedgerEntry {
  entryId: string;
  tenantId: string;
  intentId: string | null;
  referenceType: string;
  referenceId: string;
  debitAccount: string;
  creditAccount: string;
  amount: number;
  currency: string;
  description: string | null;
  createdAt: string;
}

export interface ReconEntry {
  id: string;
  tenantId: string;
  statementRef: string;
  statementDate: string;
  amount: number;
  currency: string;
  counterparty: string | null;
  status: ReconStatus;
  matchedIntentId: string | null;
  importedAt: string;
  matchedAt: string | null;
}

export interface PaymentIntent {
  intentId: string;
  tenantId: string;
  facilityId: string;
  sourceType: string;
  sourceId: string;
  currency: string;
  amountTotal: number;
  amountPaid: number;
  status: IntentStatus;
  createdAt: string;
  updatedAt: string;
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
// Types — Requests
// ---------------------------------------------------------------------------

export interface ReconImportLine {
  statementRef: string;
  statementDate: string;
  amount: number;
  currency: string;
  counterparty?: string;
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

const MUSHEX = "/mushex/v1";

export const mushexApi = {
  // -- Settlements --
  runSettlement: (periodStart: string, periodEnd: string) =>
    apiClient.post<Settlement>(`${MUSHEX}/settlements/run`, { periodStart, periodEnd }),

  getSettlement: (id: string) =>
    apiClient.get<Settlement>(`${MUSHEX}/settlements/${encodeURIComponent(id)}`),

  releasePayouts: (id: string) =>
    apiClient.post<Settlement>(`${MUSHEX}/settlements/${encodeURIComponent(id)}/release-payouts`),

  // -- Reconciliation --
  importStatement: (lines: ReconImportLine[]) =>
    apiClient.post<{ imported: number }>(`${MUSHEX}/recon/import-statement`, lines),

  getUnmatched: (params?: { page?: number; size?: number }) => {
    const qs = params
      ? `?${new URLSearchParams(
          Object.entries(params).map(([k, v]) => [k, String(v)])
        ).toString()}`
      : "";
    return apiClient.get<PagedResponse<ReconEntry>>(`${MUSHEX}/recon/unmatched${qs}`);
  },

  matchEntry: (reconId: string, intentId: string) =>
    apiClient.post<ReconEntry>(`${MUSHEX}/recon/match?reconId=${encodeURIComponent(reconId)}`, {
      intentId,
    }),

  // -- Refunds --
  getIntent: (id: string) =>
    apiClient.get<PaymentIntent>(`${MUSHEX}/payment-intents/${encodeURIComponent(id)}`),

  createRefund: (intentId: string, amount: number, reason: string) =>
    apiClient.post<Refund>(
      `${MUSHEX}/payment-intents/${encodeURIComponent(intentId)}/refund`,
      { amount, reason }
    ),
};
