/**
 * React binding for identity-context orchestration.
 */

import { useMemo } from "react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useAffiliations } from "@/hooks/queries/useAffiliations";
import { useWorkAssignments } from "@/hooks/queries/useWorkAssignments";
import { useLinkedIds } from "@/hooks/queries/useLinkedIds";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import {
  resolveIdentityContext,
  type IdentityContext,
  type LoginMethod,
} from "@/lib/identity-context";

export function useIdentityContext(): IdentityContext & { isLoading: boolean } {
  const user = useAuthStore((s) => s.user);
  const hasFacility = useFacilityStore((s) => s.hasFacility);
  const operationalMode = useOperationalContextStore((s) => s.operationalMode);
  const { data: linkedData, isLoading: linkedLoading } = useLinkedIds();
  const { data: affiliations = [], isLoading: affLoading } = useAffiliations();
  const { data: workAssignments = [], isLoading: assignLoading } = useWorkAssignments();

  const loginMethod = (user as { loginMethod?: LoginMethod } | null)?.loginMethod;

  const context = useMemo(
    () =>
      resolveIdentityContext({
        user,
        linkedIds: linkedData?.data?.attributes,
        affiliations,
        workAssignments,
        loginMethod,
        hasFacility,
        operationalMode,
      }),
    [user, linkedData?.data?.attributes, affiliations, workAssignments, loginMethod, hasFacility, operationalMode],
  );

  return {
    ...context,
    isLoading: linkedLoading || affLoading || assignLoading,
  };
}
