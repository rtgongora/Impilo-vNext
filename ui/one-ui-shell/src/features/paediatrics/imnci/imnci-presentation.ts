import type { ImnciClassification, ImnciAssessment } from "@/hooks/queries/usePaediatricDecisionSupport";

/**
 * How an IMNCI outcome is presented, decided in one place and tested without rendering.
 *
 * The IMNCI colours are not decoration. Pink means treat and refer urgently, yellow means treat
 * here, green means advise and send home. On a paper chart they are the whole navigation system,
 * and a nurse reads the colour before the words. That makes the colour the most dangerous thing on
 * this screen to get wrong, and produces the rule the whole module is built around:
 *
 * **A row that did not resolve is never coloured.** Not green, not grey-that-looks-green, not the
 * absence of a colour where the eye supplies "fine". `INDETERMINATE` gets its own visual identity
 * — an unresolved treatment, stated as such, naming the observation that would settle it. If this
 * module ever collapses INDETERMINATE into the green row, the classification engine has been
 * defeated at the last step and everything behind it is worth nothing.
 *
 * The second rule follows from the first: a `provisional` outcome is shown as provisional. The
 * engine sets it when a severer row could not be excluded, so the classification displayed is the
 * best available reading and not a conclusion, and a clinician told "some dehydration" who is not
 * told "severe dehydration could not be excluded" has been given a different sentence from the one
 * the engine produced.
 */

export type OutcomeTone = "critical" | "treat" | "well" | "unresolved" | "not-applicable";

export interface OutcomePresentation {
  tone: OutcomeTone;
  /** What the row says it is. Never a code alone. */
  heading: string;
  /** The colour band the guideline assigns, when the row resolved to one. */
  colourLabel: string | null;
  /** Tailwind classes for the row container. */
  containerClass: string;
  /** Tailwind classes for the status chip. */
  chipClass: string;
  /** The chip's text. */
  chipLabel: string;
  /**
   * The sentence that must appear when this row cannot be acted on as a finding. Null when the
   * row resolved and its action stands on its own.
   */
  caveat: string | null;
}

// Written out rather than generated so each mapping is greppable and reviewable on its own line.
const STYLES: Record<OutcomeTone, { container: string; chip: string }> = {
  // Pink on the paper chart. Kept visually loudest.
  critical: {
    container: "border-rose-400 bg-rose-50 dark:border-rose-600 dark:bg-rose-950/40",
    chip: "bg-rose-600 text-white",
  },
  treat: {
    container: "border-amber-400 bg-amber-50 dark:border-amber-600 dark:bg-amber-950/40",
    chip: "bg-amber-500 text-amber-950",
  },
  well: {
    container: "border-emerald-400 bg-emerald-50 dark:border-emerald-600 dark:bg-emerald-950/40",
    chip: "bg-emerald-600 text-white",
  },
  // Deliberately not green and deliberately not quiet. An unresolved row is an outstanding job.
  unresolved: {
    container: "border-slate-400 border-dashed bg-slate-50 dark:border-slate-500 dark:bg-slate-900/60",
    chip: "bg-slate-700 text-white",
  },
  "not-applicable": {
    container: "border-border bg-muted/40",
    chip: "bg-muted text-muted-foreground",
  },
};

export const COLOUR_LABELS: Record<string, string> = {
  PINK: "Pink — urgent referral",
  YELLOW: "Yellow — treat at this facility",
  GREEN: "Green — home care and advice",
};

/** Human wording for the fact keys the engine names as missing. */
export const OBSERVATION_LABELS: Record<string, string> = {
  chestIndrawing: "chest indrawing",
  respiratoryRate: "respiratory rate",
  temperatureC: "temperature",
  muacCm: "mid-upper-arm circumference",
  weightForHeightZ: "weight-for-height z-score",
  oedemaBothFeet: "bilateral pitting oedema",
  palmarPallor: "palmar pallor",
  malariaTest: "malaria test result",
  skinPinch: "skin pinch",
  sunkenEyes: "sunken eyes",
  drinkingEagerly: "drinking eagerly / unable to drink",
  lethargicOrUnconscious: "lethargic or unconscious",
  restlessIrritable: "restless or irritable",
  bloodInStool: "blood in the stool",
  diarrhoeaDays: "duration of diarrhoea",
  earPain: "ear pain",
  earDischargeDays: "duration of ear discharge",
  tenderSwellingBehindEar: "tender swelling behind the ear",
  stridorWhenCalm: "stridor when calm",
  generalisedRash: "generalised rash",
  mouthUlcers: "mouth ulcers",
  cornealClouding: "corneal clouding",
  eyePus: "pus draining from the eye",
  convulsions: "convulsions",
  vomitsEverything: "vomits everything",
  unableToDrinkOrBreastfeed: "unable to drink or breastfeed",
  stiffNeck: "stiff neck",
  feverDays: "duration of fever",
};

export function observationLabel(key: string): string {
  if (OBSERVATION_LABELS[key]) return OBSERVATION_LABELS[key];
  // A key the labels table has not caught up with is shown de-camel-cased rather than hidden.
  // Hiding it would produce "cannot classify: complete " with nothing named, which is the one
  // thing an unresolved row must never be.
  return key
    .replace(/([A-Z])/g, " $1")
    .replace(/_/g, " ")
    .trim()
    .toLowerCase();
}

