/**
 * OF-B28 — triage ordering and response-window helpers.
 */

import { describe, expect, it } from "vitest";
import { responseWindow, rungLabel, triageSort } from "../triage";
import type { AlertEpisodeView } from "../types";

function ep(overrides: Partial<AlertEpisodeView>): AlertEpisodeView {
  return {
    episodeId: Math.random().toString(36).slice(2),
    planId: "P",
    patientCpid: "C",
    deviceRef: null,
    triggerClass: "THRESHOLD_BREACH",
    metricCode: "SBP",
    initialSeverity: "AMBER",
    severity: "AMBER",
    status: "OPEN",
    severityCeilingApplied: false,
    contributingReadings: null,
    ladderHistory: null,
    currentRung: null,
    breachCount: 1,
    responseDeadlineAt: null,
    acknowledgedBy: null,
    acknowledgedAt: null,
    autoEscalatedAt: null,
    chwTaskDispatched: null,
    reviewReferralRef: null,
    emsIncidentRef: null,
    emsDispatched: null,
    assumptionOfCareNote: null,
    assumptionOfCareBy: null,
    resolvedBy: null,
    assessment: null,
    actionTaken: null,
    outcome: null,
    resolvedAt: null,
    createdAt: "2026-07-23T08:00:00Z",
    ...overrides,
  };
}

describe("triageSort", () => {
  it("sorts EMERGENCY before RED before AMBER, then by tightest deadline", () => {
    const amber = ep({ episodeId: "A", severity: "AMBER" });
    const redLate = ep({ episodeId: "RL", severity: "RED", responseDeadlineAt: "2026-07-23T12:00:00Z" });
    const redSoon = ep({ episodeId: "RS", severity: "RED", responseDeadlineAt: "2026-07-23T09:00:00Z" });
    const emergency = ep({ episodeId: "E", severity: "EMERGENCY" });

    const sorted = triageSort([amber, redLate, redSoon, emergency]).map((e) => e.episodeId);
    expect(sorted).toEqual(["E", "RS", "RL", "A"]);
  });
});

describe("responseWindow", () => {
  it("flags overdue episodes", () => {
    const w = responseWindow(
      ep({ responseDeadlineAt: "2026-07-23T08:00:00Z" }),
      new Date("2026-07-23T09:05:00Z"),
    );
    expect(w.overdue).toBe(true);
    expect(w.label).toMatch(/^overdue 1h 05m$/);
  });

  it("counts down when time remains", () => {
    const w = responseWindow(
      ep({ responseDeadlineAt: "2026-07-23T09:30:00Z" }),
      new Date("2026-07-23T09:00:00Z"),
    );
    expect(w.overdue).toBe(false);
    expect(w.label).toBe("30m left");
  });

  it("is honest when there is no window", () => {
    const w = responseWindow(ep({ responseDeadlineAt: null }));
    expect(w.remainingMs).toBeNull();
    expect(w.label).toBe("no response window");
  });
});

describe("rungLabel", () => {
  it("names the §14.7 rungs", () => {
    expect(rungLabel(4)).toBe("CHW task");
    expect(rungLabel(11)).toBe("Daidzai EMS dispatch");
    expect(rungLabel(null)).toBe("—");
  });
});
