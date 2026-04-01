import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type { BiometricMetadata, BiometricEnrollPayload, GenericResponse } from "@/types/vito";

export const biometricKeys = {
  all: ["biometric"] as const,
  templates: (healthId: string) => [...biometricKeys.all, "templates", healthId] as const,
};

export function useBiometricTemplates(healthId: string) {
  return useQuery({
    queryKey: biometricKeys.templates(healthId),
    queryFn: () => vitoApiClient.get<BiometricMetadata>(`/v1/biometric/${healthId}`),
    enabled: !!healthId,
    staleTime: 30_000,
  });
}

export function useEnrollBiometric() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: BiometricEnrollPayload) =>
      vitoApiClient.post<GenericResponse>("/v1/biometric/enroll", payload),
    onSuccess: (_, payload) => {
      qc.invalidateQueries({ queryKey: biometricKeys.templates(payload.healthId) });
    },
  });
}
