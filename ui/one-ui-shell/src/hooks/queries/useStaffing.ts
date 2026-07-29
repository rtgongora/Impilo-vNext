/**
 * Experience UI — Staffing (roster from shifts, on-call assignments & swaps)
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/**
 * Whether the BFF could reach the identity registries for this response.
 *
 * `LIVE` — the registries answered, so a missing name means the person genuinely has no provider
 * record. `UNAVAILABLE` — a registry did not answer, so every missing name is missing because
 * resolution failed, and the screen must say so rather than show a board of nameless staff.
 * `NOT_APPLICABLE` — nobody was rostered, so there was nothing to resolve.
 */
export type IdentityResolutionStatus = "LIVE" | "UNAVAILABLE" | "NOT_APPLICABLE";

export interface StaffingMeta {
  identity_resolution?: IdentityResolutionStatus;
  [key: string]: unknown;
}

export interface StaffingShiftResource {
  id: string;
  type: string;
  attributes: {
    /** Vashandi's person reference. Names live in vito/varapi, not in the rostering service. */
    workforce_profile_id: string;
    /** Operational worker id — a reference staff recognise, NOT the person's name. May be null. */
    staff_reference: string | null;
    /** Composed by the BFF from the provider registry; null when unknown or unresolvable. */
    display_name?: string | null;
    profession?: string | null;
    phone?: string | null;
    phone_source?: string | null;
    facility_id: string | null;
    shift_type: string | null;
    on_call_role: "PRIMARY" | "BACKUP" | null;
    specialty: string | null;
    status: string;
    started_at: string;
    ended_at: string | null;
    virtual_pool_id: string | null;
  };
}

export interface OnCallAssignmentResource {
  id: string;
  type: string;
  attributes: {
    assignment_date: string;
    specialty: string;
    shift_kind: string | null;
    primary_workforce_profile_id: string | null;
    primary_staff_reference: string | null;
    primary_shift_id: string | null;
    /** Resolved against the provider registry by the BFF; null when unknown or unresolvable. */
    primary_display_name?: string | null;
    primary_profession?: string | null;
    /** null means "not known" — no layer invents a number somebody would dial at 3am. */
    primary_phone: string | null;
    /** Present only when a number came from a registry, naming which one. */
    primary_phone_source?: string | null;
    backup_workforce_profile_id: string | null;
    backup_staff_reference: string | null;
    backup_shift_id: string | null;
    backup_display_name?: string | null;
    backup_profession?: string | null;
    backup_phone: string | null;
    backup_phone_source?: string | null;
  };
}

export interface OnCallSwapResource {
  id: string;
  type: string;
  attributes: {
    requesting_shift_id: string;
    requesting_workforce_profile_id: string;
    requesting_display_name?: string | null;
    requested_workforce_profile_id: string;
    requested_display_name?: string | null;
    offered_shift_id: string | null;
    facility_id: string | null;
    reason: string | null;
    status: string;
    decided_by: string | null;
    decided_at: string | null;
    created_at: string | null;
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
      return apiClient.get<{ data: StaffingShiftResource[]; meta?: StaffingMeta }>(
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
      return apiClient.get<{ data: OnCallAssignmentResource[]; meta?: StaffingMeta }>(
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
      return apiClient.get<{ data: OnCallSwapResource[]; meta?: StaffingMeta }>(
        `/internal/v1/staffing/on-call/swaps?${q.toString()}`
      );
    },
    enabled: !!facilityId,
  });
}

/**
 * A swap is raised against a rostered shift and a person, never against typed names: two staff
 * share a name, and a swap changes who is clinically accountable for a window.
 */
export interface CreateOnCallSwapPayload {
  requesting_shift_id: string;
  requested_profile_id: string;
  offered_shift_id?: string | null;
  reason?: string | null;
}

export function useCreateOnCallSwap(facilityId?: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateOnCallSwapPayload) =>
      apiClient.post<{ data: OnCallSwapResource }>("/internal/v1/staffing/on-call/swaps", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["staffing", "on-call-swaps", facilityId] });
    },
  });
}

export function usePatchOnCallSwap() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (args: {
      id: string;
      facilityId: string;
      status: "APPROVED" | "DECLINED" | "WITHDRAWN";
      note?: string | null;
    }) =>
      apiClient.patch<{ data: OnCallSwapResource }>(`/internal/v1/staffing/on-call/swaps/${args.id}`, {
        status: args.status,
        note: args.note ?? null,
      }),
    onSuccess: (_res, variables) => {
      queryClient.invalidateQueries({ queryKey: ["staffing", "on-call-swaps", variables.facilityId] });
    },
  });
}

/**
 * How to render a rostered person: their registry name when one resolved, otherwise the worker
 * reference staff already recognise. Never a blank, never an invented name.
 */
export function staffLabel(
  displayName: string | null | undefined,
  staffReference: string | null | undefined
): string | null {
  const name = displayName?.trim();
  if (name) return name;
  const reference = staffReference?.trim();
  return reference ? reference : null;
}

/**
 * True when names are absent because the identity registries could not be reached, rather than
 * because the people on this rota have no registry record. The screen must distinguish the two:
 * a board that silently degrades to bare references hides an outage nobody then investigates.
 */
export function identityResolutionFailed(meta: StaffingMeta | undefined): boolean {
  return meta?.identity_resolution === "UNAVAILABLE";
}
