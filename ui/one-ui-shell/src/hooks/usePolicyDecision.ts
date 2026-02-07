import { useCallback, useState } from "react";
import { apiClient } from "@/lib/apiClient";
import type { AuthzResponse, StepUpChallenge } from "@/lib/contracts";

interface PolicyDecisionState {
  isLoading: boolean;
  lastDecision: AuthzResponse | null;
  stepUpChallenge: StepUpChallenge | null;
  checkAccess: (action: string, resourceType: string, resourceId?: string) => Promise<boolean>;
  clearStepUp: () => void;
}

/**
 * Hook for explicitly checking policy decisions before sensitive actions.
 * For most requests, the trust check happens automatically at the gateway (Envoy ext_authz).
 * Use this hook only for pre-flight checks on high-risk UI actions.
 */
export function usePolicyDecision(): PolicyDecisionState {
  const [isLoading, setIsLoading] = useState(false);
  const [lastDecision, setLastDecision] = useState<AuthzResponse | null>(null);
  const [stepUpChallenge, setStepUpChallenge] = useState<StepUpChallenge | null>(null);

  const checkAccess = useCallback(
    async (action: string, resourceType: string, resourceId?: string): Promise<boolean> => {
      setIsLoading(true);
      try {
        const response = await apiClient.post<AuthzResponse>("/api/v1/authorize", {
          action,
          resourceType,
          resourceId,
        });

        setLastDecision(response.data);

        if (response.data.decision === "STEP_UP_REQUIRED") {
          setStepUpChallenge({
            methods: response.data.stepUpMethods ?? [],
            correlationId: response.headers?.["x-correlation-id"] ?? "",
          });
          return false;
        }

        return response.data.decision === "ALLOW";
      } catch {
        return false;
      } finally {
        setIsLoading(false);
      }
    },
    []
  );

  const clearStepUp = useCallback(() => {
    setStepUpChallenge(null);
  }, []);

  return { isLoading, lastDecision, stepUpChallenge, checkAccess, clearStepUp };
}
