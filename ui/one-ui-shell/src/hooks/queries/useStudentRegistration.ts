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

export interface StudentApplicationSummary {
  id: number;
  applicationType: string;
  workflowState: string;
  reviewState: string | null;
  feeState: string | null;
  submittedAt: string | null;
  closedAt: string | null;
  councilId: number | null;
  councilCode: string | null;
  providerId: number | null;
  providerPublicId: string | null;
}

export interface AdmitStudentBody {
  councilId: number;
  providerId: number;
  registerCode: string;
  policyKey: string;
  feeCode: string;
}

export interface AdmitStudentResult {
  indexNumber: string;
  registerEntryId: number;
  registerCode: string;
  effectiveFrom: string;
}

/** Regulator queue — student registration applications for one council / organisation. */
export function useStudentApplications(args: {
  organizationId?: string;
  councilId?: string | number;
  states?: string;
}) {
  const { organizationId, councilId, states } = args;
  return useQuery<StudentApplicationSummary[]>({
    queryKey: ["student-applications", organizationId, councilId, states],
    enabled: Boolean(organizationId || councilId),
    queryFn: async () => {
      const params = new URLSearchParams();
      if (organizationId) params.set("organizationId", organizationId);
      if (councilId != null && String(councilId)) params.set("councilId", String(councilId));
      if (states) params.set("states", states);
      return apiClient.get<StudentApplicationSummary[]>(`${BASE}/applications?${params}`);
    },
  });
}

export function useStudentApplication(applicationId: string | undefined) {
  return useQuery<StudentApplicationSummary>({
    queryKey: ["student-application", applicationId],
    enabled: Boolean(applicationId),
    queryFn: async () =>
      apiClient.get<StudentApplicationSummary>(
        `${BASE}/applications/${encodeURIComponent(String(applicationId))}`,
      ),
  });
}

export function useAdmitStudentApplication(applicationId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: AdmitStudentBody) =>
      apiClient.post<AdmitStudentResult>(
        `${BASE}/applications/${encodeURIComponent(String(applicationId))}/admit`,
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["student-application", applicationId] });
      void queryClient.invalidateQueries({ queryKey: ["student-application-sections", applicationId] });
      void queryClient.invalidateQueries({ queryKey: ["student-applications"] });
    },
  });
}

export function useStudentRegistrationReport(
  report:
    | "regulator-board"
    | "ageing"
    | "returns-by-section"
    | "institution-board",
  args: { councilId?: string | number; organisationId?: string },
) {
  const { councilId, organisationId } = args;
  const enabled =
    report === "institution-board" ? Boolean(organisationId) : Boolean(councilId);
  return useQuery<unknown>({
    queryKey: ["student-registration-report", report, councilId, organisationId],
    enabled,
    queryFn: async () => {
      const params = new URLSearchParams();
      if (report === "institution-board") {
        params.set("organisationId", String(organisationId));
      } else {
        params.set("councilId", String(councilId));
      }
      return apiClient.get(`${BASE}/reports/${report}?${params}`);
    },
  });
}
