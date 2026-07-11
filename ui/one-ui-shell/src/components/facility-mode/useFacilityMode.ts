import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type {
  FacilityModeContextEnvelope,
  FacilitySetupStepState,
} from "./types";

interface BffEnvelope<T> {
  data: T;
  meta?: Record<string, unknown>;
}

/** Facility Mode cockpit context (TUSO C4 read-model, composed by the BFF). */
export function useFacilityModeContext(facilityId: string | number, pctFacilityId?: string) {
  return useQuery({
    queryKey: ["facility-mode-context", String(facilityId), pctFacilityId ?? null],
    enabled: facilityId != null && facilityId !== "",
    queryFn: async () => {
      const qs = pctFacilityId ? `?pctFacilityId=${encodeURIComponent(pctFacilityId)}` : "";
      const resp = await apiClient.get<BffEnvelope<FacilityModeContextEnvelope>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/context${qs}`,
      );
      return resp.data;
    },
  });
}

/** Setup-wizard state for a facility. */
export function useFacilitySetupState(facilityId: string | number) {
  return useQuery({
    queryKey: ["facility-setup-state", String(facilityId)],
    enabled: facilityId != null && facilityId !== "",
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<FacilitySetupStepState>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/setup`,
      );
      return resp.data;
    },
  });
}

export interface FacilityUnit {
  id: number;
  name: string;
  unitType: string;
  serviceLine: string | null;
  registrationRequired: boolean;
  regulatoryStatus: string | null;
  certificateStatus: string | null;
}

export interface ServicePoint {
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

/** Facility units (departments). */
export function useFacilityUnits(facilityId: string | number) {
  return useQuery({
    queryKey: ["facility-units", String(facilityId)],
    enabled: facilityId != null && facilityId !== "",
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<FacilityUnit[]>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/units`,
      );
      return resp.data;
    },
  });
}

/** Create a department. */
export function useCreateFacilityUnit(facilityId: string | number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: {
      name: string;
      unitType?: string;
      serviceLine?: string;
      registrationRequired?: boolean;
    }) => {
      const resp = await apiClient.post<BffEnvelope<FacilityUnit>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/units`,
        input,
      );
      return resp.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["facility-units", String(facilityId)] });
      qc.invalidateQueries({ queryKey: ["facility-setup-state", String(facilityId)] });
    },
  });
}

/** Service points for a facility. */
export function useServicePoints(facilityId: string | number) {
  return useQuery({
    queryKey: ["facility-service-points", String(facilityId)],
    enabled: facilityId != null && facilityId !== "",
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<ServicePoint[]>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/service-points`,
      );
      return resp.data;
    },
  });
}

/** Create a service point. */
export function useCreateServicePoint(facilityId: string | number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: {
      name: string;
      code?: string;
      servicePointType?: string;
      facilityUnitId?: number;
      queueId?: string;
      workflowArchetype?: string;
    }) => {
      const resp = await apiClient.post<BffEnvelope<ServicePoint>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/service-points`,
        input,
      );
      return resp.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["facility-service-points", String(facilityId)] });
      qc.invalidateQueries({ queryKey: ["facility-setup-state", String(facilityId)] });
    },
  });
}

/** Update a department (partial). */
export function useUpdateFacilityUnit(facilityId: string | number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: {
      unitId: number;
      name?: string;
      unitType?: string;
      serviceLine?: string;
    }) => {
      const { unitId, ...body } = input;
      const resp = await apiClient.patch<BffEnvelope<FacilityUnit>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/units/${unitId}`,
        body,
      );
      return resp.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["facility-units", String(facilityId)] });
    },
  });
}

/** Retire a department (administrative closure — governed closures stay with the regulator). */
export function useRetireFacilityUnit(facilityId: string | number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (unitId: number) => {
      const resp = await apiClient.post<BffEnvelope<FacilityUnit>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/units/${unitId}/retire`,
        {},
      );
      return resp.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["facility-units", String(facilityId)] });
    },
  });
}

export interface FacilityCapability {
  id: number;
  capabilityCode: string;
  capabilityType: string;
  name: string;
  ziboValidated: boolean;
  active: boolean;
  operatingHours: Record<string, unknown> | null;
  metadata: Record<string, unknown> | null;
}

export interface FacilityReadiness {
  connectivity: string | null;
  powerSource: string | null;
  powerBackup: boolean;
  deviceCount: number;
  ehrReady: boolean;
  complianceFlags: Record<string, unknown> | null;
  assessedAt: string | null;
  assessedBy: string | null;
}

/** Curated facility capabilities ("services offered" registry truth). */
export function useFacilityCapabilities(facilityId: string | number) {
  return useQuery({
    queryKey: ["facility-capabilities", String(facilityId)],
    enabled: facilityId != null && facilityId !== "",
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<FacilityCapability[]>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/capabilities`,
      );
      return resp.data;
    },
  });
}

export function useCreateFacilityCapability(facilityId: string | number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: {
      capabilityCode: string;
      name?: string;
      capabilityType?: string;
    }) => {
      const resp = await apiClient.post<BffEnvelope<FacilityCapability>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/capabilities`,
        input,
      );
      return resp.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["facility-capabilities", String(facilityId)] });
    },
  });
}

export function useUpdateFacilityCapability(facilityId: string | number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: {
      capabilityId: number;
      name?: string;
      capabilityType?: string;
      active?: boolean;
    }) => {
      const { capabilityId, ...body } = input;
      const resp = await apiClient.patch<BffEnvelope<FacilityCapability>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/capabilities/${capabilityId}`,
        body,
      );
      return resp.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["facility-capabilities", String(facilityId)] });
    },
  });
}

export function useRetireFacilityCapability(facilityId: string | number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (capabilityId: number) => {
      const resp = await apiClient.post<BffEnvelope<FacilityCapability>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/capabilities/${capabilityId}/retire`,
        {},
      );
      return resp.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["facility-capabilities", String(facilityId)] });
    },
  });
}

/** Facility readiness (infrastructure assessment). */
export function useFacilityReadiness(facilityId: string | number) {
  return useQuery({
    queryKey: ["facility-readiness", String(facilityId)],
    enabled: facilityId != null && facilityId !== "",
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<FacilityReadiness | null>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/readiness`,
      );
      return resp.data;
    },
  });
}

export function useUpsertFacilityReadiness(facilityId: string | number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: {
      connectivity?: string;
      powerSource?: string;
      powerBackup?: boolean;
      deviceCount?: number;
      ehrReady?: boolean;
    }) => {
      const resp = await apiClient.put<BffEnvelope<FacilityReadiness>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/readiness`,
        input,
      );
      return resp.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["facility-readiness", String(facilityId)] });
    },
  });
}

/** Advance a single setup-wizard step. Surfaces TUSO's honest go-live rejection. */
export function useAdvanceSetupStep(facilityId: string | number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: { step: string; complete: boolean }) => {
      const resp = await apiClient.post<BffEnvelope<FacilitySetupStepState>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/setup/steps`,
        input,
      );
      return resp.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["facility-setup-state", String(facilityId)] });
      qc.invalidateQueries({ queryKey: ["facility-mode-context", String(facilityId)] });
    },
  });
}
