import { describe, it, expect } from "vitest";
import { sosReducer, initialSosState, buildDefaultSosInput } from "./sosFlow";

/**
 * Pure state-machine coverage for the floating SOS flow. The RN component (FloatingSosButton) can't
 * be rendered here (Expo), but the doctrine-critical logic — confirm-before-send, honest states,
 * no accidental dispatch — lives in this reducer and is fully verified.
 */
describe("sosFlow reducer", () => {
  it("OPEN → confirm; SEND is impossible without a confirm (accidental-trigger guard)", () => {
    expect(sosReducer(initialSosState, { type: "OPEN" }).phase).toBe("confirm");
    expect(sosReducer(initialSosState, { type: "SEND" }).phase).toBe("idle");
  });

  it("confirm → SEND → sending → SENT → tracking(requestId)", () => {
    let s = sosReducer(initialSosState, { type: "OPEN" });
    s = sosReducer(s, { type: "SEND" });
    expect(s.phase).toBe("sending");
    s = sosReducer(s, { type: "SENT", requestId: "REQ-1" });
    expect(s).toEqual({ phase: "tracking", requestId: "REQ-1" });
  });

  it("FAIL → error (honest), retriable via SEND", () => {
    const errored = sosReducer({ phase: "sending" }, { type: "FAIL", error: "network" });
    expect(errored).toEqual({ phase: "error", error: "network" });
    expect(sosReducer(errored, { type: "SEND" }).phase).toBe("sending");
  });

  it("CANCEL / CLOSE reset to idle", () => {
    expect(sosReducer({ phase: "confirm" }, { type: "CANCEL" }).phase).toBe("idle");
    expect(sosReducer({ phase: "tracking", requestId: "x" }, { type: "CLOSE" })).toEqual(initialSosState);
  });

  it("never reopens mid-send or mid-track", () => {
    expect(sosReducer({ phase: "sending" }, { type: "OPEN" }).phase).toBe("sending");
    expect(sosReducer({ phase: "tracking", requestId: "x" }, { type: "OPEN" }).phase).toBe("tracking");
  });
});

describe("buildDefaultSosInput", () => {
  it("fast-path = CRITICAL medical for self, no payment fields, overridable", () => {
    expect(buildDefaultSosInput()).toEqual({
      forSelf: true,
      emergencyCategory: "MEDICAL",
      severity: "CRITICAL",
    });
    expect(buildDefaultSosInput({ severity: "HIGH" }).severity).toBe("HIGH");
  });
});
