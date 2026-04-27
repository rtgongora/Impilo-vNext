import { apiClient, type ApiResponse } from "./client";

/**
 * Path segment for Mvumo patient-scoped communication preference routes.
 * Uses {@code Patient/} prefix when the caller passes a bare CPID.
 */
export function patientRefForMvumoPath(patientRefOrCpid: string): string {
  const s = patientRefOrCpid.trim();
  if (s.startsWith("Patient/")) {
    return s;
  }
  return `Patient/${s}`;
}

export function communicationPreferencesPath(patientRefOrCpid: string): string {
  return `/internal/v1/mvumo/patients/${encodeURIComponent(patientRefForMvumoPath(patientRefOrCpid))}/communication-preferences`;
}

export interface MvumoCommunicationPreferencesEnvelope {
  exists?: boolean;
  revisionId?: string;
  bundleVersion?: number;
  lifecycleState?: string;
  payload?: Record<string, unknown>;
  [key: string]: unknown;
}

export function getCommunicationPreferences(
  patientRefOrCpid: string,
  options?: { tenantIdHeader?: string }
) {
  const path = communicationPreferencesPath(patientRefOrCpid);
  const headers: Record<string, string> = {};
  if (options?.tenantIdHeader) {
    headers["X-Tenant-ID"] = options.tenantIdHeader;
  }
  return apiClient.get<ApiResponse<MvumoCommunicationPreferencesEnvelope>>(path, { headers: Object.keys(headers).length ? headers : undefined });
}

export function putCommunicationPreferences(
  patientRefOrCpid: string,
  body: Record<string, unknown>,
  options?: { tenantIdHeader?: string; actorRefHeader?: string }
) {
  const path = communicationPreferencesPath(patientRefOrCpid);
  const headers: Record<string, string> = {};
  if (options?.tenantIdHeader) {
    headers["X-Tenant-ID"] = options.tenantIdHeader;
  }
  if (options?.actorRefHeader) {
    headers["X-Actor-Ref"] = options.actorRefHeader;
  }
  return apiClient.put<ApiResponse<Record<string, unknown>>>(path, body, {
    headers: Object.keys(headers).length ? headers : undefined,
  });
}

export function postCommunicationPreferencesEvaluate(body: {
  patientRef: string;
  proposedChannel: string;
  messageKind?: string;
  emergencyOverride?: boolean;
}) {
  return apiClient.post<ApiResponse<Record<string, unknown>>>(
    "/internal/v1/mvumo/communication-preferences/evaluate",
    body
  );
}

export function postCommunicationPreferencesOfflineSync(body: { items: unknown[] }) {
  return apiClient.post<ApiResponse<Record<string, unknown>>>(
    "/internal/v1/mvumo/communication-preferences/offline-sync",
    body
  );
}
