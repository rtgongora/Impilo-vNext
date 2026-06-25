/**
 * Diagnostics service — provider-facing diagnostic/imaging order tracking and results inbox,
 * backed by the experience-bff diagnostics surface (/internal/v1/diagnostics/*) → OROS.
 */

import { apiClient } from "@impilo/mobile-api-client";
import { queueClinicalCreateOnRetryableError } from "./offlineClinicalQueue";

const ORDER_DRAFT_PATH = "/internal/v1/diagnostics/orders/draft";

export interface DiagnosticOrderDraft {
  orderType: string;
  patientCpid: string;
  priority?: string;
  clinicalNotes?: string;
  referringProviderId?: string;
  items: Array<{ code: string; modality?: string; procedureCode?: string }>;
}

export interface SubmitDiagnosticOrderResult {
  order?: DiagnosticOrder;
  queuedOffline: boolean;
  recordId?: string;
}

export interface DiagnosticOrder {
  orderId: string;
  orderType: string;
  status: string;
  patientCpid: string;
  accessionNumber: string | null;
  imagingState: string | null;
  workflowState?: string | null;
  placedAt: string | null;
  referringProviderName: string | null;
}

export interface Specimen {
  specimenId: string;
  orderId: string;
  specimenType: string | null;
  sampleId: string | null;
  labNumber: string | null;
  status: string;
}

/** Track diagnostic orders, optionally filtered by status or order type. */
export async function fetchDiagnosticOrders(opts?: { status?: string; type?: string }): Promise<DiagnosticOrder[]> {
  const params = new URLSearchParams();
  if (opts?.status) params.set("status", opts.status);
  if (opts?.type) params.set("type", opts.type);
  const qs = params.toString();
  const response = await apiClient.get<{ data: DiagnosticOrder[] }>(
    `/internal/v1/diagnostics/orders${qs ? `?${qs}` : ""}`,
  );
  return response.data.data ?? [];
}

/**
 * Submit a diagnostic order draft. Online: posts to the BFF. Offline / retryable failure: the
 * write is captured in the offline queue and replayed by the sync engine on reconnect — so a
 * provider can place an order at the point of care without connectivity (spec §15).
 */
export async function submitDiagnosticOrder(draft: DiagnosticOrderDraft): Promise<SubmitDiagnosticOrderResult> {
  const payload: Record<string, unknown> = { ...draft, requestSource: "INTERNAL" };
  try {
    const response = await apiClient.post<{ data: DiagnosticOrder }>(ORDER_DRAFT_PATH, payload);
    return { order: response.data.data, queuedOffline: false };
  } catch (err) {
    const queued = await queueClinicalCreateOnRetryableError(err, {
      collection: "diagnostic_orders",
      path: ORDER_DRAFT_PATH,
      payload,
    });
    if (queued) {
      return { queuedOffline: true, recordId: queued.recordId };
    }
    throw err;
  }
}

/** Requester results inbox — orders with results ready to review. */
export async function fetchResultsInbox(requester?: string): Promise<DiagnosticOrder[]> {
  const qs = requester ? `?requester=${encodeURIComponent(requester)}` : "";
  const response = await apiClient.get<{ data: DiagnosticOrder[] }>(
    `/internal/v1/diagnostics/results-inbox${qs}`,
  );
  return response.data.data ?? [];
}

/** Category fulfilment worklist (lab/procedure/assessment) filtered by workflow state. */
export async function fetchFulfilmentWorklist(type: string, states?: string): Promise<DiagnosticOrder[]> {
  const params = new URLSearchParams({ type });
  if (states) params.set("states", states);
  const response = await apiClient.get<{ data: DiagnosticOrder[] }>(
    `/internal/v1/diagnostics/fulfilment-worklist?${params.toString()}`,
  );
  return response.data.data ?? [];
}

/** Specimens for an order. */
export async function fetchOrderSpecimens(orderId: string): Promise<Specimen[]> {
  const response = await apiClient.get<{ data: Specimen[] }>(
    `/internal/v1/diagnostics/orders/${encodeURIComponent(orderId)}/specimens`,
  );
  return response.data.data ?? [];
}

/** Record a specimen collected against a lab order (offline-queued on retryable failure). */
export async function collectSpecimen(
  orderId: string,
  body?: { specimenType?: string; specimenSource?: string },
): Promise<{ queuedOffline: boolean; recordId?: string }> {
  const path = `/internal/v1/diagnostics/orders/${encodeURIComponent(orderId)}/specimens`;
  const payload: Record<string, unknown> = { ...(body ?? {}) };
  try {
    await apiClient.post(path, payload);
    return { queuedOffline: false };
  } catch (err) {
    const queued = await queueClinicalCreateOnRetryableError(err, {
      collection: "specimen_collections",
      path,
      payload,
    });
    if (queued) return { queuedOffline: true, recordId: queued.recordId };
    throw err;
  }
}

/** Drive a specimen lifecycle action (dispatch/receive/reject/recollect). */
export async function specimenAction(
  specimenId: string,
  action: string,
  body?: { labNumber?: string; reason?: string },
): Promise<void> {
  await apiClient.post(
    `/internal/v1/diagnostics/specimens/${encodeURIComponent(specimenId)}/${action}`,
    body ?? {},
  );
}

/** Drive a guarded fine-grained workflow transition (lab/procedure). */
export async function transitionWorkflow(orderId: string, target: string, reason?: string): Promise<void> {
  await apiClient.post(
    `/internal/v1/diagnostics/orders/${encodeURIComponent(orderId)}/workflow/transition`,
    { target, reason },
  );
}

/** Acknowledge a critical result from the field, closing the critical loop. */
export async function acknowledgeCritical(resultId: string, note?: string): Promise<void> {
  await apiClient.post(
    `/internal/v1/diagnostics/results/${encodeURIComponent(resultId)}/critical/ack`,
    note ? { note } : {},
  );
}
