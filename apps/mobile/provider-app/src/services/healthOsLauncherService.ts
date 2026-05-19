/**
 * Provider-side Health OS Launcher service.
 *
 * Reads the role/facility-aware launcher payload from msika-apps-service via
 * the experience BFF. Used to render the provider's installed clinical and
 * operational apps (Telemedicine, Imaging, Lab Ops, Logistics, Workflow
 * Dispatch, Public Health Field Tasks, etc.) and to surface "request access"
 * for capabilities the provider may be eligible for.
 */

import { apiClient } from "@impilo/mobile-api-client";

export type LauncherAppState =
  | "INSTALLED"
  | "AVAILABLE"
  | "REQUEST_ACCESS"
  | "PENDING_APPROVAL"
  | "NOT_AVAILABLE_AT_FACILITY"
  | "REQUIRES_CONFIGURATION"
  | "TEMPORARILY_UNAVAILABLE"
  | "SUSPENDED"
  | "DEPRECATED";

export interface LauncherApp {
  id: string;
  itemCode: string;
  name: string;
  description: string;
  type: "APP" | "AI_SKILL" | "EXTENSION" | "WORKFLOW_PACK";
  category: string;
  iconRef?: string | null;
  state: LauncherAppState;
  reason?: string | null;
  launchUrl?: string | null;
  rolesAllowed: string[];
  installationId?: string | null;
}

export interface ActivationRequestPayload {
  itemId: string;
  purposeOfUse: string;
  justification: string;
  facilityId?: string;
}

export async function fetchHealthOsLauncher(
  opts: { facilityId?: string; roles?: string[] } = {},
): Promise<LauncherApp[]> {
  const query: string[] = [];
  if (opts.facilityId) query.push(`facilityId=${encodeURIComponent(opts.facilityId)}`);
  if (opts.roles && opts.roles.length > 0) query.push(`roles=${encodeURIComponent(opts.roles.join(","))}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";
  try {
    const response = await apiClient.get<LauncherApp[] | { data: LauncherApp[] }>(
      `/internal/v1/marketplace/launcher${qs}`,
    );
    const body = response.data as unknown;
    if (Array.isArray(body)) return body as LauncherApp[];
    if (body && typeof body === "object" && Array.isArray((body as { data?: LauncherApp[] }).data)) {
      return (body as { data: LauncherApp[] }).data;
    }
    return [];
  } catch (_e) {
    return [];
  }
}

export async function requestCapabilityActivation(payload: ActivationRequestPayload) {
  const response = await apiClient.post(`/internal/v1/marketplace/activation-requests`, payload);
  return response.data;
}
