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
};
