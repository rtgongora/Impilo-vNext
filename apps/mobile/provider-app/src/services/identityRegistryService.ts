import { apiClient } from "@impilo/mobile-api-client";

export type IdentityRegistryRecord = Record<string, unknown>;

function rows(value: unknown): IdentityRegistryRecord[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is IdentityRegistryRecord => typeof item === "object" && item !== null);
  }
  if (value && typeof value === "object") {
    const record = value as Record<string, unknown>;
    if (Array.isArray(record.data)) return rows(record.data);
    if (Array.isArray(record.items)) return rows(record.items);
    if (Array.isArray(record.content)) return rows(record.content);
  }
  return [];
}

function data(value: unknown): unknown {
  if (value && typeof value === "object" && "data" in value) {
    return (value as { data?: unknown }).data;
  }
  return value;
}

export async function searchIdentities(query: string): Promise<IdentityRegistryRecord[]> {
  const response = await apiClient.get(`/internal/v1/identity/search?query=${encodeURIComponent(query)}`);
  return rows(data(response.data));
}

export async function resolvePatientIdentity(healthId: string): Promise<unknown> {
  const response = await apiClient.post("/internal/v1/identity/patient/resolve", { healthId });
  return data(response.data);
}

export async function registerPatientIdentity(payload: IdentityRegistryRecord): Promise<unknown> {
  const response = await apiClient.post("/internal/v1/identity/patient/register", payload);
  return data(response.data);
}

export async function startPatientRecovery(payload: IdentityRegistryRecord): Promise<unknown> {
  const response = await apiClient.post("/internal/v1/identity/patient/recovery/start", payload);
  return data(response.data);
}

export async function verifyPatientRecovery(payload: IdentityRegistryRecord): Promise<unknown> {
  const response = await apiClient.post("/internal/v1/identity/patient/recovery/verify", payload);
  return data(response.data);
}

export async function createProviderIdentity(payload: IdentityRegistryRecord): Promise<unknown> {
  const response = await apiClient.post("/internal/v1/identity/provider/create", payload);
  return data(response.data);
}

export async function getProviderIdentity(providerId: string): Promise<unknown> {
  const response = await apiClient.get(`/internal/v1/identity/provider/${encodeURIComponent(providerId)}`);
  return data(response.data);
}
