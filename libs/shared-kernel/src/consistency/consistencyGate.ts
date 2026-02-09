/**
 * ConsistencyGate — Companion Spec section 3.4 + Manifest Law 6.
 *
 * Determines the consistency class, sync requirements, and authority
 * for a given action type. Deterministic and side-effect-free.
 */
import type { ActionType, ConsistencyDecision } from "../types.js";
import { Authority, ConsistencyClass } from "../types.js";

const ACTION_MAP: ReadonlyMap<string, ConsistencyDecision> = new Map([
  [
    "clinical.prescribe.controlled_substance",
    {
      class: ConsistencyClass.A,
      sync_required: true,
      authority: Authority.UNKNOWN,
      max_staleness_ms: 0,
    },
  ],
  [
    "finance.tariff.update",
    {
      class: ConsistencyClass.A,
      sync_required: true,
      authority: Authority.UNKNOWN,
      max_staleness_ms: 0,
    },
  ],
  [
    "identity.patient.merge",
    {
      class: ConsistencyClass.A,
      sync_required: true,
      authority: Authority.NATIONAL_SPINE_ONLY,
      max_staleness_ms: 0,
    },
  ],
  [
    "clinical.admit.patient",
    {
      class: ConsistencyClass.B,
      sync_required: false,
      authority: Authority.UNKNOWN,
      max_staleness_ms: 30_000,
    },
  ],
  [
    "clinical.capture.vitals.offline",
    {
      class: ConsistencyClass.C,
      sync_required: false,
      authority: Authority.UNKNOWN,
      offline_allowed: true,
    },
  ],
]);

const DEFAULT_DECISION: ConsistencyDecision = {
  class: ConsistencyClass.B,
  sync_required: false,
  authority: Authority.UNKNOWN,
  max_staleness_ms: 300_000,
};

/**
 * Decide the consistency requirements for a given action.
 *
 * Returns a frozen ConsistencyDecision. For unknown actions,
 * returns Class B defaults with max_staleness_ms=300000.
 */
export function decideConsistency(action: ActionType): ConsistencyDecision {
  const decision = ACTION_MAP.get(action);
  if (decision) {
    return { ...decision };
  }
  return { ...DEFAULT_DECISION };
}
