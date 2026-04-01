import { useMutation, useQueryClient } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type {
  Client,
  IdentityRegisterPayload,
  IdentityResolvePayload,
  IdentityResolveResponse,
  IdentityRotateResponse,
} from "@/types/vito";
import { clientKeys } from "./useClients";

export function useRegisterIdentity() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: IdentityRegisterPayload) =>
      vitoApiClient.post<Client>("/v1/identity/register", payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: clientKeys.all });
    },
  });
}

export function useResolveIdentity() {
  return useMutation({
    mutationFn: (payload: IdentityResolvePayload) =>
      vitoApiClient.post<IdentityResolveResponse>("/v1/identity/resolve", payload),
  });
}

export function useRotateCpid() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ healthId, reason }: { healthId: string; reason?: string }) =>
      vitoApiClient.post<IdentityRotateResponse>("/v1/identity/rotate", { healthId, reason }),
    onSuccess: (_, { healthId }) => {
      qc.invalidateQueries({ queryKey: clientKeys.detail(healthId) });
      qc.invalidateQueries({ queryKey: clientKeys.all });
    },
  });
}
