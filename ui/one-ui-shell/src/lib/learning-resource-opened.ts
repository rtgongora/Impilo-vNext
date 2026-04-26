import { apiClient } from "@/lib/api-client";

/** Fire-and-forget audit to learning-service (via BFF). */
export function recordLearningResourceOpened(
  resourceId: string,
  workflowContext?: Record<string, unknown>,
): void {
  if (!resourceId) return;
  void apiClient
    .post<unknown>("/internal/v1/learning/resource-opened", {
      resourceId,
      workflowContext: workflowContext ?? {},
    })
    .catch(() => {
      /* non-blocking */
    });
}
