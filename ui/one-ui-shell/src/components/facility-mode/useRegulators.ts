import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

interface BffEnvelope<T> {
  data: T;
  meta?: Record<string, unknown>;
}

export interface RegulatorRelationship {
  id: string;
  facilityId: string;
  councilId: string;
  councilCode: string;
  relationshipType: string;
  registrationNumber: string | null;
  status: string;
  validFrom: string | null;
  validTo: string | null;
}

export function useFacilityRegulators(facilityId: string) {
  return useQuery({
    queryKey: ["facility-regulators", facilityId],
    enabled: !!facilityId,
    queryFn: async () =>
      (await apiClient.get<BffEnvelope<RegulatorRelationship[]>>(
        `/internal/v1/facility-mode/${encodeURIComponent(facilityId)}/regulators`,
      )).data,
  });
}

export function useLinkRegulator(facilityId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: {
      councilId: string;
      councilCode: string;
      relationshipType?: string;
      registrationNumber?: string;
    }) =>
      (await apiClient.post<BffEnvelope<RegulatorRelationship>>(
        `/internal/v1/facility-mode/${encodeURIComponent(facilityId)}/regulators`,
        input,
      )).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["facility-regulators", facilityId] }),
  });
}
