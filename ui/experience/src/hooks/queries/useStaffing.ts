/**
 * Experience UI — Staffing (roster from shifts, on-call assignments & swaps)
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface StaffingShiftResource {
  id: string;
  type: string;
  attributes: {
    user_id: string;
    staff_display_name: string;
    facility_id: string | null;
    workspace_id: string | null;
    status: string;
    started_at: string;
    ended_at: string | null;
  };
}

export interface OnCallAssignmentResource {
  id: string;
  type: string;
  attributes: {
    assignment_date: string;
    specialty: string;
    shift_kind: string;
    primary_staff_name: string;
    primary_phone: string | null;
    backup_staff_name: string;
    backup_phone: string | null;
  };
}

export interface OnCallSwapResource {
  id: string;
  type: string;
  attributes: {
    requestor_name: string;
    requestee_name: string;
    original_date: string;
    swap_date: string;
    specialty: string | null;
    status: string;
  };
}

export function useStaffingRosterWeek(params: {
  facilityId: string | undefined;
  workspaceId?: string | null;
  weekStartISO: string;
}) {
  const { facilityId, workspaceId, weekStartISO } = params;
  return useQuery({
    queryKey: ["staffing", "roster-week", facilityId, workspaceId ?? "", weekStartISO],
    queryFn: async () => {
      const q = new URLSearchParams({
        facility_id: facilityId!,
        week_start: weekStartISO,
      });
      if (workspaceId) q.set("workspace_id", workspaceId);
      return apiClient.get<{ data: StaffingShiftResource[]; meta?: Record<string, unknown> }>(
        `/internal/v1/staffing/roster-week?${q.toString()}`
      );
    },
    enabled: !!facilityId && !!weekStartISO,
  });
}

export function useOnCallWeek(params: { facilityId: string | undefined; weekStartISO: string }) {
  const { facilityId, weekStartISO } = params;
  return useQuery({
    queryKey: ["staffing", "on-call-week", facilityId, weekStartISO],
    queryFn: async () => {
      const q = new URLSearchParams({ facility_id: facilityId!, week_start: weekStartISO });
      return apiClient.get<{ data: OnCallAssignmentResource[]; meta?: Record<string, unknown> }>(
        `/internal/v1/staffing/on-call?${q.toString()}`
      );
    },
    enabled: !!facilityId && !!weekStartISO,
  });
}

export function useOnCallSwaps(facilityId: string | undefined) {
  return useQuery({
    queryKey: ["staffing", "on-call-swaps", facilityId],
    queryFn: async () => {
      const q = new URLSearchParams({ facility_id: facilityId! });
      return apiClient.get<{ data: OnCallSwapResource[]; meta?: Record<string, unknown> }>(
        `/internal/v1/staffing/on-call/swaps?${q.toString()}`
      );
    },
    enabled: !!facilityId,
  });
}

export interface CreateOnCallSwapPayload {
  facility_id: string;
  requestor_name: string;
  requestee_name: string;
  original_date: string;
  swap_date: string;
  specialty?: string | null;
}

export function useCreateOnCallSwap() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateOnCallSwapPayload) =>
      apiClient.post<{ data: OnCallSwapResource }>("/internal/v1/staffing/on-call/swaps", body),
    onSuccess: (_res, variables) => {
      queryClient.invalidateQueries({ queryKey: ["staffing", "on-call-swaps", variables.facility_id] });
    },
  });
}

export function usePatchOnCallSwap() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; facilityId: string; status: "APPROVED" | "DECLINED" }) =>
      apiClient.patch<{ data: OnCallSwapResource }>(`/internal/v1/staffing/on-call/swaps/${args.id}`, {
        status: args.status,
      }),
    onSuccess: (_res, variables) => {
      queryClient.invalidateQueries({ queryKey: ["staffing", "on-call-swaps", variables.facilityId] });
    },
  });
}
