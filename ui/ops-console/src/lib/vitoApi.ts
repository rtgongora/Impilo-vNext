/**
 * VITO Registry API client for Ops Console.
 *
 * All calls route through the Envoy gateway (apiClient injects trust headers).
 * These functions provide typed wrappers for VITO's REST API.
 */

const VITO_BASE = "/api/v1";

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

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  error?: { code: string; message: string; status: number };
  correlationId: string;
  timestamp: string;
}

// --- API Functions ---

async function fetchApi<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const envelope: ApiEnvelope<T> = await res.json();
  if (!envelope.success) {
    throw new Error(envelope.error?.message ?? "API error");
  }
  return envelope.data;
}

export const vitoApi = {
  // Clients
  listClients: (page = 0, size = 50, status?: string) =>
    fetchApi<PagedResponse<Client>>(
      `${VITO_BASE}/clients?page=${page}&size=${size}${status ? `&status=${status}` : ""}`
    ),

  getClient: (healthId: string) =>
    fetchApi<Client>(`${VITO_BASE}/clients/${healthId}`),

  // Match queue
  getPendingMatches: (page = 0, size = 50) =>
    fetchApi<PagedResponse<MatchResult>>(`${VITO_BASE}/match/pending?page=${page}&size=${size}`),

  resolveMatch: (matchId: number, disposition: string) =>
    fetchApi<MatchResult>(`${VITO_BASE}/match/${matchId}/resolve`, {
      method: "POST",
      body: JSON.stringify({ disposition }),
    }),

  // Cards
  listCardsByStatus: (status: string, page = 0, size = 50) =>
    fetchApi<PagedResponse<SmartCard>>(
      `${VITO_BASE}/cards/by-status/${status}?page=${page}&size=${size}`
    ),

  printCard: (cardId: number) =>
    fetchApi<SmartCard>(`${VITO_BASE}/cards/${cardId}/print`, { method: "POST" }),

  activateCard: (cardId: number) =>
    fetchApi<SmartCard>(`${VITO_BASE}/cards/${cardId}/activate`, { method: "POST" }),

  inactivateCard: (cardId: number) =>
    fetchApi<SmartCard>(`${VITO_BASE}/cards/${cardId}/inactivate`, { method: "POST" }),

  // Registry config
  getRegistryMode: () =>
    fetchApi<RegistryMode>(`${VITO_BASE}/registry/mode`),
};
