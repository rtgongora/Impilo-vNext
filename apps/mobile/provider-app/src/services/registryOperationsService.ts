import { apiClient } from "@impilo/mobile-api-client";

export type RegistryOperationRecord = Record<string, unknown>;

function parseJson(value: unknown): unknown {
  if (typeof value !== "string") return value;
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

function unwrap(value: unknown): unknown {
  const parsed = parseJson(value);
  if (parsed && typeof parsed === "object" && "data" in parsed) {
    return parseJson((parsed as { data?: unknown }).data);
  }
  return parsed;
}

function rows(value: unknown): RegistryOperationRecord[] {
  const unwrapped = unwrap(value);
  if (Array.isArray(unwrapped)) {
    return unwrapped.filter((item): item is RegistryOperationRecord => typeof item === "object" && item !== null);
  }
  if (unwrapped && typeof unwrapped === "object") {
    const record = unwrapped as RegistryOperationRecord;
    if (Array.isArray(record.items)) return rows(record.items);
    if (Array.isArray(record.content)) return rows(record.content);
    if (Array.isArray(record.data)) return rows(record.data);
  }
  return [];
}

export async function fetchBootstrapSnapshot(): Promise<unknown> {
  const response = await apiClient.get("/internal/v1/registry-intake/bootstrap/snapshot");
  return unwrap(response.data);
}

export async function fetchFacilityDashboardSummary(): Promise<unknown> {
  const response = await apiClient.get("/internal/v1/facility-registry/dashboard/summary");
  return unwrap(response.data);
}

export async function searchFacilityRegistry(params: {
  search?: string;
  regulatoryStatus?: string;
  size?: number;
}): Promise<RegistryOperationRecord[]> {
  const query = new URLSearchParams();
  if (params.search) query.set("search", params.search);
  if (params.regulatoryStatus) query.set("regulatoryStatus", params.regulatoryStatus);
  query.set("size", String(params.size ?? 10));
  const response = await apiClient.get(`/internal/v1/facility-registry/facilities?${query.toString()}`);
  return rows(response.data);
}

export async function createFacilityApplication(payload: RegistryOperationRecord): Promise<unknown> {
  const response = await apiClient.post("/internal/v1/facility-registry/applications", payload);
  return unwrap(response.data);
}

export async function submitFacilityApplication(applicationId: string): Promise<unknown> {
  const response = await apiClient.post(`/internal/v1/facility-registry/applications/${encodeURIComponent(applicationId)}/submit`);
  return unwrap(response.data);
}

export async function markFacilityApplicationReady(applicationId: string): Promise<unknown> {
  const response = await apiClient.post(
    `/internal/v1/facility-registry/applications/${encodeURIComponent(applicationId)}/ready-for-inspection`,
  );
  return unwrap(response.data);
}

export async function fetchPendingLocalityProposals(): Promise<RegistryOperationRecord[]> {
  const response = await apiClient.get("/internal/v1/registry/localities/proposals/pending");
  return rows(response.data);
}

export async function proposeLocality(payload: RegistryOperationRecord): Promise<unknown> {
  const response = await apiClient.post("/internal/v1/registry/localities/proposals", payload);
  return unwrap(response.data);
}

export async function approveLocalityProposal(id: string, reviewerNote: string): Promise<unknown> {
  const response = await apiClient.post(`/internal/v1/registry/localities/proposals/${encodeURIComponent(id)}/approve`, {
    reviewerNote,
  });
  return unwrap(response.data);
}

export async function rejectLocalityProposal(id: string, reviewerNote: string): Promise<unknown> {
  const response = await apiClient.post(`/internal/v1/registry/localities/proposals/${encodeURIComponent(id)}/reject`, {
    reviewerNote,
  });
  return unwrap(response.data);
}

export async function createRegistryIntakeSession(payload: RegistryOperationRecord): Promise<unknown> {
  const response = await apiClient.post("/internal/v1/registry-intake/sessions", payload);
  return unwrap(response.data);
}

export async function createRegistryRecoveryRequest(payload: RegistryOperationRecord): Promise<unknown> {
  const response = await apiClient.post("/internal/v1/registry-intake/recovery-requests", payload);
  return unwrap(response.data);
}

export async function createRegistryImportJob(payload: RegistryOperationRecord): Promise<unknown> {
  const response = await apiClient.post("/internal/v1/registry-intake/import-jobs", payload);
  return unwrap(response.data);
}

export async function executeRegistryImportJob(jobId: string, dryRun: boolean): Promise<unknown> {
  const response = await apiClient.post(`/internal/v1/registry-intake/import-jobs/${encodeURIComponent(jobId)}/execute`, {
    dryRun,
  });
  return unwrap(response.data);
}

export async function searchProductRegistry(queryText: string): Promise<RegistryOperationRecord[]> {
  const query = new URLSearchParams({ q: queryText, size: "10", page: "0" });
  const response = await apiClient.get(`/internal/v1/product-registry/search?${query.toString()}`);
  return rows(response.data);
}

export async function resolveTerminologyArtifact(canonicalUrl: string, version?: string): Promise<unknown> {
  const query = new URLSearchParams({ canonicalUrl });
  if (version) query.set("version", version);
  const response = await apiClient.get(`/internal/v1/registry/zibo/artifacts/resolve?${query.toString()}`);
  return unwrap(response.data);
}

export async function listTrustPolicies(): Promise<RegistryOperationRecord[]> {
  const response = await apiClient.get("/internal/v1/admin/trust/policies");
  return rows(response.data);
}

export async function listTrustConsents(subjectId: string): Promise<RegistryOperationRecord[]> {
  const query = new URLSearchParams({ subjectId, size: "10" });
  const response = await apiClient.get(`/internal/v1/admin/trust/consents?${query.toString()}`);
  return rows(response.data);
}

export async function probeRegistryServiceHealth(mavenModule: string): Promise<unknown> {
  const response = await apiClient.get(`/internal/v1/registry-shell/services/${encodeURIComponent(mavenModule)}/health`);
  return response.data;
}
