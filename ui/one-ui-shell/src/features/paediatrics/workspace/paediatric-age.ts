/**
 * Age facts and journey recommendation for the paediatric workspace.
 *
 * This mirrors the band boundaries in `libs/paediatric-domain`
 * (PaediatricAgeBand / PaediatricAgeRouter), which remains authoritative: any decision
 * with clinical consequence — which form is mandatory, which danger-sign rules apply,
 * which dose table is used — is made server-side against that library. What lives here
 * is navigation: which journey to *offer* first, and how to describe the child at the top
 * of the screen. A wrong answer here shows the clinician the wrong starting tile, which
 * they can ignore; a wrong answer there would change care.
 *
 * The boundaries are duplicated rather than fetched because the workspace must render an
 * age and a suggested journey instantly, before any request resolves. They are covered by
 * tests that state the same boundaries as the Java tests, so a divergence shows up as a
 * failure rather than as drift.
 */

export type PaediatricBand =
  | "NEWBORN_EARLY"
  | "NEWBORN_LATE"
  | "INFANT"
  | "YOUNG_CHILD"
  | "SCHOOL_AGE"
  | "ADOLESCENT_YOUNG"
  | "ADOLESCENT_OLDER"
  | "ADULT_TRANSITION";

export type PaediatricJourney =
  | "NEWBORN_ROUTINE"
  | "SICK_YOUNG_INFANT"
  | "IMNCI_UNDER_FIVE"
  | "WELL_CHILD"
  | "GENERAL_PAEDIATRIC"
  | "ADOLESCENT"
  | "TRANSITION_TO_ADULT"
  | "UNDETERMINED";

export type PresentationContext = "ROUTINE" | "ACUTE";

/** Upper bound of the sick-young-infant window, inclusive. */
export const SICK_YOUNG_INFANT_MAX_AGE_DAYS = 59;
/** Upper bound of the IMNCI under-five window, inclusive (59 completed months). */
export const UNDER_FIVE_MAX_AGE_DAYS = 1824;

const BANDS: { band: PaediatricBand; maxAgeDays: number }[] = [
  { band: "NEWBORN_EARLY", maxAgeDays: 7 },
  { band: "NEWBORN_LATE", maxAgeDays: 28 },
  { band: "INFANT", maxAgeDays: 364 },
  { band: "YOUNG_CHILD", maxAgeDays: 1824 },
  { band: "SCHOOL_AGE", maxAgeDays: 3649 },
  { band: "ADOLESCENT_YOUNG", maxAgeDays: 5474 },
  { band: "ADOLESCENT_OLDER", maxAgeDays: 6574 },
  { band: "ADULT_TRANSITION", maxAgeDays: Number.MAX_SAFE_INTEGER },
];

export interface AgeFacts {
  ageDays: number | null;
  ageMonths: number | null;
  ageYears: number | null;
  band: PaediatricBand | null;
  /** How a clinician would say it aloud: "3 days", "6 weeks", "14 months", "7 years". */
  display: string;
  isSickYoungInfantWindow: boolean;
  isUnderFive: boolean;
  paediatric: boolean;
}

export function ageDaysFrom(dateOfBirth: string | undefined | null, now = new Date()): number | null {
  if (!dateOfBirth) return null;
  const dob = new Date(dateOfBirth);
  if (Number.isNaN(dob.getTime())) return null;
  const days = Math.floor((now.getTime() - dob.getTime()) / 86_400_000);
  return days < 0 ? null : days;
}

export function bandFor(ageDays: number | null): PaediatricBand | null {
  if (ageDays === null || ageDays < 0) return null;
  return BANDS.find((entry) => ageDays <= entry.maxAgeDays)?.band ?? "ADULT_TRANSITION";
}

/**
 * How to say the age. Paediatric practice changes unit with age — days for a newborn,
 * weeks for a small infant, months up to two years — and showing "0 years" for a
 * ten-day-old is the kind of display that lets an adult dose be prescribed to a neonate.
 */
