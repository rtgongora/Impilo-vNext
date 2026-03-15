export type ClientStatus = "ACTIVE" | "SUSPENDED" | "REVOKED";
export type KeyStatus = "ACTIVE" | "ROTATED" | "REVOKED";
export type CertificationStatus = "RUNNING" | "COMPLETED";
export type CertificationResult = "PASS" | "FAIL";
export type DeprecationPosture = "NONE" | "WARN" | "BLOCK";
export type Environment = "SANDBOX" | "PRODUCTION";

export interface DeveloperClient {
  client_id: string;
  client_name: string;
  description: string;
  contact_email: string;
  status: ClientStatus;
  sandbox_enabled: boolean;
  deprecation_posture: DeprecationPosture;
  created_at: string;
}

export interface ApiKey {
  key_id: string;
  key_prefix: string;
  label: string;
  status: KeyStatus;
  created_at: string;
}

export interface ApiKeyIssuance extends ApiKey {
  api_key: string;
}

export interface KeyRotationResult extends ApiKeyIssuance {
  rotated_from: string;
}

export interface CertificationCheck {
  check: string;
  passed: boolean;
  detail: string;
}

export interface Certification {
  certification_id: string;
  client_id: string;
  status: CertificationStatus;
  result: CertificationResult | null;
  checks_total: number;
  checks_passed: number;
  checks_failed: number;
  triggered_by: string | null;
  started_at: string;
  completed_at: string | null;
  report?: string;
}

export interface CertificationRunResult extends Certification {
  checks: CertificationCheck[];
}

export interface FederationReadinessItem {
  item: string;
  ready: boolean;
  detail: string;
}

export interface FederationReadiness {
  client_id: string;
  client_name: string;
  overall_ready: boolean;
  checklist: FederationReadinessItem[];
  checked_at: string;
}

export interface DiscoveryEndpoint {
  method: string;
  path: string;
  description: string;
}

export interface DiscoveryResponse {
  service: string;
  version: string;
  endpoints: DiscoveryEndpoint[];
}

export interface SchemaSubject {
  subject: string;
  compatibility: string;
  description: string;
}

export interface SchemaCatalogEntry extends SchemaSubject {
  version_count: number;
  latest_version?: number;
  latest_fingerprint?: string;
  latest_created_at?: string;
  service?: string;
  entity?: string;
  action?: string;
}

export interface DashboardStats {
  total_clients: number;
  active_clients: number;
  sandbox_clients: number;
  total_keys: number;
  active_keys: number;
  revoked_keys: number;
  rotated_keys: number;
  total_certifications: number;
  certifications_passed: number;
  certifications_failed: number;
}

export interface ListResponse<T> {
  items: T[];
  count: number;
}

export interface RegisterClientParams {
  clientName: string;
  description: string;
  contactEmail: string;
  sandboxEnabled: boolean;
}

export interface IssueKeyParams {
  label: string;
  scopes?: string[];
  expiresInDays?: number;
}

export interface RotateKeyParams {
  label?: string;
  expiresInDays?: number;
}
