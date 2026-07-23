/**
 * OF-B27 — the §14.6 patient-wording law, in one place.
 *
 * Rules (binding): plain language; say what was observed and what to do next;
 * NEVER a diagnosis, prognosis or raw risk score; never alarm without an
 * action ("contact X"); every patient-facing alert carries the standing
 * safety line. DEGRADED quality stamps are made humane ("needs a re-check"),
 * never technical jargon.
 */

import type { AlertEpisodeView, MonitoringPlanView, ReadingView } from "./types";

/** Standing safety line — required on every patient-facing alert message. */
export const SAFETY_LINE =
  "If you feel seriously unwell, seek care right away — no matter what this app says.";

/** Coded metric → plain phrase. Unknown codes fall back to a neutral phrase, never the raw code alone. */
const METRIC_PLAIN: Record<string, string> = {
  SBP: "blood pressure",
  DBP: "blood pressure",
  BP: "blood pressure",
  HR: "heart rate",
  PULSE: "heart rate",
  SPO2: "oxygen level",
  GLUCOSE: "blood sugar",
  BG: "blood sugar",
  WEIGHT: "weight",
  TEMP: "temperature",
  TEMPERATURE: "temperature",
  RR: "breathing rate",
};

/** Programme code → plain plan name ("blood-pressure plan"). */
const PROGRAMME_PLAIN: Record<string, string> = {
  HTN: "blood-pressure",
  HYPERTENSION: "blood-pressure",
  DM: "blood-sugar",
  DIABETES: "blood-sugar",
  COPD: "breathing",
  ASTHMA: "breathing",
  HF: "heart-health",
  HEART_FAILURE: "heart-health",
  ANC: "pregnancy-care",
  MATERNAL: "pregnancy-care",
};

export function metricPlainPhrase(metricCode: string | null | undefined): string {
  if (!metricCode) return "health reading";
  return METRIC_PLAIN[metricCode.toUpperCase()] ?? "health reading";
}

export function programmePlainName(programmeCode: string | null | undefined): string {
  if (!programmeCode) return "monitoring";
  return PROGRAMME_PLAIN[programmeCode.toUpperCase()] ?? "monitoring";
}

/** "Your blood-pressure plan is active" — plain-language plan status. */
export function planPlainStatus(plan: MonitoringPlanView): string {
  const name = programmePlainName(plan.programmeCode);
  switch (plan.status) {
    case "ACTIVE":
      return `Your ${name} plan is active.`;
    case "SUSPENDED":
      return `Your ${name} plan is paused for now.`;
    case "COMPLETED":
      return `Your ${name} plan is complete.`;
    case "CANCELLED":
      return `Your ${name} plan has ended.`;
    case "PENDING_APPROVAL":
      return `Your ${name} plan is waiting for your clinician's approval.`;
    default:
      return `Your ${name} plan is being prepared.`;
  }
}

/** Humane quality label — the DEGRADED stamp made plain, never jargon. */
export function readingQualityLabel(reading: ReadingView): string | null {
  if (reading.status === "DEGRADED_VALID") return "This reading needs a re-check.";
  if (reading.status === "QUARANTINED") return "This reading didn't count — please take it again.";
  return null;
}

/** Hours after which a device is considered quiet enough to prompt reconnection. */
export const DEVICE_STALE_HOURS = 48;

export function deviceStaleNotice(lastMeasuredAt: string | null | undefined, now: Date = new Date()): string | null {
  if (!lastMeasuredAt) return "We haven't received a reading from your device yet — check it is set up and connected.";
  const last = new Date(lastMeasuredAt).getTime();
  if (Number.isNaN(last)) return null;
  const hours = (now.getTime() - last) / 3_600_000;
  if (hours >= DEVICE_STALE_HOURS) {
    return "We haven't heard from your device in a while — please reconnect your device.";
  }
  return null;
}

/**
 * Patient-facing alert notice — §14.6 law: what was observed (coded metric as a
 * plain phrase), what to do next ("contact X"), the standing safety line.
 * NEVER a diagnosis and never the raw severity grade.
 */
export function patientAlertNotice(episode: AlertEpisodeView): {
  headline: string;
  guidance: string;
  safetyLine: string;
} {
  const phrase = metricPlainPhrase(episode.metricCode);
  let headline: string;
  if (episode.triggerClass === "DEVICE_OFFLINE") {
    headline = "Your monitoring device has been quiet.";
  } else if (episode.triggerClass === "DEVICE_QUALITY") {
    headline = "Some recent readings from your device need a re-check.";
  } else {
    headline = `One of your ${phrase} readings was outside your plan's expected range.`;
  }

  let guidance: string;
  if (episode.emsDispatched) {
    guidance = "Emergency help has been arranged — stay where you are and keep your phone close.";
  } else if (episode.chwTaskDispatched) {
    guidance = "Your community health worker has been asked to visit you.";
  } else if (episode.status === "RESOLVED") {
    guidance = "Your care team reviewed this and it is now closed. No action is needed from you.";
  } else {
    guidance = "Your care team has been notified. If you were asked to repeat the reading, please do so — otherwise contact your care team or your community health worker.";
  }

  return { headline, guidance, safetyLine: SAFETY_LINE };
}
