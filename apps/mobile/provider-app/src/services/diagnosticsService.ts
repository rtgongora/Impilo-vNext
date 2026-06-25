/**
 * Diagnostics service — provider-facing diagnostic/imaging order tracking and results inbox,
 * backed by the experience-bff diagnostics surface (/internal/v1/diagnostics/*) → OROS.
 */

import { apiClient } from "@impilo/mobile-api-client";

export interface DiagnosticOrder {
  orderId: string;
  orderType: string;
  status: string;
  patientCpid: string;
  accessionNumber: string | null;
  imagingState: string | null;
  placedAt: string | null;
  referringProviderName: string | null;
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

/** Requester results inbox — orders with results ready to review. */
export async function fetchResultsInbox(requester?: string): Promise<DiagnosticOrder[]> {
  const qs = requester ? `?requester=${encodeURIComponent(requester)}` : "";
  const response = await apiClient.get<{ data: DiagnosticOrder[] }>(
    `/internal/v1/diagnostics/results-inbox${qs}`,
  );
  return response.data.data ?? [];
}
