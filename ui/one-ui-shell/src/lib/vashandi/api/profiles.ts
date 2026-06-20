import { getVashandi, postVashandi } from "./client";
import type { VashandiActionResponse, VashandiPrecheckRequest, WorkforceProfile } from "../types";

export async function listWorkforceProfiles(params?: Record<string, string | undefined>) {
  return getVashandi<{ items?: WorkforceProfile[] }>("/workforce-profiles", params);
}

export async function getWorkforceProfile(id: string) {
  return getVashandi<WorkforceProfile>(`/workforce-profiles/${encodeURIComponent(id)}`);
}

export async function reconcileWorkforceProfile(profileId: string) {
  return postVashandi("/workforce-profiles/reconcile", { profileId });
}

export type ImportWorkforceRow = {
  healthId?: string;
  providerWorkerId?: string;
  keycloakUserId?: string;
  organisationId?: string;
  organisationType?: string;
  membershipType?: string;
  workerType?: string;
  profession?: string;
  cadre?: string;
  currentStatus?: string;
  assignmentType?: string;
  facilityId?: string;
  departmentId?: string;
  roleTemplateId?: string;
};

export type ImportBridgeRequest = {
  source: string;
  rows: ImportWorkforceRow[];
};

export type ImportBridgeResult = {
  profilesImported?: number;
  assignmentsImported?: number;
  profileIds?: string[];
  assignmentIds?: string[];
  status?: string;
};

export async function importWorkforceBridge(body: ImportBridgeRequest) {
  return postVashandi<ImportBridgeResult>("/workforce-profiles/import-bridge", body);
}

export async function runVashandiPrecheck(body: VashandiPrecheckRequest) {
  return postVashandi("/precheck", body);
}

export function profilesFromResponse(response: VashandiActionResponse): WorkforceProfile[] {
  const data = response.data;
  if (!data || typeof data !== "object") return [];
  const record = data as Record<string, unknown>;
  if (Array.isArray(record.items)) return record.items as WorkforceProfile[];
  if (Array.isArray(record.profiles)) return record.profiles as WorkforceProfile[];
  if (Array.isArray(data)) return data as WorkforceProfile[];
  return [];
}
