import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/ubomi";

export interface UbomiStatus {
  available: boolean;
  message?: string;
}

export interface UbomiVerificationResult {
  registrationNumber: string;
  valid: boolean;
  status?: string;
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

export async function fetchUbomiStatus(): Promise<UbomiStatus> {
  const response = await apiClient.get<{ data: unknown }>(`${BASE}/status`);
  const data = asRecord(asRecord(response.data).data ?? response.data);
  return {
    available: Boolean(data.available),
    message: data.message != null ? String(data.message) : undefined,
  };
}

export async function verifyUbomiRegistration(registrationNumber: string): Promise<UbomiVerificationResult> {
  const response = await apiClient.get<{ data: unknown }>(
    `${BASE}/verifications/${encodeURIComponent(registrationNumber)}`,
  );
  const data = asRecord(asRecord(response.data).data ?? response.data);
  return {
    registrationNumber,
    valid: Boolean(data.valid ?? data.verified ?? false),
    status: data.status != null ? String(data.status) : undefined,
  };
}

export async function listUbomiBirthNotifications(): Promise<unknown[]> {
  const response = await apiClient.get<{ data: unknown }>(`${BASE}/births?page=0&size=10`);
  const data = asRecord(response.data).data ?? response.data;
  return Array.isArray(data) ? data : [];
}

export async function listUbomiDeathNotifications(): Promise<unknown[]> {
  const response = await apiClient.get<{ data: unknown }>(`${BASE}/deaths?page=0&size=10`);
  const data = asRecord(response.data).data ?? response.data;
  return Array.isArray(data) ? data : [];
}

export async function submitUbomiBirthNotification(body: Record<string, unknown>): Promise<unknown> {
  const response = await apiClient.post<{ data: unknown }>(`${BASE}/births`, body);
  return asRecord(response.data).data ?? response.data;
}

export async function submitUbomiDeathNotification(body: Record<string, unknown>): Promise<unknown> {
  const response = await apiClient.post<{ data: unknown }>(`${BASE}/deaths`, body);
  return asRecord(response.data).data ?? response.data;
}
