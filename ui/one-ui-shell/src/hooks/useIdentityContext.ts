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

/**
 * {@code isUnavailable} means the professional identity reads (linked IDs, council affiliations,
 * work assignments) failed, not that the person holds none. The resolver below fails closed on
 * missing input, so an outage silently downgrades a clinician to citizen access — safe, but
 * indistinguishable from "you are not a provider". Surfaces that explain or gate professional
 * capacity should say which of the two happened rather than let the person conclude their
 * registration has lapsed.
 */
export function useIdentityContext(): IdentityContext & {
  isLoading: boolean;
  isUnavailable: boolean;
} {
  const user = useAuthStore((s) => s.user);
  const hasFacility = useFacilityStore((s) => s.hasFacility);
  const operationalMode = useOperationalContextStore((s) => s.operationalMode);
  const { data: linkedData, isLoading: linkedLoading, isError: linkedUnavailable } = useLinkedIds();
  const { data: affiliations = [], isLoading: affLoading, isError: affUnavailable } =
    useAffiliations();
  const { data: workAssignments = [], isLoading: assignLoading, isError: assignUnavailable } =
    useWorkAssignments();

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

  // A failed source resolves the same way an empty one does: no linked Provider ID, no
  // affiliations, no work assignments — an identity inferred from an outage rather than read.
  // `isLoading` deliberately still goes false on error (a permanently-spinning chooser strands
  // the user), so surfaces that present this absence as fact must consult `isUnavailable`.
  //
  // AuthGuardProvider is safe by construction: the BFF Session Experience Contract is
  // authoritative for route visibility there, and the client-side citizen heuristic only blocks
  // where the contract also declines. It is the surfaces that *display* the inferred identity
  // that need this flag.
  return {
    ...context,
    isLoading: linkedLoading || affLoading || assignLoading,
    isUnavailable: linkedUnavailable || affUnavailable || assignUnavailable,
  };
}