export function observationList(keys: string[]): string {
  const labels = keys.map(observationLabel);
  if (labels.length === 0) return "";
  if (labels.length === 1) return labels[0];
  return `${labels.slice(0, -1).join(", ")} and ${labels[labels.length - 1]}`;
}

/**
 * Decides the tone of one classification row.
 *
 * Order matters: unresolved is tested before colour, so a row that carries a stale or defaulted
 * colour alongside an INDETERMINATE status still presents as unresolved. The status is the
 * authority on whether the engine reached a conclusion; the colour only describes a conclusion
 * that was reached.
 */
export function outcomeTone(outcome: ImnciClassification): OutcomeTone {
  if (outcome.status === "INDETERMINATE") return "unresolved";
  if (outcome.status === "NOT_APPLICABLE") return "not-applicable";
  if (!outcome.classification) return "unresolved";
  switch ((outcome.colour ?? "").toUpperCase()) {
    case "PINK":
      return "critical";
    case "YELLOW":
      return "treat";
    case "GREEN":
      return "well";
    default:
      // A resolved row with no colour is not a well child; it is a row this UI does not
      // understand, and saying so is safer than picking a band for it.
      return "unresolved";
  }
}

export function presentOutcome(outcome: ImnciClassification, tableName?: string): OutcomePresentation {
  const tone = outcome.status === "INDETERMINATE" ? "unresolved" : outcomeTone(outcome);
  const styles = STYLES[tone];
  const axis = outcome.axis ?? tableName ?? outcome.tableId;

  if (tone === "unresolved") {
    const missing = observationList(outcome.missingInputs);
    return {
      tone,
      heading: `${axis} — not classified`,
      colourLabel: null,
      containerClass: styles.container,
      chipClass: styles.chip,
      chipLabel: "Not classified",
      caveat: missing
        ? `This cannot be classified until ${missing} ${outcome.missingInputs.length === 1 ? "is" : "are"} recorded. It is not a finding that the child is well.`
        : "This could not be classified. It is not a finding that the child is well.",
    };
  }

  if (tone === "not-applicable") {
    return {
      tone,
      heading: `${axis} — not applicable`,
      colourLabel: null,
      containerClass: styles.container,
      chipClass: styles.chip,
      chipLabel: "Not applicable",
      caveat: outcome.rationale ?? "This chart does not apply to this child.",
    };
  }

  const name = outcome.classificationName ?? outcome.classification ?? axis;
  const colourLabel = outcome.colour ? (COLOUR_LABELS[outcome.colour.toUpperCase()] ?? outcome.colour) : null;

  return {
    tone,
    heading: name,
    colourLabel,
    containerClass: styles.container,
    chipClass: styles.chip,
    chipLabel: outcome.provisional ? "Provisional" : (outcome.colour ?? "Classified"),
    caveat: outcome.provisional
      ? "Provisional — a more severe classification on this chart could not be excluded from what has been recorded. Treat this as the least this child has, not the most."
      : null,
  };
}

/** Severity order for display: what must be acted on first appears first. */
export function outcomeRank(outcome: ImnciClassification): number {
  switch (outcomeTone(outcome)) {
    case "critical":
      return 0;
    // An unresolved row outranks a treat-here row: it is the one with an outstanding action on
    // the clinician, and burying it under resolved findings is how it gets missed.
    case "unresolved":
      return 1;
    case "treat":
      return 2;
    case "well":
      return 3;
    default:
      return 4;
  }
}

export function sortOutcomes(outcomes: ImnciClassification[]): ImnciClassification[] {
  return [...outcomes].sort((a, b) => outcomeRank(a) - outcomeRank(b));
}

/**
 * The one-line summary at the head of the panel.
 *
 * An assessment with unresolved rows never summarises as a disposition, because the disposition
 * was computed over an incomplete picture and reading it as final is exactly the mistake the
 * engine's `incomplete` flag exists to prevent.
 */
export function assessmentHeadline(assessment: ImnciAssessment): {
  tone: OutcomeTone;
  text: string;
  detail: string | null;
} {
  const unresolved = assessment.classifications.filter((c) => outcomeTone(c) === "unresolved");

  if (assessment.urgentReferralRequired) {
    return {
      tone: "critical",
      text: "Urgent referral required",
      detail: assessment.disposition,
    };
  }

  if (assessment.incomplete || unresolved.length > 0) {
    const outstanding = observationList(assessment.outstandingAssessments);
    return {
      tone: "unresolved",
      text: `Assessment incomplete — ${unresolved.length || assessment.outstandingAssessments.length} outstanding`,
      detail: outstanding
        ? `Record ${outstanding} to complete this assessment. The classifications below are what could be reached without them.`
        : "Some charts could not be classified from what has been recorded.",
    };
  }

  if (assessment.referralRequired) {
    return { tone: "treat", text: "Referral required", detail: assessment.disposition };
  }

  return {
    tone: "well",
    text: assessment.disposition ?? "Assessment complete",
    detail: null,
  };
}
