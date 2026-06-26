/**
 * Delegated-access (L5) hooks — read/revoke Mvumo delegation relationships via the BFF proxy
 * (/internal/v1/mvumo/delegations/**). Closes G-CZO-03's UI surface: a delegate sees who they may
 * act for; a subject sees (and can revoke) who may act for them.
 */
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface DelegationRelationship {
  id: string;
  delegatorSubjectRef: string;
  delegateActorId: string;
  relationshipType: string;
  legalBasis: string;
  scope: string[];
  minDelegateLoa: number;
  status: string;
  startsAt?: string;
  expiresAt?: string;
}

/** Relationships where the current user is the delegate (their "acting for" list). */
export function useDelegationsAsDelegate(actorId: string | undefined) {
  return useQuery<ApiResponse<DelegationRelationship[]>>({
    queryKey: ["delegations", "delegate", actorId],
    enabled: Boolean(actorId),
    queryFn: () =>
      apiClient.get<ApiResponse<DelegationRelationship[]>>(
        `/internal/v1/mvumo/delegations/delegate/${encodeURIComponent(actorId!)}`,
      ),
  });
}

/** Relationships where the current user is the subject ("who can act for me"). */
export function useDelegationsAsSubject(subjectRef: string | undefined) {
  return useQuery<ApiResponse<DelegationRelationship[]>>({
    queryKey: ["delegations", "subject", subjectRef],
    enabled: Boolean(subjectRef),
    queryFn: () =>
      apiClient.get<ApiResponse<DelegationRelationship[]>>(
        `/internal/v1/mvumo/delegations/subject/${encodeURIComponent(subjectRef!)}`,
      ),
  });
}

/** A subject revokes a delegation (always available to the subject). */
export function useRevokeDelegation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
      apiClient.post(`/internal/v1/mvumo/delegations/${id}/revoke`, { reason }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["delegations"] });
    },
  });
}
