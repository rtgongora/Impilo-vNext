/**
 * The order in which an IMNCI assessment is worked through.
 *
 * IMNCI is a sequence, not a questionnaire: emergency signs are looked for before anything
 * else, general danger signs before the presenting complaint, and nutrition and immunisation
 * in every child regardless of why they came. Presenting the sections in the order the
 * guideline intends is most of what turns a form into an assessment.
 *
 * The step list mirrors the section ids of the governed form
 * (forms-service seed-forms/08-imci-child-assessment.json), which remains the single
 * definition of what is asked. This module decides sequence and emphasis only — if the two
 * disagree about which sections exist, the form wins and the extra step simply has nothing
 * to render.
 */

export interface ImnciStep {
  /** Matches a section id in the governed IMNCI form definition. */
  sectionId: string;
  title: string;
  /** Why this step sits where it does — shown so the sequence teaches rather than just directs. */
  intent: string;
  /** Steps that must be worked through in every child, whatever the presenting complaint. */
  universal: boolean;
}

export const IMNCI_STEPS: ImnciStep[] = [
  {
    sectionId: "emergency",
    title: "Emergency signs",
    intent: "Looked for first. Any of these needs treatment before the assessment continues.",
    universal: true,
  },
  {
    sectionId: "danger",
    title: "General danger signs",
    intent: "Asked in every sick child, whatever brought them in. Any one means urgent referral after pre-referral treatment.",
    universal: true,
  },
  {
    sectionId: "cough",
    title: "Cough or difficult breathing",
    intent: "Count the respiratory rate for a full minute while the child is calm.",
    universal: false,
  },
  {
    sectionId: "diarrhoea",
    title: "Diarrhoea",
    intent: "Assess hydration, duration and blood in the stool.",
    universal: false,
  },
  {
    sectionId: "fever",
    title: "Fever",
    intent: "Consider malaria risk, measles and signs of meningitis.",
    universal: false,
  },
  {
    sectionId: "ear",
    title: "Ear problem",
    intent: "Look for discharge, its duration, and tender swelling behind the ear.",
    universal: false,
  },
  {
    sectionId: "nutrition",
    title: "Nutrition and anaemia",
    intent: "Assessed in every child, not only where malnutrition is suspected.",
    universal: true,
  },
  {
    sectionId: "immunisation",
    title: "Immunisation",
    intent: "Every sick-child visit is a chance to catch up on missed doses.",
    universal: true,
  },
  {
    sectionId: "classify",
    title: "Classify and plan",
    intent: "A guideline classification is not a diagnosis. Both are recorded, and kept apart.",
    universal: true,
  },
];

/** The four general danger signs, by the fact key the assessment records them under. */
export const GENERAL_DANGER_SIGN_FIELDS = [
  "unableToDrinkOrBreastfeed",
  "vomitsEverything",
  "convulsions",
  "lethargicOrUnconscious",
] as const;

export type AssessmentAnswers = Record<string, unknown>;

/** An answer that states a finding, as opposed to one that records it was not looked for. */
export function isAnswered(value: unknown): boolean {
  return value === "yes" || value === "no" || value === "unknown";
}

export function isPositive(value: unknown): boolean {
  return value === "yes";
}

export interface DangerSignStatus {
  /** Signs answered "yes". */
  present: string[];
  /** Signs the clinician has not yet addressed at all. */
  unassessed: string[];
  /** True once every general danger sign carries an answer. */
  complete: boolean;
}

/**
 * The state of the general danger signs.
 *
 * "None present" and "none asked" produce very different displays, because the second is
 * not reassurance. This is the client-side view for prompting the clinician; the
 * authoritative evaluation happens server-side against the governed rule content, which is
 * why nothing here classifies or advises.
 */
export function dangerSignStatus(answers: AssessmentAnswers): DangerSignStatus {
  const present: string[] = [];
  const unassessed: string[] = [];

  for (const field of GENERAL_DANGER_SIGN_FIELDS) {
    const value = answers[field];
    if (isPositive(value)) {
      present.push(field);
    } else if (!isAnswered(value)) {
      unassessed.push(field);
    }
  }

  return { present, unassessed, complete: unassessed.length === 0 };
}

export const DANGER_SIGN_LABELS: Record<string, string> = {
  unableToDrinkOrBreastfeed: "Unable to drink or breastfeed",
  vomitsEverything: "Vomits everything",
  convulsions: "Convulsions",
  lethargicOrUnconscious: "Lethargic or unconscious",
};

/**
 * Which symptom branches to show. A branch whose screening question is answered "no" is
 * collapsed rather than hidden: the answer stays visible and reversible, because hiding a
 * negative makes it look unasked.
 */
export function branchState(answers: AssessmentAnswers, sectionId: string): "open" | "collapsed" | "unasked" {
  const screening: Record<string, string> = {
    cough: "cough",
    diarrhoea: "diarrhoea",
    fever: "fever",
    ear: "earProblem",
  };
  const field = screening[sectionId];
  if (!field) return "open";
  const value = answers[field];
  if (isPositive(value)) return "open";
  if (isAnswered(value)) return "collapsed";
  return "unasked";
}
