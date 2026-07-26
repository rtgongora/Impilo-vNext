import type { ImamAssessment, ImamVisit } from "@/hooks/queries/useImam";

/**
 * How an IMAM episode reads on screen.
 *
 * Pure, so the three distinctions this feature turns on can be tested without rendering:
 *
 *  - **not evaluated ≠ not ready.** A null discharge verdict means the knowledge platform could
 *    not be reached. Painting it as "not ready for discharge" would put a clinical finding about
 *    the child on screen when the only fact was a failed network call.
 *  - **never reviewed ≠ overdue.** A child enrolled and never seen is the most serious row on any
 *    list, and a plain "2 reviews missed" hides that nobody has ever looked at them.
 *  - **a cure needs its evidence.** Where the criteria are unmet or unknown, the outcome form must
 *    say so and demand a reason, not quietly accept the discharge.
 */

export type Tone = "critical" | "warning" | "positive" | "neutral" | "unknown";

export interface Banner {
  tone: Tone;
  title: string;
  detail: string;
}

export const PROGRAMME_LABELS: Record<string, string> = {
  OTP: "Outpatient therapeutic programme",
  SFP: "Supplementary feeding programme",
  INPATIENT_STABILISATION: "Inpatient stabilisation",
};

export const PROGRAMME_SHORT: Record<string, string> = {
  OTP: "OTP",
  SFP: "SFP",
  INPATIENT_STABILISATION: "Stabilisation",
};

export function programmeLabel(code: string | null | undefined): string {
  if (!code) return "Unknown programme";
  return PROGRAMME_LABELS[code] ?? code;
}

export const CLASSIFICATION_LABELS: Record<string, string> = {
  COMPLICATED_SEVERE_ACUTE_MALNUTRITION: "Complicated severe acute malnutrition",
  UNCOMPLICATED_SEVERE_ACUTE_MALNUTRITION: "Uncomplicated severe acute malnutrition",
  MODERATE_ACUTE_MALNUTRITION: "Moderate acute malnutrition",
};

export function classificationLabel(code: string | null | undefined): string | null {
  if (!code) return null;
  return CLASSIFICATION_LABELS[code] ?? code;
}

/** The outcomes the programme recognises, in the order a clinician is most likely to need them. */
export const OUTCOME_OPTIONS: { value: string; label: string; hint: string }[] = [
  { value: "CURED", label: "Cured", hint: "Discharge criteria met and recorded." },
  {
    value: "DEFAULTED",
    label: "Defaulted",
    hint: "Absent for the defined consecutive reviews, after tracing was attempted.",
  },
  { value: "DIED", label: "Died", hint: "Died while enrolled in the programme." },
  {
    value: "NON_RESPONDER",
    label: "Non-responder",
    hint: "Did not recover within the maximum stay. Needs investigation, not re-enrolment.",
  },
  {
    value: "TRANSFERRED_OUT",
    label: "Transferred out",
    hint: "Continuing the same treatment elsewhere; not an outcome of care.",
  },
  {
    value: "MEDICAL_TRANSFER",
    label: "Medical transfer",
    hint: "Referred to inpatient care for a medical complication.",
  },
];

export type DischargeState = "READY" | "NOT_READY" | "NOT_EVALUATED";

export interface DischargeReadiness {
  state: DischargeState;
  tone: Tone;
  headline: string;
  detail: string;
}

export function dischargeReadiness(assessment: ImamAssessment | null): DischargeReadiness {
  if (!assessment || assessment.dischargeEligible === null) {
    return {
      state: "NOT_EVALUATED",
      tone: "unknown",
      headline: "Discharge readiness was not evaluated",
      detail:
        assessment?.notAssessableReason ??
        "The clinical knowledge platform could not be reached, so the discharge criteria were not checked. This is an absence of assessment, not a finding about the child.",
    };
  }
  if (assessment.dischargeEligible) {
    const cure = assessment.dischargeOutcome === "CURED";
    return {
      state: "READY",
      tone: "positive",
      headline: cure ? "Discharge criteria are met" : "Ready to transfer",
      detail: cure
        ? "Every criterion is met. Discharge as cured and counsel on continued feeding."
        : `Stabilisation criteria are met. Transfer to ${assessment.dischargeDestination ?? "outpatient care"} — this is a transfer of setting, not a cure.`,
    };
  }
  const unmet = assessment.unmetCriteria.length;
  return {
    state: "NOT_READY",
    tone: "neutral",
    headline: "Not yet dischargeable",
    detail:
      unmet === 0
        ? "Treatment continues."
        : `${unmet} criterion${unmet === 1 ? "" : "s"} still to meet. Treatment continues.`,
  };
}

/**
 * The attendance line. Ordered so the child nobody has ever seen outranks the child who has
 * missed the most reviews — a count alone cannot tell those apart.
 */
