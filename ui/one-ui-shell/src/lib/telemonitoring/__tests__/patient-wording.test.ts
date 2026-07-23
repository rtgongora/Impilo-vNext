/**
 * OF-B27 — §14.6 patient-wording law: plain phrases, no diagnosis, always an
 * action, always the standing safety line; DEGRADED made humane; device
 * silence becomes "reconnect".
 */

import { describe, expect, it } from "vitest";
import {
  DEVICE_STALE_HOURS,
  SAFETY_LINE,
  deviceStaleNotice,
  metricPlainPhrase,
  patientAlertNotice,
  planPlainStatus,
  readingQualityLabel,
} from "../patient-wording";
import type { AlertEpisodeView, MonitoringPlanView, ReadingView } from "../types";

function episode(overrides: Partial<AlertEpisodeView> = {}): AlertEpisodeView {
  return {
    episodeId: "E-1",
    planId: "P-1",
    patientCpid: "CPID-1",
    deviceRef: "DEV-1",
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

describe("metricPlainPhrase", () => {
  it("maps coded metrics to plain phrases and never exposes only a raw code", () => {
    expect(metricPlainPhrase("SBP")).toBe("blood pressure");
    expect(metricPlainPhrase("SPO2")).toBe("oxygen level");
    expect(metricPlainPhrase("GLUCOSE")).toBe("blood sugar");
    expect(metricPlainPhrase("XYZ_UNKNOWN")).toBe("health reading");
    expect(metricPlainPhrase(null)).toBe("health reading");
  });
});

describe("planPlainStatus", () => {
  it("speaks plainly about the active plan", () => {
    const plan = { programmeCode: "HTN", status: "ACTIVE" } as MonitoringPlanView;
    expect(planPlainStatus(plan)).toBe("Your blood-pressure plan is active.");
  });

  it("never says CANCELLED to a patient", () => {
    const plan = { programmeCode: "DM", status: "CANCELLED" } as MonitoringPlanView;
    const text = planPlainStatus(plan);
    expect(text).toBe("Your blood-sugar plan has ended.");
    expect(text).not.toMatch(/CANCELLED/);
  });
});

describe("readingQualityLabel (the DEGRADED stamp made humane)", () => {
  it("says re-check, never jargon", () => {
    const degraded = { status: "DEGRADED_VALID" } as ReadingView;
    expect(readingQualityLabel(degraded)).toBe("This reading needs a re-check.");
    expect(readingQualityLabel(degraded)).not.toMatch(/DEGRADED/);
  });

  it("is silent for valid readings", () => {
    expect(readingQualityLabel({ status: "VALID" } as ReadingView)).toBeNull();
  });
});

describe("deviceStaleNotice", () => {
  it("prompts reconnection after the staleness window", () => {
    const now = new Date("2026-07-23T12:00:00Z");
    const old = new Date(now.getTime() - (DEVICE_STALE_HOURS + 1) * 3_600_000).toISOString();
    expect(deviceStaleNotice(old, now)).toMatch(/reconnect your device/);
  });

  it("stays quiet for a fresh reading", () => {
    const now = new Date("2026-07-23T12:00:00Z");
    const recent = new Date(now.getTime() - 2 * 3_600_000).toISOString();
    expect(deviceStaleNotice(recent, now)).toBeNull();
  });
});

describe("patientAlertNotice (§14.6 law)", () => {
  it("uses the coded-metric plain phrase, gives contact guidance and the safety line — never a diagnosis", () => {
    const notice = patientAlertNotice(episode());
    expect(notice.headline).toMatch(/blood pressure/);
    expect(notice.headline).not.toMatch(/hypertens|diagnos|risk score/i);
    expect(notice.guidance).toMatch(/contact your care team/i);
    expect(notice.safetyLine).toBe(SAFETY_LINE);
  });

  it("never exposes the raw severity grade", () => {
    const notice = patientAlertNotice(episode({ severity: "EMERGENCY" }));
    expect(`${notice.headline} ${notice.guidance}`).not.toMatch(/EMERGENCY|RED|AMBER/);
  });

  it("tells the patient a CHW visit is coming when one was dispatched", () => {
    const notice = patientAlertNotice(episode({ chwTaskDispatched: true }));
    expect(notice.guidance).toMatch(/community health worker/);
  });

  it("device-offline alerts speak about the device, not the body", () => {
    const notice = patientAlertNotice(episode({ triggerClass: "DEVICE_OFFLINE", metricCode: null }));
    expect(notice.headline).toMatch(/device/);
  });
});
