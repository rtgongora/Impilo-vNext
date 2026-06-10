/**
 * Active work assignments for the authenticated provider/worker.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  mapGovernanceAssignmentNode,
  type WorkAssignment,
} from "@/lib/trust";

interface AssignmentsResponse {
  data: Record<string, unknown>[] | { attributes?: Record<string, unknown>[] };
}

function normalizeAssignments(payload: AssignmentsResponse["data"]): WorkAssignment[] {
  const rows = Array.isArray(payload) ? payload : payload?.attributes ?? [];
  return rows
    .map((row) => mapGovernanceAssignmentNode(row))
    .filter((a): a is WorkAssignment => a !== null);
}

export function useWorkAssignments() {
  const { user, isAuthenticated } = useAuthStore();
  const providerId = user?.providerId ?? user?.linkedIds?.providerId;

  return useQuery<WorkAssignment[]>({
    queryKey: ["work-assignments", user?.id, providerId],
    queryFn: async () => {
      if (!providerId) return [];
      const res = await apiClient.get<{ data: Record<string, unknown>[] }>(
        `/internal/v1/workforce-governance/assignments/search?subjectType=PROVIDER&subjectId=${encodeURIComponent(providerId)}&status=ACTIVE`,
      );
      const rows = Array.isArray(res.data) ? res.data : [];
      return rows
        .map((row) => mapGovernanceAssignmentNode(row))
        .filter((a): a is WorkAssignment => a !== null);
    },
    enabled: isAuthenticated && !!providerId,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });
}