export function attendanceBanner(assessment: ImamAssessment | null): Banner | null {
  if (!assessment) return null;

  if (assessment.noReviewRecorded) {
    return {
      tone: "critical",
      title: "No review has ever been recorded",
      detail:
        "This child was enrolled and has not been seen since. An episode with no recorded review is not a child doing well — trace today.",
    };
  }
  switch (assessment.attendanceStatus) {
    case "DEFAULTER":
      return {
        tone: "critical",
        title: `Defaulter — ${assessment.consecutiveMissedVisits ?? 0} consecutive reviews missed`,
        detail: `Last seen ${assessment.daysSinceLastContact ?? "?"} days ago. Trace, and record what the trace found rather than closing the episode blind.`,
      };
    case "TRACE_REQUIRED":
      return {
        tone: "warning",
        title: "A review was missed — trace now",
        detail: `${assessment.daysOverdue ?? 0} days overdue. Tracing starts before the child meets the defaulter definition, because by then the window in which a home visit brings them back has usually closed.`,
      };
    case "ATTENDING":
      return {
        tone: "positive",
        title: "Attending on schedule",
        detail: assessment.nextReviewDue
          ? `Next review due ${assessment.nextReviewDue}.`
          : "Next review not scheduled.",
      };
    default:
      return {
        tone: "unknown",
        title: "Attendance could not be determined",
        detail:
          assessment.notAssessableReason ??
          "The schedule could not be evaluated. Do not read this as the child attending.",
      };
  }
}

export const RESPONSE_LABELS: Record<string, string> = {
  RESPONDING: "Responding to treatment",
  RECOVERED: "Recovered",
  STATIC: "Weight gain below target",
  DETERIORATING: "Losing weight",
  NON_RESPONDER: "Not responding",
  UNKNOWN: "Response not yet measurable",
};

export function responseLabel(status: string | null | undefined): string {
  if (!status) return RESPONSE_LABELS.UNKNOWN;
  return RESPONSE_LABELS[status] ?? status;
}

export function responseTone(status: string | null | undefined): Tone {
  switch (status) {
    case "RECOVERED":
    case "RESPONDING":
      return "positive";
    case "STATIC":
      return "warning";
    case "DETERIORATING":
    case "NON_RESPONDER":
      return "critical";
    default:
      return "unknown";
  }
}

/**
 * Whether closing as CURED needs a written reason.
 *
 * True when the criteria are unmet *and* when they could not be evaluated. The second case
 * matters as much as the first: certifying a cure the system could not check is exactly the
 * false reassurance the episode record exists to prevent.
 */
export function requiresOverrideReason(
  outcome: string | null | undefined,
  assessment: ImamAssessment | null,
): boolean {
  if (outcome !== "CURED") return false;
  return assessment?.dischargeEligible !== true;
}

export function overrideReasonPrompt(assessment: ImamAssessment | null): string {
  if (!assessment || assessment.dischargeEligible === null) {
    return "The discharge criteria could not be evaluated, so a cure cannot be certified without a stated reason.";
  }
  const unmet = assessment.unmetCriteria.join(", ");
  return `The discharge criteria are not met (${unmet || "criteria unmet"}). Recording this as cured needs a stated reason, which is kept on the child's record.`;
}

export interface VisitVerdict {
  tone: Tone;
  label: string;
}

/** What one recorded review says about discharge. Null verdicts stay visibly unevaluated. */
export function visitVerdict(visit: ImamVisit): VisitVerdict {
  if (!visit.attended) {
    return { tone: "warning", label: "Did not attend" };
  }
  if (visit.dischargeEligible === null) {
    return { tone: "unknown", label: "Not evaluated" };
  }
  return visit.dischargeEligible
    ? { tone: "positive", label: "Discharge criteria met" }
    : { tone: "neutral", label: "Treatment continues" };
}

/** Tri-state findings must show "not assessed" as its own answer, never fold it into "no". */
export function triStateLabel(value: boolean | null | undefined, whenTrue: string, whenFalse: string): string {
  if (value === null || value === undefined) return "Not assessed";
  return value ? whenTrue : whenFalse;
}

export function oedemaLabel(value: string | null | undefined): string {
  switch (value) {
    case "PRESENT":
      return "Oedema present";
    case "ABSENT":
      return "No oedema";
    default:
      return "Oedema not assessed";
  }
}

export function formatMuac(value: number | null | undefined): string {
  return typeof value === "number" ? `${value.toFixed(1)} cm` : "not measured";
}

export function formatWeight(value: number | null | undefined): string {
  return typeof value === "number" ? `${value.toFixed(2)} kg` : "not measured";
}

export const TONE_CLASSES: Record<Tone, string> = {
  critical: "border-red-500 bg-red-50 text-red-900",
  warning: "border-amber-500 bg-amber-50 text-amber-900",
  positive: "border-emerald-500 bg-emerald-50 text-emerald-900",
  neutral: "border-border bg-muted text-foreground",
  unknown: "border-slate-400 bg-slate-50 text-slate-800",
};

export const TONE_BADGE: Record<Tone, string> = {
  critical: "bg-red-600 text-white",
  warning: "bg-amber-500 text-white",
  positive: "bg-emerald-600 text-white",
  neutral: "bg-muted text-foreground",
  unknown: "bg-slate-500 text-white",
};
