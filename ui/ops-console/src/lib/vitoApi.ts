/**
 * VITO Registry API client for Ops Console.
 *
 * All calls route through apiClient which injects trust headers
 * (x-actor-id, x-tenant-id, x-correlation-id, etc.) before they
 * reach Envoy ext_authz → TSHEPO.
 */

import { apiClient } from "./apiClient";
import type { PagedResponse } from "shared-ui";

// --- Types ---

export interface Client {
  id: number;
  healthId: string;
  tenantId: string;
  givenName: string;
  familyName: string;
  dateOfBirth: string;
  sex: string;
  status: "PROVISIONAL" | "VERIFIED" | "ACTIVE" | "INACTIVE" | "DECEASED" | "MERGED";
  createdAt: string;
  updatedAt: string;
}

export interface MatchResult {
  id: number;
  sourceHealthId: string;
  candidateHealthId: string;
  matchScore: number;
  matchAlgorithm: string;
  matchFields: Record<string, number>;
  disposition: "PENDING" | "AUTO_LINKED" | "MANUAL_LINKED" | "REJECTED" | "DEFERRED";
  resolvedBy?: string;
  resolvedAt?: string;
  createdAt: string;
}

export interface SmartCard {
  id: number;
  cardNumber: string;
  healthId: string;
  didUri: string;
  status: "REQUESTED" | "PRINTED" | "ACTIVE" | "INACTIVE" | "REVOKED";
  requestedBy: string;
  requestedAt: string;
  printedAt?: string;
  activatedAt?: string;
  revokedAt?: string;
  revocationReason?: string;
  expiresAt: string;
}

export interface RegistryMode {
  mode: "OPENCR" | "STANDALONE";
  opencrEnabled: boolean;
}

export interface IssuanceRequest {
  id: number;
  tenantId: string;
  healthId: string;
  type: "NEW" | "REPLACEMENT" | "RECOVERY";
  channel: "PORTAL" | "ASSISTED";
  state: "SUBMITTED" | "PROOFING" | "APPROVED" | "ISSUED" | "DELIVERED" | "REJECTED" | "EXPIRED";
  assignedTo?: string;
  rejectionReason?: string;
  impiloIdIssued?: string;
  submittedAt: string;
  approvedAt?: string;
  issuedAt?: string;
}

export interface DedupCase {
  id: number;
  tenantId: string;
  candidateA: string;
  candidateB: string;
  matchScore: number;
  status: string;
  risk?: string;
  assignedTo?: string;
  createdAt: string;
}

export interface MaskedClient {
  healthId: string;
  impiloId?: string;
  givenName: string;
  familyName: string;
  yearOfBirth?: number;
  sex?: string;
  status: string;
}

export interface AuditEvent {
  id: number;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  payload: string;
  createdAt: string;
  publishedAt?: string;
}

const VITO_BASE = "/api/v1";

// --- API Functions ---

export const vitoApi = {
  // Clients
  listClients: (page = 0, size = 50, status?: string) =>
    apiClient.get<PagedResponse<Client>>(
      `${VITO_BASE}/clients?page=${page}&size=${size}${status ? `&status=${status}` : ""}`
    ),

  getClient: (healthId: string) =>
    apiClient.get<Client>(`${VITO_BASE}/clients/${healthId}`),

  verifyClient: (healthId: string) =>
    apiClient.post<Client>(`${VITO_BASE}/clients/${healthId}/verify`),

  // Match queue
  getPendingMatches: (page = 0, size = 50) =>
    apiClient.get<PagedResponse<MatchResult>>(
      `${VITO_BASE}/match/pending?page=${page}&size=${size}`
    ),

  resolveMatch: (matchId: number, disposition: string) =>
    apiClient.post<MatchResult>(`${VITO_BASE}/match/${matchId}/resolve`, {
      disposition,
    }),

  // Cards
  listCardsByStatus: (status: string, page = 0, size = 50) =>
    apiClient.get<PagedResponse<SmartCard>>(
      `${VITO_BASE}/cards/by-status/${status}?page=${page}&size=${size}`
    ),

  printCard: (cardId: number) =>
    apiClient.post<SmartCard>(`${VITO_BASE}/cards/${cardId}/print`),

  activateCard: (cardId: number) =>
    apiClient.post<SmartCard>(`${VITO_BASE}/cards/${cardId}/activate`),

  inactivateCard: (cardId: number) =>
    apiClient.post<SmartCard>(`${VITO_BASE}/cards/${cardId}/inactivate`),

  // Registry config
  getRegistryMode: () =>
    apiClient.get<RegistryMode>(`${VITO_BASE}/registry/mode`),

  // Internal search (masked)
  searchClients: (query: string, page = 0, size = 20) =>
    apiClient.post<PagedResponse<MaskedClient>>(`${VITO_BASE}/internal/clients/search`, { query, page, size }),

  // Issuance queue
  getIssuanceQueue: (state: string, page = 0, size = 50) =>
    apiClient.get<PagedResponse<IssuanceRequest>>(`${VITO_BASE}/internal/issuance/queue?state=${state}&page=${page}&size=${size}`),

  startProofing: (requestId: number) =>
    apiClient.post<IssuanceRequest>(`${VITO_BASE}/internal/issuance/${requestId}/proofing`),

  approveIssuance: (requestId: number) =>
    apiClient.post<IssuanceRequest>(`${VITO_BASE}/internal/issuance/${requestId}/approve`),

  issueId: (requestId: number) =>
    apiClient.post<IssuanceRequest>(`${VITO_BASE}/internal/issuance/${requestId}/issue`),

  rejectIssuance: (requestId: number, reason: string) =>
    apiClient.post<IssuanceRequest>(`${VITO_BASE}/internal/issuance/${requestId}/reject`, { reason }),

  // Dedup
  getDedupCases: (status: string, page = 0, size = 50) =>
    apiClient.get<PagedResponse<DedupCase>>(`${VITO_BASE}/internal/dedup/cases?status=${status}&page=${page}&size=${size}`),

  scoreDedupPair: (healthIdA: string, healthIdB: string) =>
    apiClient.post<{ score: number; fieldScores: string; disposition: string }>(
      `${VITO_BASE}/internal/dedup/score`, { healthIdA, healthIdB }),

  executeMerge: (caseId: number, survivorCrid: string, mergedCrid: string, strategy: string) =>
    apiClient.post(`${VITO_BASE}/internal/dedup/cases/${caseId}/merge`, {
      survivorCrid, mergedCrid, strategy, fieldDecisions: "{}" }),

  // Audit (event_outbox)
  getAuditEvents: (page = 0, size = 50) =>
    apiClient.get<PagedResponse<AuditEvent>>(`${VITO_BASE}/internal/audit?page=${page}&size=${size}`),
};
