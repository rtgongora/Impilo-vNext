/**
 * What to offer someone who hit a facility-guarded route without a duty session.
 *
 * The BFF already knows where this person works: `SessionExperienceService` v1.4.0 resolves
 * `resolvedWorkContexts` from five real sources (Vashandi, WGV, TUSO facility-admin,
 * regulatory, support) and ranks one as `recommendedContextId`. None of that ever reached the
 * guard path — 148 routes carry `guard: "facility"`, and every one of them sent the person to
 * `/facility`, which lists the national facility registry and asks them to find their own
 * hospital in it. That is the "why do I keep re-capturing this" complaint: not amnesia, a
 * registry search standing in for a one-tap confirmation.
 *
 * What this does NOT do is auto-select. A work context is only real once a duty token is
 * minted — that mint is what `PolicyEngine.bindWorkContext` folds into the clinical role, and
 * a TYPE fence exists precisely because six paths were once found reaching a governed mode
 * without it. A recommendation must never silently become an authority. So: pre-select,
 * present, and let one deliberate tap do the minting.
 */

import type { ResolvedWorkContextView, WorkContextSourceStatusView } from "@/lib/trust";

/**
 * The five product states this surface must tell apart. Collapsing DEGRADED into EMPTY would
 * claim "you have no workplaces" when the truth is "we could not reach the system that knows"
 * — the exact class of lie the shell has been burned by before.
 */
export type ResumeState =
  | "LOADING"
  /** At least one resolved context, and one of them is recommended. */
  | "RESUMABLE"
  /** Contexts exist but none is ranked first — the person must choose. */
  | "CHOOSE"
  /** Every source answered, and this person genuinely holds no work context. */
  | "EMPTY"
  /** One or more sources could not be reached: the list may be incomplete. */
  | "DEGRADED";

export interface ResumeOffer {
  state: ResumeState;
  /** Pre-selected context — present only in RESUMABLE. */
  recommended: ResolvedWorkContextView | null;
  /** The mode a one-tap resume would mint. Null when the context offers a genuine choice. */
  recommendedMode: string | null;
  /** Everything except the recommended one, for the "somewhere else" list. */
  others: ResolvedWorkContextView[];
  /** Sources that failed, so the surface can say which ones rather than hand-waving. */
  degradedSources: string[];
}

/**
 * The single mode a context would mint without asking. A context offering several modes has no
 * unambiguous answer, so resume must fall through to an explicit mode choice rather than
 * guessing which authority the person wanted.
 */
export function soleMode(context: ResolvedWorkContextView): string | null {
  const modes = context.availableModes?.length
    ? context.availableModes
    : context.defaultMode
      ? [context.defaultMode]
      : [];
  if (modes.length === 1) return modes[0];
  if (context.defaultMode && modes.includes(context.defaultMode)) return context.defaultMode;
  return null;
}

export function buildResumeOffer(input: {
  contexts: ResolvedWorkContextView[] | undefined;
  recommendedContextId: string | null | undefined;
  sourceStatuses: WorkContextSourceStatusView[] | undefined;
  isLoading: boolean;
}): ResumeOffer {
  const contexts = input.contexts ?? [];
  const degradedSources = (input.sourceStatuses ?? [])
    .filter((s) => s.state === "DEGRADED")
    .map((s) => s.system);

  const empty: ResumeOffer = {
    state: "LOADING",
    recommended: null,
    recommendedMode: null,
    others: [],
    degradedSources,
  };

  if (input.isLoading) return empty;

  if (contexts.length === 0) {
    // A source being down is the difference between "you have none" and "we cannot tell".
    return { ...empty, state: degradedSources.length > 0 ? "DEGRADED" : "EMPTY" };
  }

  const recommended =
    contexts.find((c) => c.contextId === input.recommendedContextId) ?? null;

  if (!recommended) {
    return { ...empty, state: "CHOOSE", others: contexts };
  }

  return {
    state: "RESUMABLE",
    recommended,
    recommendedMode: soleMode(recommended),
    others: contexts.filter((c) => c.contextId !== recommended.contextId),
    degradedSources,
  };
}
