import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * Facility Configuration Console hooks. Backed by policy-protected experience-bff routes under
 * /internal/v1/admin/** which proxy TUSO — the source of truth for facility configuration (service
 * points, workspaces, derived queue definitions, setup checklist, downstream readiness). Queue
 * definitions are read-only here (TUSO-owned; materialised into PCT for care operations); use the
 * queue materialisation viewer hooks for live PCT status. No fabricated status.
 */

const BASE = "/internal/v1/admin/facilities";

export interface FacilityConfigurationSummary {
  facilityId: number;
  facilityUuid: string | null;
  name: string;
  facilityCode: string | null;
  facilityType: string | null;
  province: string | null;
  district: string | null;
  regulatoryStatus: string | null;
  operationalStatus: string | null;
  status: string | null;
  configurationState: string;
  activeServicePointCount: number;
  activeWorkspaceCount: number;
  departmentCount: number;
  activeQueueDefinitionCount: number;
  setupComplete: boolean;
  missingSections: string[];
  sourceProvenance: string | null;
  configVersion: number | null;
  lastConfigChangeAt: string | null;
}

export interface ChecklistItem {
  key: string;
  label: string;
  status: "COMPLETE" | "MISSING" | "PENDING" | "NOT_APPLICABLE" | string;
  required: boolean;
  detail: string | null;
}

export interface FacilitySetupChecklist {
  facilityId: number;
  configurationState: string;
  setupComplete: boolean;
  items: ChecklistItem[];
}

export interface ConfigServicePoint {
  id: string;
  facilityId: number;
  facilityUnitId: number | null;
  name: string;
  code: string | null;
  servicePointType: string;
  queueId: string | null;
  workflowArchetype: string | null;
  status: string;
  active: boolean;
}

export interface ConfigWorkspace {
  id: string;
  facilityId: number | null;
  name: string;
  workspaceType: string;
  active: boolean;
  description: string | null;
}

export interface ConfigQueueDefinition {
  sourceRef: string;
  displayName: string;
  queueType: string;
  servicePointType: string | null;
  workspaceId: string | null;
  priorityFlag: boolean;
  walkInSupported: boolean;
  appointmentSupported: boolean;
  active: boolean;
}

export interface DownstreamReadiness {
  facilityId: number;
  facilityUuid: string | null;
  activeQueueDefinitionCount: number;
  activeServicePointCount: number;
  lastConfigChangeAt: string | null;
  note: string;
}

export interface ConfigEvent {
  eventType: string;
  aggregateType: string;
  aggregateId: string;
  occurredAt: string | null;
  publishedAt: string | null;
}

export interface CreateServicePointInput {
  name: string;
  code?: string;
  servicePointType?: string;
  facilityUnitId?: number | null;
  queueId?: string;
  workflowArchetype?: string;
}

export interface UpdateServicePointInput {
  name?: string;
  code?: string;
  servicePointType?: string;
  status?: string;
  active?: boolean;
}

export interface CreateWorkspaceInput {
  name: string;
  workspaceType: string;
  description?: string;
}

export interface UpdateWorkspaceInput {
  name?: string;
  workspaceType?: string;
  description?: string;
}

// ── Reads ──────────────────────────────────────────────────────────────

