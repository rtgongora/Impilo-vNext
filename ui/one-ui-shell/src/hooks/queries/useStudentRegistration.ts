/**
 * The student registration journey, all three sides (NCZ-W1C).
 *
 * The contributor hooks take an INVITATION id and have no way to name an application — the same
 * boundary the service and the BFF hold. A convenience overload that accepted an application id
 * would put a training institution one bug away from a student's identity documents.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type SectionState = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETE" | "RETURNED";

export interface ApplicationSection {
  sectionKey: string;
  label: string;
  sequenceNo: number;
  state: SectionState;
  content: Record<string, unknown> | null;
  completedAt: string | null;
  /** Why a regulator sent this section back. Present exactly when state is RETURNED. */
  returnedReason: string | null;
  returnedAt: string | null;
  returnCount: number;
}

export type FeeVerdict = "PAYABLE" | "NOT_CONFIGURED" | "NO_SUCH_FEE";

export interface FeeVerdictResponse {
  verdict: FeeVerdict;
  amount: number | null;
  currency: string | null;
  /** The council decision an unset fee is waiting on, e.g. NCZ-DEC-002. */
  decisionRef: string | null;
  chargeable: boolean;
  explanation: string;
}

const BASE = "/api/v1/student-registration";

export function useApplicationSections(applicationId: string | undefined) {
  return useQuery<ApplicationSection[]>({
    queryKey: ["student-application-sections", applicationId],
    enabled: Boolean(applicationId),
    queryFn: async () =>
      apiClient.get<ApplicationSection[]>(
        `${BASE}/applications/${encodeURIComponent(String(applicationId))}/sections`,
      ),
  });
}

/** A contributor sees only what their invitation covers — the API cannot return more. */
export function useContributionSections(inviteId: string | undefined) {
  return useQuery<ApplicationSection[]>({
    queryKey: ["student-contribution-sections", inviteId],
    enabled: Boolean(inviteId),
    queryFn: async () =>
      apiClient.get<ApplicationSection[]>(
        `${BASE}/contributions/${encodeURIComponent(String(inviteId))}/sections`,
      ),
  });
}

export function useCompleteContribution(inviteId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { sectionKey: string; content: Record<string, unknown> }) =>
      apiClient.post(
        `${BASE}/contributions/${encodeURIComponent(String(inviteId))}/sections/${encodeURIComponent(vars.sectionKey)}`,
        vars.content,
      ),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["student-contribution-sections", inviteId] }),
  });
}

export function useResubmitSection(applicationId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { sectionKey: string; content: Record<string, unknown> }) =>
      apiClient.post(
        `${BASE}/applications/${encodeURIComponent(String(applicationId))}/sections/${encodeURIComponent(vars.sectionKey)}/resubmit`,
        vars.content,
      ),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["student-application-sections", applicationId] }),
  });
}

export function useReturnSection(applicationId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { sectionKey: string; reason: string }) =>
      apiClient.post(
        `${BASE}/applications/${encodeURIComponent(String(applicationId))}/sections/${encodeURIComponent(vars.sectionKey)}/return`,
        { reason: vars.reason },
      ),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["student-application-sections", applicationId] }),
  });
}

/**
 * What the fee gate says. Read BEFORE a decision so the regulator sees "awaiting NCZ-DEC-002"
 * rather than discovering it when the admission fails.
 */
export function useFeeVerdict(councilId: string | undefined, feeCode: string) {
  return useQuery<FeeVerdictResponse>({
    queryKey: ["student-fee-verdict", councilId, feeCode],
    enabled: Boolean(councilId),
    queryFn: async () =>
      apiClient.get<FeeVerdictResponse>(
        `${BASE}/fee-verdict?councilId=${encodeURIComponent(String(councilId))}&feeCode=${encodeURIComponent(feeCode)}`,
      ),
  });
}
