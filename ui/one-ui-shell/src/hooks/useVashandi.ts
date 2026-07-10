import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { listWorkforceProfiles, getWorkforceProfile, reconcileWorkforceProfile, importWorkforceBridge, runVashandiPrecheck, profilesFromResponse, type ImportBridgeRequest } from "@/lib/vashandi/api/profiles";
import {
  listAssignments,
  getAssignment,
  createAssignment,
  precheckAssignment,
  approveAssignment,
  activateAssignment,
  suspendAssignment,
  endAssignment,
  assignmentsFromResponse,
} from "@/lib/vashandi/api/assignments";
import {
  listRosters,
  listRosterShifts,
  createRoster,
  approveRoster,
  createShift,
  updateShift,
  rostersFromResponse,
  shiftsFromResponse,
} from "@/lib/vashandi/api/rosters";
import { listAttendance, checkIn, adhocCheckIn, checkOut, supervisorConfirmAttendance, attendanceFromResponse } from "@/lib/vashandi/api/attendance";
import {
  listAvailability,
  createLeave,
  updateLeave,
  listLeaveTypes,
  listLeaveBalances,
  leaveFromResponse,
  leaveTypesFromResponse,
  leaveBalancesFromResponse,
} from "@/lib/vashandi/api/leave";
import {
  listAccessRisks,
  scanAccessRisks,
  resolveAccessRisk,
  revokeAccessForRisk,
  accessRisksFromResponse,
} from "@/lib/vashandi/api/accessReview";
import {
  getStaffingAnalytics,
  getRosterCoverageAnalytics,
  getAttendanceAnalytics,
  getAccessRiskAnalytics,
  staffingFromResponse,
  rosterCoverageFromResponse,
  attendanceAnalyticsFromResponse,
  accessRiskAnalyticsFromResponse,
} from "@/lib/vashandi/api/analytics";
import {
  listTrainingRequirements,
  createTrainingRequirement,
  deactivateTrainingRequirement,
  trainingRequirementsFromResponse,
} from "@/lib/vashandi/api/trainingRequirements";
import type { CheckInRequest, CheckOutRequest, CreateAssignmentRequest, VashandiActionResponse } from "@/lib/vashandi/types";
import { isUpstreamUnavailable } from "@/lib/vashandi/api/client";

export function useWorkforceProfiles(params?: Record<string, string | undefined>) {
  return useQuery({
    queryKey: ["vashandi", "workforce-profiles", params],
    queryFn: async () => {
      const response = await listWorkforceProfiles(params);
      return { response, items: profilesFromResponse(response) };
    },
    staleTime: 30_000,
  });
}

export function useWorkforceProfile(id: string) {
  return useQuery({
    queryKey: ["vashandi", "workforce-profile", id],
    queryFn: () => getWorkforceProfile(id),
    enabled: Boolean(id),
    staleTime: 30_000,
  });
}

export function useReconcileWorkforceProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (profileId: string) => reconcileWorkforceProfile(profileId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "workforce-profiles"] });
    },
  });
}

export function useWorkforceImportBridge() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ImportBridgeRequest) => importWorkforceBridge(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "workforce-profiles"] });
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "assignments"] });
    },
  });
}

export function useVashandiPrecheck() {
  return useMutation({ mutationFn: runVashandiPrecheck });
}

export function useAssignments(params?: Record<string, string | undefined>) {
  return useQuery({
    queryKey: ["vashandi", "assignments", params],
    queryFn: async () => {
      const response = await listAssignments(params);
      return { response, items: assignmentsFromResponse(response) };
    },
    staleTime: 30_000,
  });
}

export function useAssignment(id: string) {
  return useQuery({
    queryKey: ["vashandi", "assignment", id],
    queryFn: () => getAssignment(id),
    enabled: Boolean(id),
    staleTime: 30_000,
  });
}

export function useCreateAssignment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateAssignmentRequest) => createAssignment(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "assignments"] });
    },
  });
}

export function usePrecheckAssignment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ assignmentId, body }: { assignmentId: string; body?: Record<string, unknown> }) =>
      precheckAssignment(assignmentId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "assignments"] });
    },
  });
}

function useAssignmentAction(
  action: (id: string, body?: Record<string, unknown>) => Promise<import("@/lib/vashandi/types").VashandiActionResponse>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ assignmentId, body }: { assignmentId: string; body?: Record<string, unknown> }) =>
      action(assignmentId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "assignments"] });
    },
  });
}

export function useApproveAssignment() {
  return useAssignmentAction(approveAssignment);
}

export function useActivateAssignment() {
  return useAssignmentAction(activateAssignment);
}

export function useSuspendAssignment() {
  return useAssignmentAction(suspendAssignment);
}

export function useEndAssignment() {
  return useAssignmentAction(endAssignment);
}

export function useRosters(params?: Record<string, string | undefined>) {
  return useQuery({
    queryKey: ["vashandi", "rosters", params],
    queryFn: async () => {
      const response = await listRosters(params);
      return { response, items: rostersFromResponse(response) };
    },
    staleTime: 30_000,
  });
}

export function useRosterShifts(rosterId: string) {
  return useQuery({
    queryKey: ["vashandi", "roster-shifts", rosterId],
    queryFn: async () => {
      const response = await listRosterShifts(rosterId);
      return { response, items: shiftsFromResponse(response) };
    },
    enabled: Boolean(rosterId),
    staleTime: 15_000,
  });
}

export function useCreateRoster() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => createRoster(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "rosters"] });
    },
  });
}

export function useApproveRoster() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (rosterId: string) => approveRoster(rosterId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "rosters"] });
    },
  });
}

