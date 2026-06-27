import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  buildOnboardingPrecheck,
  createFacilityStaffAssignment,
  decideAccessRequest,
  listAccessRequests,
  listAdminGovernanceAudit,
  runOpaPrecheck,
  submitDomainGovernanceAction,
  submitOnboardingFlow,
} from "@/lib/admin-governance/api";
import type {
  AdminGovernanceActionResponse,
  FacilityStaffAssignmentRequest,
  OnboardingFlowSubmission,
  OpaPrecheckRequest,
} from "@/lib/admin-governance/types";
import { isActionResponse } from "@/lib/admin-governance/api/client";
import { listGdhcnReadiness } from "@/lib/admin-governance/api/gdhcnReadinessApi";

export function useAdminAccessRequests() {
  return useQuery({
    queryKey: ["admin-governance", "access-requests"],
    queryFn: listAccessRequests,
    staleTime: 30_000,
  });
}

export function useGdhcnReadiness() {
  return useQuery({
    queryKey: ["admin-governance", "gdhcn-readiness"],
    queryFn: listGdhcnReadiness,
    staleTime: 30_000,
  });
}

export function useAdminGovernanceAudit() {
  return useQuery({
    queryKey: ["admin-governance", "audit"],
    queryFn: listAdminGovernanceAudit,
    staleTime: 30_000,
  });
}

export function useOpaPrecheck() {
  return useMutation<AdminGovernanceActionResponse, unknown, OpaPrecheckRequest>({
    mutationFn: runOpaPrecheck,
  });
}

export function useSubmitOnboardingFlow(flowId: string) {
  const queryClient = useQueryClient();
  return useMutation<AdminGovernanceActionResponse, unknown, OnboardingFlowSubmission>({
    mutationFn: (submission) => submitOnboardingFlow(flowId, submission),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-governance", "access-requests"] });
    },
  });
}

export function useOnboardingPrecheck(flowId: string) {
  return useMutation<AdminGovernanceActionResponse, unknown, OnboardingFlowSubmission>({
    mutationFn: (submission) => runOpaPrecheck(buildOnboardingPrecheck(flowId, submission)),
  });
}

export function useDecideAccessRequest() {
  const queryClient = useQueryClient();
  return useMutation<
    AdminGovernanceActionResponse,
    unknown,
    { requestId: string; decision: "approve" | "reject" | "request-more-information" | "escalate" | "revoke"; notes?: string }
  >({
    mutationFn: ({ requestId, decision, notes }) => decideAccessRequest(requestId, decision, notes),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-governance", "access-requests"] });
    },
  });
}

export function useCreateFacilityStaffAssignment() {
  const queryClient = useQueryClient();
  return useMutation<AdminGovernanceActionResponse, unknown, FacilityStaffAssignmentRequest>({
    mutationFn: createFacilityStaffAssignment,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-governance", "access-requests"] });
    },
  });
}

export function useDomainGovernanceAction(action: string) {
  const queryClient = useQueryClient();
  return useMutation<AdminGovernanceActionResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) => submitDomainGovernanceAction(action, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-governance", "access-requests"] });
    },
  });
}

export function isAccessRequestList(value: unknown): value is import("@/lib/admin-governance/types").AccessRequest[] {
  return Array.isArray(value);
}

export function isGovernanceActionResult(value: unknown): value is AdminGovernanceActionResponse {
  return isActionResponse(value);
}
