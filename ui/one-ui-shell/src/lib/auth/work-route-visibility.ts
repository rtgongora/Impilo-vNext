/**
 * Whether a work/professional route may render for the current session.
 *
 * The client-side citizen heuristic is derived from three BFF reads (linked IDs,
 * affiliations, workforce-governance assignments). Those reads start empty, and an
 * empty read is indistinguishable from "this person is a citizen" — so on every full
 * page load a clinician is briefly citizen-only. Deciding on that value bounces a
 * legitimate deep link into /clinical before the page has a chance to render.
 *
 * Two sources can settle the question, and the guard must wait for both to stop
 * loading before it acts on an absence:
 *
 *   - the identity reads themselves, and
 *   - the BFF Session Experience Contract, which is authoritative for visibility
 *     and can grant a route the local heuristic would refuse.
 *
 * Waiting is only ever a deferral. Once both have settled, an unresolved
 * professional identity still blocks — an outage must not open a work surface.
 */
export type WorkRouteVisibility = "allow" | "wait" | "block";

export interface WorkRouteVisibilityInput {
  /** The local heuristic's verdict: no professional identity resolved for this session. */
  isCitizenOnly: boolean;
  /** The identity reads have not settled yet (true while any of the three is in flight). */
  identityLoading: boolean;
  /** This path is one the citizen shell may not reach. */
  routeBlockedForCitizen: boolean;
  /** A contract has been received (from the BFF or the local fallback resolver). */
  hasContract: boolean;
  /** The contract request has not settled yet. */
  contractLoading: boolean;
  /** The received contract grants this path. Ignored when no contract has arrived. */
  contractGrantsRoute: boolean;
}

export function resolveWorkRouteVisibility(input: WorkRouteVisibilityInput): WorkRouteVisibility {
  if (!input.isCitizenOnly || !input.routeBlockedForCitizen) return "allow";

  // The contract outranks the heuristic, so a granted route renders even while the
  // identity reads are still in flight.
  if (input.hasContract && input.contractGrantsRoute) return "allow";

  if (input.identityLoading) return "wait";
  if (!input.hasContract && input.contractLoading) return "wait";

  return "block";
}
