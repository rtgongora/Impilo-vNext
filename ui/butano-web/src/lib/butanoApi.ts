/**
 * BUTANO SHR API client for butano-web.
 *
 * All calls route through apiClient which injects trust headers
 * (x-actor-id, x-tenant-id, x-correlation-id, etc.) before they
 * reach Envoy ext_authz -> TSHEPO -> BUTANO.
 */

import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface TimelineItem {
  resourceType: string;
  resourceId: string;
  date: string;
  summary: string;
  encounterRef?: string;
}

export interface TimelineResponse {
  items: TimelineItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface ReconcileSubjectRequest {
  oldCpid: string;
  newCpid: string;
  reason: string;
}

export interface ReconciliationStatus {
  id: string;
  tenantId: string;
  oldCpid: string;
  newCpid: string;
  status: "PENDING" | "IN_PROGRESS" | "COMPLETED" | "FAILED";
  resourcesUpdated: number;
  requestedAt: string;
  completedAt?: string;
  errorMessage?: string;
}

export interface TenantInfo {
  tenantId: string;
  tenantName: string;
  active: boolean;
}

export interface ResourceStats {
  counts: Record<string, number>;
}

/**
 * Represents a FHIR R4 Bundle — simplified for display purposes.
 * The actual payload is the full FHIR JSON; we only type the top-level
 * fields we need for the UI.
 */
export interface FhirBundle {
  resourceType: "Bundle";
  type: string;
  total?: number;
  entry?: FhirBundleEntry[];
}

export interface FhirBundleEntry {
  fullUrl?: string;
  resource: {
    resourceType: string;
    id?: string;
    [key: string]: unknown;
  };
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

const V1 = "/v1";

export const butanoApi = {
  // IPS Bundle (returns raw FHIR, not ApiEnvelope)
  getIpsSummary: (cpid: string) =>
    apiClient.fhir<FhirBundle>(`${V1}/summary/ips/${encodeURIComponent(cpid)}`),

  // Visit Summary (returns raw FHIR, not ApiEnvelope)
  getVisitSummary: (encounterId: string) =>
    apiClient.fhir<FhirBundle>(`${V1}/summary/visit/${encodeURIComponent(encounterId)}`),

  // Timeline (returns ApiEnvelope<TimelineResponse>)
  getTimeline: (
    cpid: string,
    params?: { since?: string; type?: string; page?: number; size?: number },
  ) => {
    const query = new URLSearchParams();
    if (params?.since) query.set("since", params.since);
    if (params?.type) query.set("type", params.type);
    if (params?.page !== undefined) query.set("page", String(params.page));
    if (params?.size !== undefined) query.set("size", String(params.size));
    const qs = query.toString();
    return apiClient.get<TimelineResponse>(
      `${V1}/timeline/${encodeURIComponent(cpid)}${qs ? `?${qs}` : ""}`,
    );
  },

  // Reconciliation
  triggerReconciliation: (req: ReconcileSubjectRequest) =>
    apiClient.post<ReconciliationStatus>(`${V1}/internal/reconcile-subject`, req),

  getReconciliationStatus: (id: string) =>
    apiClient.get<ReconciliationStatus>(`${V1}/internal/reconciliation/${encodeURIComponent(id)}`),

  listReconciliationJobs: (params?: { page?: number; size?: number }) => {
    const qs = new URLSearchParams();
    qs.set("page", String(params?.page ?? 0));
    qs.set("size", String(params?.size ?? 20));
    return apiClient.get<{ content: ReconciliationStatus[]; totalElements: number; totalPages: number }>(
      `${V1}/internal/reconciliation?${qs.toString()}`
    );
  },

  // Ops
  getTenantHealth: () =>
    apiClient.get<TenantInfo[]>(`${V1}/internal/health/tenants`),

  getResourceStats: () =>
    apiClient.get<ResourceStats>(`${V1}/internal/stats`),
};
