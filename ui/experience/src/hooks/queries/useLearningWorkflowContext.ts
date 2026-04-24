import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export function useLearningWorkflowContext(opts: {
  appCode: string;
  routeRef: string;
  workflowCode?: string;
  roles?: string;
  subjectType?: string;
  subjectId?: string;
  enabled?: boolean;
}) {
  const { appCode, routeRef, workflowCode, roles, subjectType, subjectId, enabled = true } = opts;
  const sp = new URLSearchParams();
  sp.set("appCode", appCode);
  sp.set("routeRef", routeRef);
  if (workflowCode) sp.set("workflowCode", workflowCode);
  if (roles) sp.set("roles", roles);
  if (subjectType) sp.set("subjectType", subjectType);
  if (subjectId) sp.set("subjectId", subjectId);
  return useQuery({
    queryKey: ["learning-workflow", appCode, routeRef, workflowCode, roles, subjectType, subjectId],
    queryFn: () => apiClient.get<unknown>(`/internal/v1/learning/workflow-context?${sp.toString()}`),
    enabled: enabled && !!appCode && !!routeRef,
    staleTime: 60_000,
  });
}

export function useLearningHelpdesk(
  issueType: string | undefined,
  subjectType?: string,
  subjectId?: string,
  enabled = true,
) {
  return useQuery({
    queryKey: ["learning-helpdesk", issueType, subjectType, subjectId],
    queryFn: () => {
      const sp = new URLSearchParams();
      if (subjectType) sp.set("subjectType", subjectType);
      if (subjectId) sp.set("subjectId", subjectId);
      const q = sp.toString();
      return apiClient.get<unknown>(
        `/internal/v1/learning/helpdesk/${encodeURIComponent(issueType!)}${q ? `?${q}` : ""}`,
      );
    },
    enabled: enabled && !!issueType,
    staleTime: 60_000,
  });
}