export function useFacilityConfiguration(facilityId: string | undefined) {
  return useQuery<ApiResponse<FacilityConfigurationSummary>>({
    queryKey: ["facility-configuration", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<FacilityConfigurationSummary>>(`${BASE}/${facilityId}/configuration`),
    enabled: !!facilityId,
  });
}

export function useFacilitySetupChecklist(facilityId: string | undefined) {
  return useQuery<ApiResponse<FacilitySetupChecklist>>({
    queryKey: ["facility-setup-checklist", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<FacilitySetupChecklist>>(`${BASE}/${facilityId}/setup-checklist`),
    enabled: !!facilityId,
  });
}

export function useConfigServicePoints(facilityId: string | undefined) {
  return useQuery<ApiResponse<ConfigServicePoint[]>>({
    queryKey: ["facility-config-service-points", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<ConfigServicePoint[]>>(`${BASE}/${facilityId}/service-points`),
    enabled: !!facilityId,
  });
}

export function useConfigWorkspaces(facilityId: string | undefined) {
  return useQuery<ApiResponse<ConfigWorkspace[]>>({
    queryKey: ["facility-config-workspaces", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<ConfigWorkspace[]>>(`${BASE}/${facilityId}/workspaces`),
    enabled: !!facilityId,
  });
}

export function useFacilityQueueDefinitions(facilityId: string | undefined) {
  return useQuery<ApiResponse<ConfigQueueDefinition[]>>({
    queryKey: ["facility-queue-definitions", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<ConfigQueueDefinition[]>>(`${BASE}/${facilityId}/queue-definitions`),
    enabled: !!facilityId,
  });
}

export function useFacilityDownstreamReadiness(facilityId: string | undefined) {
  return useQuery<ApiResponse<DownstreamReadiness>>({
    queryKey: ["facility-downstream-readiness", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<DownstreamReadiness>>(`${BASE}/${facilityId}/downstream-readiness`),
    enabled: !!facilityId,
  });
}

export function useFacilityConfigHistory(facilityId: string | undefined) {
  return useQuery<ApiResponse<ConfigEvent[]>>({
    queryKey: ["facility-config-history", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<ConfigEvent[]>>(`${BASE}/${facilityId}/configuration/history`),
    enabled: !!facilityId,
  });
}

// ── Writes ─────────────────────────────────────────────────────────────

function useConfigInvalidation(facilityId: string | undefined) {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: ["facility-configuration", facilityId] });
    qc.invalidateQueries({ queryKey: ["facility-setup-checklist", facilityId] });
    qc.invalidateQueries({ queryKey: ["facility-config-service-points", facilityId] });
    qc.invalidateQueries({ queryKey: ["facility-config-workspaces", facilityId] });
    qc.invalidateQueries({ queryKey: ["facility-queue-definitions", facilityId] });
    qc.invalidateQueries({ queryKey: ["facility-downstream-readiness", facilityId] });
    qc.invalidateQueries({ queryKey: ["facility-config-history", facilityId] });
  };
}

export function useCreateServicePoint(facilityId: string | undefined) {
  const invalidate = useConfigInvalidation(facilityId);
  return useMutation({
    mutationFn: (input: CreateServicePointInput) =>
      apiClient.post<ApiResponse<ConfigServicePoint>>(`${BASE}/${facilityId}/service-points`, input),
    onSuccess: invalidate,
  });
}

export function useUpdateServicePoint(facilityId: string | undefined) {
  const invalidate = useConfigInvalidation(facilityId);
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateServicePointInput }) =>
      apiClient.patch<ApiResponse<ConfigServicePoint>>(`${BASE}/${facilityId}/service-points/${id}`, input),
    onSuccess: invalidate,
  });
}

export function useRetireServicePoint(facilityId: string | undefined) {
  const invalidate = useConfigInvalidation(facilityId);
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.post<ApiResponse<ConfigServicePoint>>(`${BASE}/${facilityId}/service-points/${id}/retire`),
    onSuccess: invalidate,
  });
}

export function useCreateWorkspace(facilityId: string | undefined) {
  const invalidate = useConfigInvalidation(facilityId);
  return useMutation({
    mutationFn: (input: CreateWorkspaceInput) =>
      apiClient.post<ApiResponse<ConfigWorkspace>>(`${BASE}/${facilityId}/workspaces`, input),
    onSuccess: invalidate,
  });
}

export function useUpdateWorkspace(facilityId: string | undefined) {
  const invalidate = useConfigInvalidation(facilityId);
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateWorkspaceInput }) =>
      apiClient.patch<ApiResponse<ConfigWorkspace>>(`${BASE}/${facilityId}/workspaces/${id}`, input),
    onSuccess: invalidate,
  });
}

export function useRetireWorkspace(facilityId: string | undefined) {
  const invalidate = useConfigInvalidation(facilityId);
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.post<ApiResponse<ConfigWorkspace>>(`${BASE}/${facilityId}/workspaces/${id}/retire`),
    onSuccess: invalidate,
  });
}

export function usePublishConfiguration(facilityId: string | undefined) {
  const invalidate = useConfigInvalidation(facilityId);
  return useMutation({
    mutationFn: () =>
      apiClient.post<ApiResponse<FacilityConfigurationSummary>>(`${BASE}/${facilityId}/configuration/publish`),
    onSuccess: invalidate,
  });
}
