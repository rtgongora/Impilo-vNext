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
  /** Generalised fine-grained lifecycle state for all categories (imaging/lab/procedure). */
  workflowState: string | null;
  studyUid: string | null;
  studyViewerUrl: string | null;
}

/** A structured laboratory observation (value/unit/reference-range/flags). */
export interface ResultObservation {
  observationId: string;
  resultId: string;
  orderId: string;
  sequence: number;
  analyteCode: string | null;
  analyteName: string;
  valueText: string | null;
  valueNumeric: number | null;
  unit: string | null;
  refRangeLow: number | null;
  refRangeHigh: number | null;
  refRangeText: string | null;
  abnormalFlag: string | null;
  criticalFlag: boolean;
  comment: string | null;
}

/** A result captured against an order. */
export interface OrderResult {
  resultId: string;
  orderId: string;
  kind: string;
  reportStatus: string | null;
  isCritical: boolean;
  impression: string | null;
  version: number;
  createdAt: string | null;
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
  specimenType?: string;
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
      specimenType: it.specimenType,
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
  supersedesResultId: string | null;
  impression: string | null;
  recommendations: string | null;
  critical: boolean;
  criticalReason: string | null;
  reportedBy: string | null;
  validatedBy: string | null;
  releasedAt: string | null;
  acknowledgedAt: string | null;
  createdAt: string | null;
}

type CriticalResultsResponse = ApiResponse<DiagnosticResult[]>;

export type ReportAction = "preliminary" | "final" | "amend" | "addendum";

export interface AuthorReportInput {
  orderId: string;
  action: ReportAction;
  summary?: string;
  impression?: string;
  recommendations?: string;
  reason?: string;
}

/** Full report version chain for an order (newest first). */
export function useOrderReportVersions(orderId?: string) {
  return useQuery<ApiResponse<DiagnosticResult[]>>({
    queryKey: ["diagnostics-report-versions", orderId ?? null],
    queryFn: () =>
      apiClient.get<ApiResponse<DiagnosticResult[]>>(
        `/internal/v1/diagnostics/results/${encodeURIComponent(orderId!)}/versions`,
      ),
    enabled: !!orderId,
    staleTime: 5_000,
  });
}

/** Author or amend a report (preliminary/final/amend/addendum). */
export function useAuthorReport() {
  const queryClient = useQueryClient();
  return useMutation<DiagnosticResult, unknown, AuthorReportInput>({
    mutationFn: async ({ orderId, action, summary, impression, recommendations, reason }) => {
      const body: Record<string, unknown> = { impression, recommendations };
      if (summary) body.summary = summary;
      if (action === "amend" || action === "addendum") body.reason = reason;
      const res = await apiClient.post<ApiResponse<DiagnosticResult>>(
        `/internal/v1/diagnostics/results/${encodeURIComponent(orderId)}/report/${action}`,
        body,
      );
      return res.data;
    },
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ["diagnostics-report-versions", vars.orderId] });
      queryClient.invalidateQueries({ queryKey: ["diagnostics-orders"] });
    },
  });
}

/** Release a report version to requesters. */
export function useReleaseReport() {
  const queryClient = useQueryClient();
  return useMutation<unknown, unknown, { resultId: string; orderId: string; note?: string }>({
    mutationFn: ({ resultId, note }) =>
      apiClient.post(`/internal/v1/diagnostics/results/${encodeURIComponent(resultId)}/release`,
        note ? { note } : {}),
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ["diagnostics-report-versions", vars.orderId] });
    },
  });
}

/** Unacknowledged critical results (critical-results dashboard). */
export function useCriticalUnacknowledged() {
  return useQuery<CriticalResultsResponse>({
    queryKey: ["diagnostics-critical-unacknowledged"],
    queryFn: () =>
      apiClient.get<CriticalResultsResponse>("/internal/v1/diagnostics/critical-unacknowledged"),
    staleTime: 10_000,
  });
}

