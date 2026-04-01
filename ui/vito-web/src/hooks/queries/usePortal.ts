import { useQuery, useMutation } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type {
  Client,
  IssuanceRequest,
  DelegatedPickup,
  GenericResponse,
  PortalIdRequestPayload,
  PortalRequestIdResponse,
  PortalQrResponse,
  RecoveryStartPayload,
  RecoveryVerifyPayload,
  RecoveryVerifyResponse,
  DelegatedPickupCreatePayload,
  DelegatedPickupRedeemPayload,
} from "@/types/vito";

export const portalKeys = {
  all: ["portal"] as const,
  me: () => [...portalKeys.all, "me"] as const,
  qr: () => [...portalKeys.all, "qr"] as const,
};

export function usePortalMe() {
  return useQuery({
    queryKey: portalKeys.me(),
    queryFn: () => vitoApiClient.get<Client>("/v1/portal/me"),
    staleTime: 30_000,
  });
}

export function useRequestId() {
  return useMutation({
    mutationFn: (payload: PortalIdRequestPayload) =>
      vitoApiClient.post<PortalRequestIdResponse>("/v1/portal/id/request", payload),
  });
}

export function useHealthIdQr() {
  return useQuery({
    queryKey: portalKeys.qr(),
    queryFn: () => vitoApiClient.get<PortalQrResponse>("/v1/portal/health-id/qr"),
    staleTime: 30_000,
  });
}

export function useStartRecovery() {
  return useMutation({
    mutationFn: (payload: RecoveryStartPayload) =>
      vitoApiClient.post<GenericResponse>("/v1/portal/id/recovery/start", payload),
  });
}

export function useVerifyRecovery() {
  return useMutation({
    mutationFn: (payload: RecoveryVerifyPayload) =>
      vitoApiClient.post<RecoveryVerifyResponse>("/v1/portal/id/recovery/verify", payload),
  });
}

export function useCreatePickup() {
  return useMutation({
    mutationFn: (payload: DelegatedPickupCreatePayload) =>
      vitoApiClient.post<DelegatedPickup>("/v1/portal/delegated-pickup/create", payload),
  });
}

export function useRedeemPickup() {
  return useMutation({
    mutationFn: (payload: DelegatedPickupRedeemPayload) =>
      vitoApiClient.post<GenericResponse>("/v1/portal/delegated-pickup/redeem", payload),
  });
}
