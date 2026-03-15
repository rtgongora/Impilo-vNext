/**
 * Developer Console — Developer Portal + Schema Registry API
 *
 * Typed API client methods for all developer-portal-service and
 * schema-registry-service endpoints.
 * All requests route through the trust-aware apiClient.
 */

import { apiClient } from "./apiClient";
import type {
  DeveloperClient,
  ApiKey,
  ApiKeyIssuance,
  KeyRotationResult,
  Certification,
  CertificationRunResult,
  FederationReadiness,
  DiscoveryResponse,
  SchemaSubject,
  SchemaCatalogEntry,
  DashboardStats,
  ListResponse,
  RegisterClientParams,
  IssueKeyParams,
  RotateKeyParams,
} from "@/types/developer";

const DEV_BASE = "/internal/v1/developer";
const SCHEMA_BASE = "/internal/v1/schemas";

// ── Dashboard ──

export function getDashboardStats(): Promise<DashboardStats> {
  return apiClient.get<DashboardStats>(`${DEV_BASE}/dashboard/stats`);
}

// ── Client Registration ──

export function listClients(): Promise<ListResponse<DeveloperClient>> {
  return apiClient.get<ListResponse<DeveloperClient>>(`${DEV_BASE}/clients`);
}

export function getClient(clientId: string): Promise<DeveloperClient> {
  return apiClient.get<DeveloperClient>(`${DEV_BASE}/clients/${clientId}`);
}

export function registerClient(params: RegisterClientParams): Promise<DeveloperClient> {
  return apiClient.post<DeveloperClient>(`${DEV_BASE}/clients`, params);
}

// ── API Key Management ──

export function listKeys(clientId: string): Promise<ListResponse<ApiKey>> {
  return apiClient.get<ListResponse<ApiKey>>(`${DEV_BASE}/clients/${clientId}/keys`);
}

export function issueKey(clientId: string, params: IssueKeyParams): Promise<ApiKeyIssuance> {
  return apiClient.post<ApiKeyIssuance>(`${DEV_BASE}/clients/${clientId}/keys`, params);
}

export function rotateKey(keyId: string, params: RotateKeyParams): Promise<KeyRotationResult> {
  return apiClient.post<KeyRotationResult>(`${DEV_BASE}/keys/${keyId}/rotate`, params);
}

export function revokeKey(keyId: string): Promise<{ key_id: string; status: string }> {
  return apiClient.delete<{ key_id: string; status: string }>(`${DEV_BASE}/keys/${keyId}`);
}

// ── Sandbox Configuration ──

export function configureSandbox(
  clientId: string,
  config: Record<string, unknown>,
): Promise<{ client_id: string; sandbox_enabled: boolean; sandbox_config: string }> {
  return apiClient.put(`${DEV_BASE}/clients/${clientId}/sandbox`, { config });
}

// ── Deprecation Posture ──

export function updateDeprecationPosture(
  clientId: string,
  posture: "NONE" | "WARN" | "BLOCK",
): Promise<{ client_id: string; deprecation_posture: string }> {
  return apiClient.put(`${DEV_BASE}/clients/${clientId}/deprecation-posture`, { posture });
}

// ── Certification ──

export function runCertification(
  clientId: string,
  triggeredBy: string,
): Promise<CertificationRunResult> {
  return apiClient.post<CertificationRunResult>(`${DEV_BASE}/clients/${clientId}/certify`, {
    triggeredBy,
  });
}

export function listCertifications(clientId: string): Promise<ListResponse<Certification>> {
  return apiClient.get<ListResponse<Certification>>(`${DEV_BASE}/clients/${clientId}/certifications`);
}

export function getCertification(certId: string): Promise<Certification> {
  return apiClient.get<Certification>(`${DEV_BASE}/certifications/${certId}`);
}

// ── Federation Readiness ──

export function getFederationReadiness(clientId: string): Promise<FederationReadiness> {
  return apiClient.get<FederationReadiness>(`${DEV_BASE}/clients/${clientId}/federation-readiness`);
}

// ── Discovery ──

export function getDiscovery(): Promise<DiscoveryResponse> {
  return apiClient.get<DiscoveryResponse>(`${DEV_BASE}/discovery`);
}

// ── Schema Registry ──

export function listSchemaSubjects(): Promise<ListResponse<SchemaSubject>> {
  return apiClient.get<ListResponse<SchemaSubject>>(`${SCHEMA_BASE}/subjects`);
}

export function getSchemaCatalog(): Promise<ListResponse<SchemaCatalogEntry>> {
  return apiClient.get<ListResponse<SchemaCatalogEntry>>(`${SCHEMA_BASE}/catalog`);
}

export function getSchemaSubject(subject: string): Promise<{
  subject: string;
  compatibility: string;
  versions: { version: number; fingerprint: string; created_at: string }[];
}> {
  return apiClient.get(`${SCHEMA_BASE}/subjects/${subject}`);
}

export function getSchemaVersion(
  subject: string,
  version: number,
): Promise<{ subject: string; version: number; schema: string; fingerprint: string }> {
  return apiClient.get(`${SCHEMA_BASE}/subjects/${subject}/versions/${version}`);
}

export function checkSchemaCompatibility(
  subject: string,
  schema: string,
): Promise<{ compatible: boolean; violations: string[] }> {
  return apiClient.post(`${SCHEMA_BASE}/compatibility`, { subject, schema });
}
