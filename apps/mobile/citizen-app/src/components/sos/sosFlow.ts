import type { CreateSosInput } from "../../services/emergencyService";

/**
 * Pure (RN-runtime-free) state machine for the floating SOS flow — unit-testable in isolation.
 *
 * Doctrine: life-safety + low friction, but a floating button is easy to hit by accident, so a
 * SEND is always gated behind an explicit CONFIRM. Every network result is an honest state
 * (sending / tracking / error) — never a fabricated success. No payment is ever involved.
 */
export type SosPhase = "idle" | "confirm" | "sending" | "tracking" | "error";

export interface SosState {
  phase: SosPhase;
  requestId?: string;
  error?: string;
}

export const initialSosState: SosState = { phase: "idle" };

export type SosAction =
  | { type: "OPEN" } // press the FAB → confirm sheet
  | { type: "CANCEL" } // dismiss the confirm
  | { type: "SEND" } // user confirmed → dispatch createSos
  | { type: "SENT"; requestId: string } // createSos resolved
  | { type: "FAIL"; error: string } // createSos rejected
  | { type: "CLOSE" }; // close the whole flow

export function sosReducer(state: SosState, action: SosAction): SosState {
  switch (action.type) {
    case "OPEN":
      // Openable from idle or after an error (retry).
      return state.phase === "sending" || state.phase === "tracking"
        ? state
        : { phase: "confirm" };
    case "CANCEL":
      return { phase: "idle" };
    case "SEND":
      return state.phase === "confirm" || state.phase === "error"
        ? { phase: "sending" }
        : state;
    case "SENT":
      return { phase: "tracking", requestId: action.requestId };
    case "FAIL":
      return { phase: "error", error: action.error };
    case "CLOSE":
      return initialSosState;
    default:
      return state;
  }
}

/**
 * Fast-path defaults for a floating one-tap SOS: raise a CRITICAL medical emergency for the signed-in
 * person. The full SosScreen remains available for category/severity/location detail.
 */
export function buildDefaultSosInput(overrides: Partial<CreateSosInput> = {}): CreateSosInput {
  return {
    forSelf: true,
    emergencyCategory: "MEDICAL",
    severity: "CRITICAL",
    ...overrides,
  };
}
