/**
 * Experience UI — Referrals Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ReferralResource {
  id: string;
  type: "referral";
  attributes: {
    patientId: string;
    encounterId: string;
    referralType: string;
    specialty: string;
    referredTo: string;
    referredToFacility: string;
    reason: string;
    urgency: string;
    status: string;
    clinicalSummary: string | null;
    referredBy: string;
    referredByName: string;
    scheduledAt: string | null;
    completedAt: string | null;
    outcome: string | null;
    createdAt: string;
  };
}

interface CreateReferralPayload {
  patientId: string;
  encounterId: string;
  referralType: string;
  specialty: string;
  referredTo: string;
  referredToFacility: string;
  reason: string;
  urgency: string;
  clinicalSummary?: string | null;
  [key: string]: unknown;
}

interface CompleteReferralPayload {
  id: string;
  outcome?: string | null;
  [key: string]: unknown;
}

type ReferralsResponse = ApiResponse<ReferralResource[]>;
type ReferralResponse = ApiResponse<ReferralResource>;

export function useReferrals(patientId: string) {
  return useQuery<ReferralsResponse>({
    queryKey: ["referrals", { patientId }],
    queryFn: () =>
      apiClient.get<ReferralsResponse>(
        `/internal/v1/referrals?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}

export function useCreateReferral() {
  const queryClient = useQueryClient();

  return useMutation<ReferralResponse, unknown, CreateReferralPayload>({
    mutationFn: (payload: CreateReferralPayload) =>
      apiClient.post<ReferralResponse>("/internal/v1/referrals", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["referrals"] });
    },
  });
}

export function useCompleteReferral() {
  const queryClient = useQueryClient();

  return useMutation<ReferralResponse, unknown, CompleteReferralPayload>({
    mutationFn: ({ id, ...rest }: CompleteReferralPayload) =>
      apiClient.post<ReferralResponse>(`/internal/v1/referrals/${id}/complete`, rest),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["referrals"] });
    },
  });
}
