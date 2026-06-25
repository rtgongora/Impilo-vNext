/**
 * Experience UI — OROS Diagnostics Journey query hooks.
 *
 * Backed by the experience-bff diagnostics proxy (`/internal/v1/diagnostics/*`), which governs and
 * forwards to OROS. Every hook calls a real BFF endpoint backed by OROS.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/** Mirrors the OROS OrderSummaryDto surfaced through the BFF. */
export interface DiagnosticOrder {
  orderId: string;
  orderType: string;
  status: string;
  priority: string | null;
  patientCpid: string;
  facilityId: string | null;
  placedAt: string | null;
  placedBy: string | null;
  encounterRef: string | null;
  clinicalNotes: string | null;
  updatedAt: string | null;
  requestSource: string | null;
  accessionNumber: string | null;
  referringProviderId: string | null;
  referringProviderName: string | null;
  scheduledAt: string | null;
  imagingState: string | null;
  studyUid: string | null;
  studyViewerUrl: string | null;
}

export interface DiagnosticOrderFilters {
  client?: string;
  requester?: string;
  status?: string;
  type?: string;
}

type OrdersResponse = ApiResponse<DiagnosticOrder[]>;
type ReconcileSummaryResponse = ApiResponse<Record<string, number>>;

function buildQuery(filters: DiagnosticOrderFilters): string {
  const params = new URLSearchParams();
  if (filters.client) params.set("client", filters.client);
  if (filters.requester) params.set("requester", filters.requester);
  if (filters.status) params.set("status", filters.status);
  if (filters.type) params.set("type", filters.type);
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

/** Diagnostic order tracking list. */
export function useDiagnosticsOrders(filters: DiagnosticOrderFilters = {}) {
  return useQuery<OrdersResponse>({
    queryKey: [
      "diagnostics-orders",
      filters.client ?? null,
      filters.requester ?? null,
      filters.status ?? null,
      filters.type ?? null,
    ],
    queryFn: () =>
      apiClient.get<OrdersResponse>(`/internal/v1/diagnostics/orders${buildQuery(filters)}`),
    staleTime: 15_000,
  });
}

/** Requester results inbox — orders with results ready to review. */
export function useResultsInbox(requester?: string) {
  return useQuery<OrdersResponse>({
    queryKey: ["diagnostics-results-inbox", requester ?? null],
    queryFn: () => {
      const qs = requester ? `?requester=${encodeURIComponent(requester)}` : "";
      return apiClient.get<OrdersResponse>(`/internal/v1/diagnostics/results-inbox${qs}`);
    },
    staleTime: 15_000,
  });
}

export interface CreateDiagnosticOrderItem {
  code: string;
  displayName?: string;
  quantity?: number;
  modality?: string;
  laterality?: string;
  contrast?: string;
  procedureCode?: string;
  bodySite?: string;
}

export interface CreateDiagnosticOrderInput {
  patientCpid: string;
  orderType: string;
  priority?: string;
  clinicalNotes?: string;
  referringProviderId?: string;
  referringProviderName?: string;
  items: CreateDiagnosticOrderItem[];
}

function toDraftBody(input: CreateDiagnosticOrderInput): Record<string, unknown> {
  return {
    orderType: input.orderType,
    priority: input.priority ?? "ROUTINE",
    patientCpid: input.patientCpid,
    clinicalNotes: input.clinicalNotes,
    requestSource: "INTERNAL",
    referringProviderId: input.referringProviderId,
    referringProviderName: input.referringProviderName,
    items: input.items.map((it) => ({
      code: it.code,
      displayName: it.displayName ?? it.code,
      quantity: it.quantity ?? 1,
      modality: it.modality,
      laterality: it.laterality,
      contrast: it.contrast,
      procedureCode: it.procedureCode,
      bodySite: it.bodySite,
    })),
  };
}

/**
 * Create a diagnostic order: create a draft then submit it (reserving an accession and
 * initializing the imaging workflow on the OROS side). Returns the submitted order.
 */
export function useCreateDiagnosticOrder() {
  const queryClient = useQueryClient();
  return useMutation<DiagnosticOrder, unknown, CreateDiagnosticOrderInput>({
    mutationFn: async (input) => {
      const draft = await apiClient.post<ApiResponse<DiagnosticOrder>>(
        "/internal/v1/diagnostics/orders/draft",
        toDraftBody(input),
      );
      const orderId = draft.data.orderId;
      const submitted = await apiClient.post<ApiResponse<DiagnosticOrder>>(
        `/internal/v1/diagnostics/orders/${encodeURIComponent(orderId)}/submit`,
      );
      return submitted.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["diagnostics-orders"] });
    },
  });
}

