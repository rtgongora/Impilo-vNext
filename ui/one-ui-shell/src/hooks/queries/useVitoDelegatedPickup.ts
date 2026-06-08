/**
 * VITO delegated pickup — ops/citizen handoff for card collection.
 */
import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface CreateDelegatedPickupRequest {
  issuanceRequestId: number;
  delegateName: string;
  delegateIdRef?: string;
  delegateContact?: string;
  facilityId: string;
  facilityName?: string;
  expiryHours?: number;
}

export interface CreateDelegatedPickupResult {
  pickupId: number;
  pickupToken: string;
  otp: string;
  expiresAt: string;
  message: string;
}

export function useCreateDelegatedPickup() {
  return useMutation({
    mutationFn: (body: CreateDelegatedPickupRequest) =>
      apiClient.post<CreateDelegatedPickupResult>("/internal/v1/vito/delegated-pickup/create", {
        issuanceRequestId: body.issuanceRequestId,
        delegateName: body.delegateName,
        delegateIdRef: body.delegateIdRef ?? "",
        delegateContact: body.delegateContact ?? "{}",
        facilityId: body.facilityId,
        facilityName: body.facilityName,
        expiryHours: body.expiryHours ?? 72,
      }),
  });
}
