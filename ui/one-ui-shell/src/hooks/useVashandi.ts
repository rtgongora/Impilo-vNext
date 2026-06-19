import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { listWorkforceProfiles, getWorkforceProfile, reconcileWorkforceProfile, runVashandiPrecheck, profilesFromResponse } from "@/lib/vashandi/api/profiles";
import {
  listAssignments,
  getAssignment,
  createAssignment,
  precheckAssignment,
  assignmentsFromResponse,
} from "@/lib/vashandi/api/assignments";
import { listRosters, rostersFromResponse } from "@/lib/vashandi/api/rosters";
import { listAttendance, checkIn, checkOut, attendanceFromResponse } from "@/lib/vashandi/api/attendance";
import { listAvailability, leaveFromResponse } from "@/lib/vashandi/api/leave";
import { listAccessRisks, scanAccessRisks, accessRisksFromResponse } from "@/lib/vashandi/api/accessReview";
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
  return useMutation({
    mutationFn: ({ assignmentId, body }: { assignmentId: string; body?: Record<string, unknown> }) =>
      precheckAssignment(assignmentId, body),
  });
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

export function useCheckOut() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CheckOutRequest) => checkOut(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vashandi", "attendance"] });
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