export function describeAge(ageDays: number | null): string {
  if (ageDays === null) return "Age unknown";
  if (ageDays === 0) return "Born today";
  if (ageDays < 14) return `${ageDays} day${ageDays === 1 ? "" : "s"}`;
  if (ageDays < 60) {
    const weeks = Math.floor(ageDays / 7);
    return `${weeks} week${weeks === 1 ? "" : "s"}`;
  }
  if (ageDays < 730) {
    const months = Math.floor(ageDays / 30.4375);
    return `${months} month${months === 1 ? "" : "s"}`;
  }
  const years = Math.floor(ageDays / 365.25);
  return `${years} year${years === 1 ? "" : "s"}`;
}

export function ageFactsFor(dateOfBirth: string | undefined | null, now = new Date()): AgeFacts {
  const ageDays = ageDaysFrom(dateOfBirth, now);
  const band = bandFor(ageDays);
  return {
    ageDays,
    ageMonths: ageDays === null ? null : Math.floor(ageDays / 30.4375),
    ageYears: ageDays === null ? null : Math.floor(ageDays / 365.25),
    band,
    display: describeAge(ageDays),
    isSickYoungInfantWindow: ageDays !== null && ageDays <= SICK_YOUNG_INFANT_MAX_AGE_DAYS,
    isUnderFive: ageDays !== null && ageDays <= UNDER_FIVE_MAX_AGE_DAYS,
    paediatric: band !== null && band !== "ADULT_TRANSITION",
  };
}

export interface JourneyRecommendation {
  journey: PaediatricJourney;
  title: string;
  rationale: string;
}

/**
 * Recommends a starting journey. It recommends and never forces: a clinician may open any
 * journey, and the reason for the suggestion is always shown so it can be disagreed with.
 */
export function recommendJourney(
  facts: AgeFacts,
  presentation: PresentationContext = "ROUTINE",
): JourneyRecommendation {
  if (facts.ageDays === null) {
    return {
      journey: "UNDETERMINED",
      title: "No journey suggested",
      rationale: "Date of birth is not recorded, so no age-specific journey can be suggested.",
    };
  }
  if (facts.isSickYoungInfantWindow) {
    return presentation === "ACUTE"
      ? {
          journey: "SICK_YOUNG_INFANT",
          title: "Sick young infant",
          rationale: `${facts.display} old — in the first 59 days any illness is assessed on the young-infant pathway, because the signs of severe infection are few and deterioration is fast.`,
        }
      : {
          journey: "NEWBORN_ROUTINE",
          title: "Newborn assessment",
          rationale: `${facts.display} old and presenting routinely.`,
        };
  }
  if (facts.isUnderFive) {
    return presentation === "ACUTE"
      ? {
          journey: "IMNCI_UNDER_FIVE",
          title: "IMNCI assessment",
          rationale: "Under five and unwell — danger signs, main symptoms, nutrition and immunisation are assessed in one encounter.",
        }
      : {
          journey: "WELL_CHILD",
          title: "Well child visit",
          rationale: "Under five, presenting routinely — growth, feeding, development and immunisation together.",
        };
  }
  if (facts.band === "SCHOOL_AGE") {
    return presentation === "ACUTE"
      ? {
          journey: "GENERAL_PAEDIATRIC",
          title: "General paediatric clerking",
          rationale: "School age — full age-appropriate clerking rather than symptom classification.",
        }
      : { journey: "WELL_CHILD", title: "Well child visit", rationale: "School age, routine contact." };
  }
  if (facts.band === "ADOLESCENT_YOUNG" || facts.band === "ADOLESCENT_OLDER") {
    return {
      journey: "ADOLESCENT",
      title: "Adolescent consultation",
      rationale: "Adolescent — a confidential encounter with psychosocial and safety assessment, not a larger child form.",
    };
  }
  return {
    journey: "TRANSITION_TO_ADULT",
    title: "Transition to adult services",
    rationale: "Has reached adult age — care transitions out of paediatric services.",
  };
}