/** Imaging-team worklist filtered by fine-grained imaging state. */
export function useImagingWorklist(states?: string) {
  return useQuery<OrdersResponse>({
    queryKey: ["diagnostics-imaging-worklist", states ?? null],
    queryFn: () => {
      const qs = states ? `?states=${encodeURIComponent(states)}` : "";
      return apiClient.get<OrdersResponse>(`/internal/v1/diagnostics/imaging-worklist${qs}`);
    },
    staleTime: 10_000,
  });
}

/** Drive a guarded imaging-workflow transition (accept/start/complete/...). */
export function useImagingTransition() {
  const queryClient = useQueryClient();
  return useMutation<unknown, unknown, { orderId: string; target: string; reason?: string }>({
    mutationFn: ({ orderId, target, reason }) =>
      apiClient.post(`/internal/v1/diagnostics/orders/${encodeURIComponent(orderId)}/imaging-transition`,
        reason ? { target, reason } : { target }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["diagnostics-imaging-worklist"] });
      queryClient.invalidateQueries({ queryKey: ["diagnostics-orders"] });
    },
  });
}

/** Launch a governed DICOM viewer session for an order's linked study; returns the launch context. */
export function useLaunchOrderViewer() {
  return useMutation<Record<string, unknown>, unknown, { orderId: string }>({
    mutationFn: async ({ orderId }) => {
      const res = await apiClient.post<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/diagnostics/orders/${encodeURIComponent(orderId)}/viewer`);
      return res.data;
    },
  });
}

export interface ShareLinkRef {
  linkId: string | null;
  shareToken: string | null;
  status: string | null;
  expiresAt: string | null;
}

/** Issue a printable, OTP-protected QR for a patient-carried order. */
export function useOrderPrintable() {
  return useMutation<ShareLinkRef, unknown, { orderId: string; expiryHours?: number; maxClaims?: number }>({
    mutationFn: async ({ orderId, expiryHours, maxClaims }) => {
      const res = await apiClient.post<ApiResponse<ShareLinkRef>>(
        `/internal/v1/diagnostics/orders/${encodeURIComponent(orderId)}/printable`,
        { expiryHours, maxClaims },
      );
      return res.data;
    },
  });
}

/** Claim a patient-carried order QR at a fulfilling facility. */
export function useQrClaim() {
  const queryClient = useQueryClient();
  return useMutation<DiagnosticOrder, unknown, { shareToken: string; otp?: string; deviceFingerprint?: string }>({
    mutationFn: async (body) => {
      const res = await apiClient.post<ApiResponse<DiagnosticOrder>>(
        "/internal/v1/diagnostics/intake/qr/claim", body);
      return res.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["diagnostics-orders"] }),
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

/** All results for an order (patient-file investigations view). */
export function useOrderResults(orderId: string | null | undefined) {
  return useQuery<ApiResponse<OrderResult[]>>({
    queryKey: ["diagnostics-order-results", orderId ?? null],
    queryFn: () =>
      apiClient.get<ApiResponse<OrderResult[]>>(
        `/internal/v1/diagnostics/orders/${encodeURIComponent(orderId as string)}/results`,
      ),
    enabled: !!orderId,
    staleTime: 15_000,
  });
}

/** Structured laboratory observations for a result (value/unit/reference-range/flags). */
export function useResultObservations(resultId: string | null | undefined) {
  return useQuery<ApiResponse<ResultObservation[]>>({
    queryKey: ["diagnostics-result-observations", resultId ?? null],
    queryFn: () =>
      apiClient.get<ApiResponse<ResultObservation[]>>(
        `/internal/v1/diagnostics/results/${encodeURIComponent(resultId as string)}/observations`,
      ),
    enabled: !!resultId,
    staleTime: 15_000,
  });
}

/** A laboratory specimen (collection/dispatch/receipt lifecycle). */
export interface Specimen {
  specimenId: string;
  orderId: string;
  specimenType: string | null;
  sampleId: string | null;
  labNumber: string | null;
  status: string;
  collectedBy: string | null;
  collectedAt: string | null;
  rejectionReason: string | null;
}

/** Category fulfilment worklist (lab/procedure/assessment) filtered by workflow state. */
export function useFulfilmentWorklist(type: string, states?: string) {
  return useQuery<OrdersResponse>({
    queryKey: ["diagnostics-fulfilment-worklist", type, states ?? null],
    queryFn: () => {
      const params = new URLSearchParams({ type });
      if (states) params.set("states", states);
      return apiClient.get<OrdersResponse>(
        `/internal/v1/diagnostics/fulfilment-worklist?${params.toString()}`,
      );
    },
    staleTime: 15_000,
  });
}

/** Specimens for an order. */
export function useOrderSpecimens(orderId: string | null | undefined) {
  return useQuery<ApiResponse<Specimen[]>>({
    queryKey: ["diagnostics-order-specimens", orderId ?? null],
    queryFn: () =>
      apiClient.get<ApiResponse<Specimen[]>>(
        `/internal/v1/diagnostics/orders/${encodeURIComponent(orderId as string)}/specimens`,
      ),
    enabled: !!orderId,
    staleTime: 10_000,
  });
}

/** Drive a guarded fine-grained workflow transition (lab/procedure). */
export function useWorkflowTransition() {
  const queryClient = useQueryClient();
  return useMutation<unknown, unknown, { orderId: string; target: string; reason?: string }>({
    mutationFn: ({ orderId, target, reason }) =>
      apiClient.post(`/internal/v1/diagnostics/orders/${encodeURIComponent(orderId)}/workflow/transition`, {
        target,
        reason,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["diagnostics-fulfilment-worklist"] });
    },
  });
}

/** Record a specimen collected against a lab order. */
export function useCollectSpecimen() {
  const queryClient = useQueryClient();
  return useMutation<unknown, unknown, { orderId: string; specimenType?: string; specimenSource?: string }>({
    mutationFn: ({ orderId, ...body }) =>
      apiClient.post(`/internal/v1/diagnostics/orders/${encodeURIComponent(orderId)}/specimens`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["diagnostics-fulfilment-worklist"] });
      queryClient.invalidateQueries({ queryKey: ["diagnostics-order-specimens"] });
    },
  });
}

/** Drive a specimen lifecycle action (dispatch/receive/reject/recollect). */
export function useSpecimenAction() {
  const queryClient = useQueryClient();
  return useMutation<unknown, unknown, { specimenId: string; action: string; labNumber?: string; reason?: string }>({
    mutationFn: ({ specimenId, action, ...body }) =>
      apiClient.post(`/internal/v1/diagnostics/specimens/${encodeURIComponent(specimenId)}/${action}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["diagnostics-order-specimens"] });
      queryClient.invalidateQueries({ queryKey: ["diagnostics-fulfilment-worklist"] });
    },
  });
}

/** Read a service-catalogue segment (services / orderables / specimen-config). */
export function useCatalogue(segment: string) {
  return useQuery<ApiResponse<unknown>>({
    queryKey: ["diagnostics-catalogue", segment],
    queryFn: () =>
      apiClient.get<ApiResponse<unknown>>(`/internal/v1/diagnostics/catalogue/${segment}`),
    staleTime: 30_000,
  });
}

/** Curate (upsert) an admin catalogue (service-catalogue / orderable-catalogue / specimen-config). */
export function useSaveCatalogue() {
  const queryClient = useQueryClient();
  return useMutation<unknown, unknown, { adminPath: string; body: Record<string, unknown> }>({
    mutationFn: ({ adminPath, body }) =>
      apiClient.put(`/internal/v1/diagnostics/admin/catalogue/${adminPath}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["diagnostics-catalogue"] });
    },
  });
}
