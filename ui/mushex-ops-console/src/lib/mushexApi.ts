/**
 * MUSHEX Ops Console — Domain API Service Layer
 *
 * All calls route through apiClient which injects trust headers
 * (X-Tenant-Id, X-Actor-Id, X-Correlation-Id, etc.) before they
 * reach Envoy ext_authz -> TSHEPO -> MUSHEX.
 */

import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// Types — Enumerations
// ---------------------------------------------------------------------------

export type AdapterType = "MOBILE_MONEY" | "BANK_TRANSFER" | "CARD_GATEWAY" | "SANDBOX";

export type FraudKind =
  | "DUPLICATE_PAYMENT"
  | "REPEATED_REFUND"
  | "UNUSUAL_CLAIM_AMOUNT"
  | "ABNORMAL_FREQUENCY"
  | "VELOCITY_BREACH";

export type FraudSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export type ReviewStatus = "PENDING" | "IN_REVIEW" | "APPROVED" | "REJECTED";

export type ClaimStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "RECEIVED"
  | "ADJUDICATED"
  | "PAID"
  | "PARTIAL"
  | "REJECTED"
  | "RESUBMIT_PENDING";

// ---------------------------------------------------------------------------
// Types — Entities
// ---------------------------------------------------------------------------

export interface AdapterConfig {
  id: string;
  tenantId: string;
  adapterType: AdapterType;
  name: string;
  enabled: boolean;
  config: string | null;
  createdAt: string;
}

export interface FraudFlag {
  id: string;
  tenantId: string;
  kind: FraudKind;
  severity: FraudSeverity;
  entityType: string;
  entityId: string;
  evidence: string | null;
  status: string;
  createdAt: string;
}

export interface OpsReview {
  id: string;
  tenantId: string;
  queueType: string;
  entityType: string;
  entityId: string;
  status: ReviewStatus;
  assignedTo: string | null;
  notes: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export interface Claim {
  claimId: string;
  tenantId: string;
  facilityId: string;
  billId: string;
  insurerId: string;
  status: ClaimStatus;
  totals: string | null;
  submittedAt: string | null;
  adjudicatedAt: string | null;
  externalRef: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ClaimDetail {
  claim: Claim;
  events: Array<{
    id: string;
    claimId: string;
    eventType: string;
    payload: string | null;
    createdAt: string;
  }>;
  attachments: Array<{
    id: string;
    claimId: string;
    landelaDocId: string;
    docType: string;
    createdAt: string;
  }>;
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
  // -- Adapters --
  listAdapters: () =>
    apiClient.get<AdapterConfig[]>(`${MUSHEX}/adapters`),

  // -- Fraud --
  listFraudFlags: (params?: { page?: number; size?: number; severity?: FraudSeverity }) => {
    const entries: [string, string][] = [];
    if (params?.page !== undefined) entries.push(["page", String(params.page)]);
    if (params?.size !== undefined) entries.push(["size", String(params.size)]);
    if (params?.severity) entries.push(["severity", params.severity]);
    const qs = entries.length > 0 ? `?${new URLSearchParams(entries).toString()}` : "";
    return apiClient.get<PagedResponse<FraudFlag>>(`${MUSHEX}/fraud-flags${qs}`);
  },

  // -- Reviews --
  listReviews: (params?: { page?: number; size?: number; status?: ReviewStatus }) => {
    const entries: [string, string][] = [];
    if (params?.page !== undefined) entries.push(["page", String(params.page)]);
    if (params?.size !== undefined) entries.push(["size", String(params.size)]);
    if (params?.status) entries.push(["status", params.status]);
    const qs = entries.length > 0 ? `?${new URLSearchParams(entries).toString()}` : "";
    return apiClient.get<PagedResponse<OpsReview>>(`${MUSHEX}/ops-reviews${qs}`);
  },

  approveReview: (id: string, notes?: string) =>
    apiClient.post<OpsReview>(`${MUSHEX}/ops-reviews/${encodeURIComponent(id)}/approve`, { notes }),

  rejectReview: (id: string, notes?: string) =>
    apiClient.post<OpsReview>(`${MUSHEX}/ops-reviews/${encodeURIComponent(id)}/reject`, { notes }),

  // -- Claims --
  getClaim: (id: string) =>
    apiClient.get<ClaimDetail>(`${MUSHEX}/claims/${encodeURIComponent(id)}`),

  submitClaim: (id: string) =>
    apiClient.post<Claim>(`${MUSHEX}/claims/${encodeURIComponent(id)}/submit`),

  disputeClaim: (id: string, reason: string) =>
    apiClient.post<Claim>(`${MUSHEX}/claims/${encodeURIComponent(id)}/dispute`, { reason }),
};
