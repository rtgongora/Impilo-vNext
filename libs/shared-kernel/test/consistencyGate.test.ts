import { describe, it, expect } from "vitest";
import { decideConsistency } from "../src/consistency/consistencyGate.js";
import { Authority, ConsistencyClass } from "../src/types.js";

describe("ConsistencyGate — decideConsistency", () => {
  it("clinical.prescribe.controlled_substance => Class A, sync, UNKNOWN, staleness 0", () => {
    const d = decideConsistency("clinical.prescribe.controlled_substance");
    expect(d.class).toBe(ConsistencyClass.A);
    expect(d.sync_required).toBe(true);
    expect(d.authority).toBe(Authority.UNKNOWN);
    expect(d.max_staleness_ms).toBe(0);
  });

  it("finance.tariff.update => Class A, sync, UNKNOWN, staleness 0", () => {
    const d = decideConsistency("finance.tariff.update");
    expect(d.class).toBe(ConsistencyClass.A);
    expect(d.sync_required).toBe(true);
    expect(d.authority).toBe(Authority.UNKNOWN);
    expect(d.max_staleness_ms).toBe(0);
  });

  it("identity.patient.merge => Class A, sync, NATIONAL_SPINE_ONLY, staleness 0", () => {
    const d = decideConsistency("identity.patient.merge");
    expect(d.class).toBe(ConsistencyClass.A);
    expect(d.sync_required).toBe(true);
    expect(d.authority).toBe(Authority.NATIONAL_SPINE_ONLY);
    expect(d.max_staleness_ms).toBe(0);
  });

  it("clinical.admit.patient => Class B, no sync, UNKNOWN, staleness 30000", () => {
    const d = decideConsistency("clinical.admit.patient");
    expect(d.class).toBe(ConsistencyClass.B);
    expect(d.sync_required).toBe(false);
    expect(d.authority).toBe(Authority.UNKNOWN);
    expect(d.max_staleness_ms).toBe(30_000);
  });

  it("clinical.capture.vitals.offline => Class C, no sync, UNKNOWN, offline_allowed", () => {
    const d = decideConsistency("clinical.capture.vitals.offline");
    expect(d.class).toBe(ConsistencyClass.C);
    expect(d.sync_required).toBe(false);
    expect(d.authority).toBe(Authority.UNKNOWN);
    expect(d.offline_allowed).toBe(true);
  });

  it("unknown action => Class B defaults with staleness 300000", () => {
    const d = decideConsistency("some.unknown.action");
    expect(d.class).toBe(ConsistencyClass.B);
    expect(d.sync_required).toBe(false);
    expect(d.authority).toBe(Authority.UNKNOWN);
    expect(d.max_staleness_ms).toBe(300_000);
  });

  it("returns a fresh object each call (no shared mutation)", () => {
    const a = decideConsistency("finance.tariff.update");
    const b = decideConsistency("finance.tariff.update");
    expect(a).toEqual(b);
    expect(a).not.toBe(b);
  });
});
