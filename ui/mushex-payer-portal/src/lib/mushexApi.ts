/**
 * MUSHEX Payer Portal — Domain API Service Layer
 *
 * All calls route through apiClient which injects trust headers
 * (X-Tenant-Id, X-Actor-Id, X-Correlation-Id, etc.) before they
 * reach Envoy ext_authz -> TSHEPO -> MUSHEX.
 */

import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// Types — Enumerations
// ---------------------------------------------------------------------------

export type IntentStatus =
  | "CREATED"
  | "PENDING"
  | "AUTHORIZED"
  | "PAID"
  | "FAILED"
  | "CANCELLED"
  | "REFUND_PENDING"
  | "REFUNDED";

export type SourceType =
  | "COSTA_BILL"
  | "MSIKA_ORDER"
  | "INSURANCE_CLAIM"
  | "MANUAL";

export type RemittanceStatus =
  | "ACTIVE"
  | "CLAIMED"
  | "EXPIRED"
  | "REVOKED";

// ---------------------------------------------------------------------------
// Types — Entities
// ---------------------------------------------------------------------------

export interface PaymentIntent {
  intentId: string;
  tenantId: string;
  facilityId: string;
  sourceType: SourceType;
  sourceId: string;
  currency: string;
  amountTotal: number;
  amountPaid: number;
  status: IntentStatus;
  idempotencyKey: string | null;
  expiresAt: string | null;
  metadata: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Receipt {
  receiptId: string;
  intentId: string;
  landelaDocId: string | null;
  issuedAt: string;
  summary: string | null;
}

export interface RemittanceToken {
  id: string;
  intentId: string;
  token: string;
  otp: string | null;
  status: RemittanceStatus;
  claimedAt: string | null;
  expiresAt: string;
  createdAt: string;
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

const MUSHEX = "/mushex/v1";

export const mushexApi = {
  // -- Payment Intents --
  getIntent: (id: string) =>
    apiClient.get<PaymentIntent>(`${MUSHEX}/payment-intents/${encodeURIComponent(id)}`),

  cancelIntent: (id: string) =>
    apiClient.post<PaymentIntent>(`${MUSHEX}/payment-intents/${encodeURIComponent(id)}/cancel`),

  // -- Receipts --
  getReceipts: (intentId: string) =>
    apiClient.get<Receipt[]>(`${MUSHEX}/payment-intents/${encodeURIComponent(intentId)}/receipts`),

  // -- Remittance --
  issueRemittanceSlip: (intentId: string) =>
    apiClient.post<RemittanceToken>(
      `${MUSHEX}/payment-intents/${encodeURIComponent(intentId)}/issue-remittance-slip`
    ),

  claimRemittance: (token: string, otp: string) =>
    apiClient.post<RemittanceToken>(`${MUSHEX}/remittance/claim`, { token, otp }),
};