export function useCreateShift() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => createShift(body),
    onSuccess: (_data, body) => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "roster-shifts", String(body.rosterId ?? "")] });
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "rosters"] });
    },
  });
}

export function useUpdateShift() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ shiftId, body }: { shiftId: string; body: Record<string, unknown> }) =>
      updateShift(shiftId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "roster-shifts"] });
    },
  });
}

export function useAttendance(params?: Record<string, string | undefined>) {
  return useQuery({
    queryKey: ["vashandi", "attendance", params],
    queryFn: async () => {
      const response = await listAttendance(params);
      return { response, items: attendanceFromResponse(response) };
    },
    staleTime: 30_000,
  });
}

export function useCheckIn() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CheckInRequest) => checkIn(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "attendance"] });
    },
  });
}

export function useAdhocCheckIn() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { workforceProfileId: string; facilityId?: string; checkInMode?: string }) => adhocCheckIn(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "attendance"] });
    },
  });
}

export function useCheckOut() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CheckOutRequest) => checkOut(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "attendance"] });
    },
  });
}

export function useSupervisorConfirmAttendance() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => supervisorConfirmAttendance(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "attendance"] });
    },
  });
}

export function useTrainingRequirements(params?: Record<string, string | undefined>) {
  return useQuery({
    queryKey: ["vashandi", "training-requirements", params],
    queryFn: async () => {
      const response = await listTrainingRequirements(params);
      return { response, items: trainingRequirementsFromResponse(response) };
    },
    staleTime: 30_000,
  });
}

export function useCreateTrainingRequirement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { roleTemplateId: string; courseCode: string; enforcementLevel?: string; note?: string }) =>
      createTrainingRequirement(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "training-requirements"] });
    },
  });
}

export function useDeactivateTrainingRequirement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deactivateTrainingRequirement(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "training-requirements"] });
    },
  });
}

export function useLeaveAvailability(params?: Record<string, string | undefined>) {
  return useQuery({
    queryKey: ["vashandi", "leave", params],
    queryFn: async () => {
      const response = await listAvailability(params);
      return { response, items: leaveFromResponse(response) };
    },
    staleTime: 30_000,
  });
}

export function useCreateLeave() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => createLeave(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "leave"] });
    },
  });
}

export function useUpdateLeave() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ leaveId, body }: { leaveId: string; body: Record<string, unknown> }) =>
      updateLeave(leaveId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "leave"] });
    },
  });
}

export function useLeaveTypes() {
  return useQuery({
    queryKey: ["vashandi", "leave", "types"],
    queryFn: async () => {
      const response = await listLeaveTypes();
      return { response, items: leaveTypesFromResponse(response) };
    },
    staleTime: 60_000,
  });
}

export function useLeaveBalances(params?: Record<string, string | undefined>) {
  return useQuery({
    queryKey: ["vashandi", "leave", "balances", params],
    queryFn: async () => {
      const response = await listLeaveBalances(params);
      return { response, items: leaveBalancesFromResponse(response) };
    },
    staleTime: 30_000,
  });
}

export function useAccessRisks(params?: Record<string, string | undefined>) {
  return useQuery({
    queryKey: ["vashandi", "access-risks", params],
    queryFn: async () => {
      const response = await listAccessRisks(params);
      return { response, items: accessRisksFromResponse(response) };
    },
    staleTime: 30_000,
  });
}

export function useResolveAccessRisk() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ riskId, body }: { riskId: string; body?: Record<string, unknown> }) =>
      resolveAccessRisk(riskId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "access-risks"] });
    },
  });
}

export function useRevokeAccessForRisk() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ riskId, body }: { riskId: string; body?: Record<string, unknown> }) =>
      revokeAccessForRisk(riskId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "access-risks"] });
    },
  });
}

export function useScanAccessRisks() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body?: Record<string, unknown>) => scanAccessRisks(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "access-risks"] });
    },
  });
}

export function useStaffingAnalytics(params?: Record<string, string | undefined>) {
  return useQuery({
    queryKey: ["vashandi", "analytics", "staffing", params],
    queryFn: async () => {
      const response = await getStaffingAnalytics(params);
      return { response, analytics: staffingFromResponse(response) };
    },
    staleTime: 60_000,
  });
}

export function useRosterCoverageAnalytics(params?: Record<string, string | undefined>) {
  return useQuery({
    queryKey: ["vashandi", "analytics", "roster-coverage", params],
    queryFn: async () => {
      const response = await getRosterCoverageAnalytics(params);
      return { response, coverage: rosterCoverageFromResponse(response) };
    },
    staleTime: 60_000,
  });
}

export function useAttendanceAnalytics(params?: Record<string, string | undefined>) {
  return useQuery({
    queryKey: ["vashandi", "analytics", "attendance", params],
    queryFn: async () => {
      const response = await getAttendanceAnalytics(params);
      return { response, analytics: attendanceAnalyticsFromResponse(response) };
    },
    staleTime: 60_000,
  });
}

export function useAccessRiskAnalytics(params?: Record<string, string | undefined>) {
  return useQuery({
    queryKey: ["vashandi", "analytics", "access-risk", params],
    queryFn: async () => {
      const response = await getAccessRiskAnalytics(params);
      return { response, analytics: accessRiskAnalyticsFromResponse(response) };
    },
    staleTime: 60_000,
  });
}

export function isVashandiDegraded(response: VashandiActionResponse | undefined): boolean {
  if (!response) return false;
  return isUpstreamUnavailable(response) || response.integrationStatus === "DEGRADED";
}

export function isVashandiEmptyList<T>(items: T[], response: VashandiActionResponse | undefined): boolean {
  return items.length === 0 && Boolean(response?.success);
}
