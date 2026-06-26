/**
 * C2 Vashandi work-context read-model for the WHERE/WHAT context picker.
 *
 * Source: BFF GET /internal/v1/work-context (composes Vashandi's
 * /v1/internal/vashandi/work-context). Returns active assignments with their
 * WHERE scope (FACILITY / DEPARTMENT / WARD / SERVICE_POINT / VIRTUAL_POOL /
 * ABOVE_SITE), current check-in state, affiliations, and whether a context
 * chooser is required (>1 active assignment).
 *
 * Degrades honestly: when the BFF reports UPSTREAM_UNAVAILABLE (or the call
 * fails), the hook returns an unresolved context rather than throwing — the
 * picker shows "no work context" instead of an error.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

export type WorkContextScope =
  | "FACILITY"
  | "DEPARTMENT"
  | "WARD"
  | "SERVICE_POINT"
  | "VIRTUAL_POOL"
  | "ABOVE_SITE";

export interface WorkContextAssignment {
  assignmentId: string;
  status: string;
  organisationId: string | null;
  facilityId: string | null;
  departmentId: string | null;
  unitId: string | null;
  workspaceId: string | null;
  programmeId: string | null;
  roleTemplateId: string | null;
  supervisorProfileId: string | null;
  scope: WorkContextScope;
  eligibilityStatus: string | null;
}

export interface WorkContextCheckIn {
  shiftId: string | null;
  state: "CHECKED_IN" | "CHECKED_OUT";
  at: string | null;
  facilityId: string | null;
  supervisorConfirmed: boolean;
}

export interface WorkContextAffiliation {
  organisationId: string;
  relationshipType: string;
  primary: boolean;
  validFrom: string | null;
  validTo: string | null;
}

export interface WorkContext {
  workforceProfileId: string | null;
  impiloHealthId: string | null;
  activeAssignments: WorkContextAssignment[];
  checkIn: WorkContextCheckIn;
  affiliations: WorkContextAffiliation[];
  requiresContextChooser: boolean;
  resolved: boolean;
  integrationStatus: "LIVE" | "NO_WORK_CONTEXT" | "UPSTREAM_UNAVAILABLE";
}

interface WorkContextResource {
  id: string;
  type: "work-context";
  attributes: WorkContext;
}

type WorkContextResponse = ApiResponse<WorkContextResource>;

const UNRESOLVED: WorkContext = {
  workforceProfileId: null,
  impiloHealthId: null,
  activeAssignments: [],
  checkIn: { shiftId: null, state: "CHECKED_OUT", at: null, facilityId: null, supervisorConfirmed: false },
  affiliations: [],
  requiresContextChooser: false,
  resolved: false,
  integrationStatus: "UPSTREAM_UNAVAILABLE",
};

export function useWorkContext() {
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const query = useQuery<WorkContext>({
    queryKey: ["work-context", user?.id],
    queryFn: async () => {
      try {
        const res = await apiClient.get<WorkContextResponse>("/internal/v1/work-context");
        return res.data.attributes;
      } catch {
        return UNRESOLVED;
      }
    },
    enabled: isAuthenticated && !!user,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });

  return {
    workContext: query.data ?? UNRESOLVED,
    isLoading: query.isLoading,
    isError: query.isError,
    requiresContextChooser: query.data?.requiresContextChooser ?? false,
  };
}