export interface RouteDiagnosticOrderInput {
  orderId: string;
  destinationType: string;
  destinationName?: string;
  destinationFacilityId?: string;
  destinationDepartmentId?: string;
  destinationServicePointId?: string;
  destinationProviderId?: string;
  expectedReturnMethod?: string;
}

/** Assign a routing destination to an order (referral). */
export function useRouteDiagnosticOrder() {
  const queryClient = useQueryClient();
  return useMutation<unknown, unknown, RouteDiagnosticOrderInput>({
    mutationFn: ({ orderId, ...body }) =>
      apiClient.post(`/internal/v1/diagnostics/orders/${encodeURIComponent(orderId)}/route`, {
        destinationType: body.destinationType,
        destinationName: body.destinationName,
        destinationFacilityId: body.destinationFacilityId || undefined,
        destinationDepartmentId: body.destinationDepartmentId || undefined,
        destinationServicePointId: body.destinationServicePointId || undefined,
        destinationProviderId: body.destinationProviderId || undefined,
        expectedReturnMethod: body.expectedReturnMethod || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["diagnostics-orders"] });
    },
  });
}

/** Operational reconciliation summary (stuck-order bucket counts + unacked critical). */
export function useDiagnosticsReconcileSummary() {
  return useQuery<ReconcileSummaryResponse>({
    queryKey: ["diagnostics-reconcile-summary"],
    queryFn: () =>
      apiClient.get<ReconcileSummaryResponse>("/internal/v1/diagnostics/reconcile-summary"),
    staleTime: 30_000,
  });
}

/** A versioned diagnostic result/report (mirrors the OROS ReportDto). */
export interface DiagnosticResult {
  resultId: string;
  orderId: string;
  reportStatus: string | null;
  version: number;
  critical: boolean;
  criticalReason: string | null;
  reportedBy: string | null;
  acknowledgedAt: string | null;
  createdAt: string | null;
}

type CriticalResultsResponse = ApiResponse<DiagnosticResult[]>;

/** Unacknowledged critical results (critical-results dashboard). */
export function useCriticalUnacknowledged() {
  return useQuery<CriticalResultsResponse>({
    queryKey: ["diagnostics-critical-unacknowledged"],
    queryFn: () =>
      apiClient.get<CriticalResultsResponse>("/internal/v1/diagnostics/critical-unacknowledged"),
    staleTime: 10_000,
  });
}

export interface TurnaroundMetrics {
  totalImaging: number;
  byState: Record<string, number>;
}

/** Imaging workload/turnaround distribution by state. */
export function useDiagnosticsTurnaround() {
  return useQuery<ApiResponse<TurnaroundMetrics>>({
    queryKey: ["diagnostics-turnaround"],
    queryFn: () => apiClient.get<ApiResponse<TurnaroundMetrics>>("/internal/v1/diagnostics/turnaround"),
    staleTime: 30_000,
  });
}

export interface IntegrationStatusEntry {
  adapter: string;
  category: string;
  direction: string;
  configured: boolean;
  detail: string;
}

/** Honest configured/not-configured integration-adapter status. */
export function useIntegrationStatus() {
  return useQuery<ApiResponse<IntegrationStatusEntry[]>>({
    queryKey: ["diagnostics-integration-status"],
    queryFn: () =>
      apiClient.get<ApiResponse<IntegrationStatusEntry[]>>("/internal/v1/diagnostics/integration-status"),
    staleTime: 60_000,
  });
}

/** Acknowledge a critical result, closing the critical loop. */
export function useAcknowledgeCritical() {
  const queryClient = useQueryClient();
  return useMutation<unknown, unknown, { resultId: string; note?: string }>({
    mutationFn: ({ resultId, note }) =>
      apiClient.post(`/internal/v1/diagnostics/results/${encodeURIComponent(resultId)}/critical/ack`,
        note ? { note } : {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["diagnostics-critical-unacknowledged"] });
    },
  });
}
