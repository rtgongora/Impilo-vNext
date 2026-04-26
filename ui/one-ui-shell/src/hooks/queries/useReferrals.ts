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
    receivingFacilityId: string | null;
    receivingFacilityName: string | null;
    responseNotes: string | null;
    respondedAt: string | null;
    acceptedAt: string | null;
    scheduledAt: string | null;
    completedAt: string | null;
    outcome: string | null;
    createdAt: string;
    // snake_case aliases from API response
    receiving_facility_id?: string | null;
    receiving_facility_name?: string | null;
    response_notes?: string | null;
    responded_at?: string | null;
    accepted_at?: string | null;
    encounter_id?: string;
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

interface AcceptReferralPayload {
  id: string;
  receiving_facility_id?: string;
  receiving_facility_name?: string;
  scheduled_at?: string;
  notes?: string;
}

interface RespondReferralPayload {
  id: string;
  response_notes: string;
  outcome?: string;
}

export function useAcceptReferral() {
  const queryClient = useQueryClient();

  return useMutation<ReferralResponse, unknown, AcceptReferralPayload>({
    mutationFn: ({ id, ...rest }: AcceptReferralPayload) =>
      apiClient.post<ReferralResponse>(`/internal/v1/referrals/${id}/accept`, rest),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["referrals"] });
    },
  });
}

export function useRespondReferral() {
  const queryClient = useQueryClient();

  return useMutation<ReferralResponse, unknown, RespondReferralPayload>({
    mutationFn: ({ id, ...rest }: RespondReferralPayload) =>
      apiClient.post<ReferralResponse>(`/internal/v1/referrals/${id}/respond`, rest),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["referrals"] });
    },
  });
}
