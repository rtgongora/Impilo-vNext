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
