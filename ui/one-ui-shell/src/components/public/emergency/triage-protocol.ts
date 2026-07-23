/**
 * Nompilo emergency triage protocol — a DETERMINISTIC, clinically conservative
 * danger-sign decision flow presented conversationally (one question at a time).
 *
 * This is deliberately NOT an LLM: emergency danger-sign screening must be
 * predictable, testable and safe. Nompilo here TRIAGES and GUIDES — it never
 * diagnoses. Any danger sign escalates immediately (call-first banner + safe
 * actions) and shortens the remaining flow to the minimum needed to submit a
 * real assistance request (callback + location).
 *
 * The category values map 1:1 to daidzai-service's emergency_category enum so
 * the submission lands on the real intake without translation.
 */

export type TriageStepId =
  | "situation"
  | "conscious"
  | "breathing"
  | "bleeding"
  | "redFlags"
  | "onset"
  | "age"
  | "pregnancy"
  | "conditions"
  | "alone"
  | "callback"
  | "location"
  | "review";

export interface TriageOption {
  value: string;
  label: string;
  /** Marks the "none of these" style option in multi-selects. */
  exclusive?: boolean;
}

export interface TriageQuestion {
  id: TriageStepId;
  prompt: string;
  help?: string;
  options?: TriageOption[];
  multi?: boolean;
  /** Steps answered via dedicated inputs (callback/location/review) have no options. */
  kind: "choice" | "callback" | "location" | "review";
}

export interface TriageAnswers {
  situation?: string;
  conscious?: string;
  breathing?: string;
  bleeding?: string;
  redFlags?: string[];
  onset?: string;
  age?: string;
  pregnancy?: string;
  conditions?: string[];
  alone?: string;
  callbackNumber?: string;
  locationDescription?: string;
  province?: string;
  atFacility?: string;
  lat?: number;
  lng?: number;
}

export const TRIAGE_QUESTIONS: Record<TriageStepId, TriageQuestion> = {
  situation: {
    id: "situation",
    kind: "choice",
    prompt: "What has happened? Choose the closest match.",
    options: [
      { value: "MEDICAL", label: "Sudden illness" },
      { value: "TRAUMA", label: "Injury or accident" },
      { value: "OBSTETRIC", label: "Pregnancy or newborn" },
      { value: "CARDIAC", label: "Chest pain / heart" },
      { value: "MENTAL_HEALTH", label: "Mental-health crisis" },
      { value: "POISONING", label: "Poisoning" },
      { value: "VIOLENCE", label: "Violence or assault" },
      { value: "OTHER", label: "Something else" },
    ],
  },
  conscious: {
    id: "conscious",
    kind: "choice",
    prompt: "Is the person awake and responding to you?",
    options: [
      { value: "yes", label: "Yes, awake and responding" },
      { value: "no", label: "No — unconscious or not responding" },
      { value: "unsure", label: "Not sure" },
    ],
  },
  breathing: {
    id: "breathing",
    kind: "choice",
    prompt: "Are they breathing normally?",
    options: [
      { value: "normal", label: "Breathing normally" },
      { value: "difficulty", label: "Struggling to breathe" },
      { value: "not_breathing", label: "Not breathing" },
      { value: "unsure", label: "Not sure" },
    ],
  },
  bleeding: {
    id: "bleeding",
    kind: "choice",
    prompt: "Is there bleeding?",
    options: [
      { value: "none", label: "No bleeding" },
      { value: "minor", label: "Some bleeding, controllable" },
      { value: "severe", label: "Severe bleeding that won't stop" },
    ],
  },
  redFlags: {
    id: "redFlags",
    kind: "choice",
    multi: true,
    prompt: "Do any of these apply right now? Choose all that you see.",
    options: [
      { value: "chest_pain", label: "Chest pain or pressure" },
      { value: "weakness_speech", label: "Sudden weakness, confusion or trouble speaking" },
      { value: "seizure", label: "Seizure or fitting" },
      { value: "infant_fever", label: "High fever in a baby" },
      { value: "reduced_baby_movement", label: "Pregnancy: reduced baby movement or heavy bleeding" },
      { value: "self_harm", label: "Risk of harm to self or others" },
      { value: "none", label: "None of these", exclusive: true },
    ],
  },
  onset: {
    id: "onset",
    kind: "choice",
    prompt: "When did it start?",
    options: [
      { value: "now", label: "Just now" },
      { value: "hour", label: "Within the last hour" },
      { value: "today", label: "Earlier today" },
      { value: "longer", label: "A day or more ago" },
    ],
  },
  age: {
    id: "age",
    kind: "choice",
    prompt: "Roughly how old is the person?",
    options: [
      { value: "infant", label: "Baby (under 1 year)" },
      { value: "child", label: "Child (1–12)" },
      { value: "adolescent", label: "Teenager" },
      { value: "adult", label: "Adult" },
      { value: "older_adult", label: "Older adult (65+)" },
    ],
  },
  pregnancy: {
    id: "pregnancy",
    kind: "choice",
    prompt: "Is the person pregnant, or recently gave birth?",
    options: [
      { value: "yes", label: "Yes, pregnant" },
      { value: "recent", label: "Gave birth in the last 6 weeks" },
      { value: "no", label: "No / not applicable" },
    ],
  },
  conditions: {
    id: "conditions",
    kind: "choice",
    multi: true,
    prompt: "Any known conditions or important medicines? Choose what applies.",
    help: "This helps responders prepare. Skip if you don't know.",
    options: [
      { value: "diabetes", label: "Diabetes" },
      { value: "hypertension", label: "High blood pressure / heart condition" },
      { value: "asthma", label: "Asthma / lung condition" },
      { value: "epilepsy", label: "Epilepsy" },
      { value: "hiv", label: "On ART / chronic treatment" },
      { value: "allergy", label: "Serious allergy" },
      { value: "none", label: "None / don't know", exclusive: true },
    ],
  },
  alone: {
    id: "alone",
    kind: "choice",
    prompt: "Is the person alone, or is someone with them?",
    options: [
      { value: "no", label: "Someone is with them" },
      { value: "yes", label: "They are alone" },
    ],
  },
  callback: {
    id: "callback",
    kind: "callback",
    prompt: "What number can a responder call you back on?",
    help: "A responder calls this number to confirm before any help is arranged.",
  },
  location: {
    id: "location",
    kind: "location",
    prompt: "Where is the person right now?",
    help: "Share your device location, or describe the place — a landmark, address, clinic name or area.",
  },
  review: {
    id: "review",
    kind: "review",
    prompt: "Here is your emergency summary. Send it so a responder can call you back.",
  },
};

/** Question order for the full (non-escalated) flow. */
const FULL_ORDER: TriageStepId[] = [
  "situation",
  "conscious",
  "breathing",
  "bleeding",
  "redFlags",
  "onset",
  "age",
  "pregnancy",
  "conditions",
  "alone",
  "callback",
  "location",
  "review",
];

/** Once danger is confirmed, stop questioning — go straight to contact + place. */
const ESCALATED_ORDER: TriageStepId[] = [
  "situation",
  "conscious",
  "breathing",
  "bleeding",
  "redFlags",
  "callback",
  "location",
  "review",
];

export interface DangerAssessment {
  danger: boolean;
  /** Machine-readable reasons, first is primary. */
  reasons: string[];
}

export function assessDanger(answers: TriageAnswers): DangerAssessment {
  const reasons: string[] = [];
  if (answers.conscious === "no") reasons.push("unconscious");
  if (answers.breathing === "not_breathing") reasons.push("not_breathing");
  if (answers.breathing === "difficulty") reasons.push("breathing_difficulty");
  if (answers.bleeding === "severe") reasons.push("severe_bleeding");
  for (const flag of answers.redFlags ?? []) {
    if (flag !== "none") reasons.push(flag);
  }
  if (answers.situation === "CARDIAC" && !reasons.includes("chest_pain")) reasons.push("chest_pain");
  if (answers.situation === "POISONING") reasons.push("poisoning");
  return { danger: reasons.length > 0, reasons };
}

/**
 * Urgency taxonomy (gateway doctrine §13.5): emergency / urgency / routine. Nompilo
 * classifies but always errs toward safety — anything with a danger sign is an emergency,
 * and several concerning-but-not-danger presentations are treated as urgency (prompt,
 * same-day care) rather than routine. This drives the review-step recommendation, never a
 * diagnosis.
 */
export type UrgencyTier = "emergency" | "urgency" | "routine";

export interface UrgencyAssessment {
  tier: UrgencyTier;
  /** One-line, plain-language reason for the tier. */
  reason: string;
}

export function classifyUrgency(answers: TriageAnswers): UrgencyAssessment {
  if (assessDanger(answers).danger) {
    return { tier: "emergency", reason: "Danger signs are present — this needs help now." };
  }
  // Concerning-but-not-danger presentations that still need prompt (same-day) attention.
  const recent = answers.onset === "now" || answers.onset === "hour";
  if (answers.pregnancy === "yes" || answers.pregnancy === "recent") {
    return { tier: "urgency", reason: "Pregnancy or recent birth — get checked the same day." };
  }
  if (answers.situation === "MENTAL_HEALTH") {
    return { tier: "urgency", reason: "A mental-health concern deserves prompt support." };
  }
  if (answers.bleeding === "minor" && answers.situation === "TRAUMA") {
    return { tier: "urgency", reason: "An injury with bleeding should be seen the same day." };
  }
  if (recent && (answers.age === "infant" || answers.age === "older_adult")) {
    return { tier: "urgency", reason: "New symptoms in a baby or older adult need prompt care." };
  }
  if (recent && (answers.situation === "MEDICAL" || answers.situation === "CARDIAC")) {
    return { tier: "urgency", reason: "New symptoms — seek care today to be safe." };
  }
  return {
    tier: "routine",
    reason: "No danger signs reported — routine care or self-care is usually appropriate.",
  };
}

/** Plain-language next-step guidance per urgency tier (non-diagnostic). */
export const URGENCY_GUIDANCE: Record<UrgencyTier, { headline: string; action: string }> = {
  emergency: {
    headline: "Get help now",
    action: "Call the emergency numbers or go to the nearest emergency department immediately.",
  },
  urgency: {
    headline: "Seek care today",
    action:
      "Visit a clinic or arrange a same-day consultation. Find the nearest facility below, and call the emergency numbers if things get worse.",
  },
  routine: {
    headline: "Routine care",
    action:
      "You can book a normal appointment or use self-care. Find a facility below, and return here or call for help if anything changes.",
  },
};

/** Simple, safe, non-diagnostic first-aid guidance per danger reason. */
export const SAFE_ACTIONS: Record<string, string> = {
  unconscious:
    "Turn the person gently onto their side (recovery position) and keep the airway clear. Do not give food or drink.",
  not_breathing:
    "If you are trained in CPR, start chest compressions now. Put your phone on speaker while you call for help.",
  breathing_difficulty:
    "Help them sit upright and stay calm. Loosen any tight clothing around the neck and chest.",
  severe_bleeding:
    "Press firmly on the wound with a clean cloth and keep pressing without lifting it to check.",
  chest_pain: "Help them sit down and rest. Loosen tight clothing. Do not leave them alone.",
  weakness_speech:
    "Note the time the symptoms started — tell the responders. Help them lie safely on their side.",
  seizure:
    "Protect the head, move hard objects away, do not restrain them and never put anything in the mouth.",
  infant_fever: "Undress the baby to a single layer and keep them hydrated while you seek help now.",
  reduced_baby_movement: "Go to a maternity unit now — do not wait to see if it improves.",
  self_harm: "Stay with the person. Remove anything that could cause harm. Keep talking calmly.",
  poisoning: "Do not make them vomit. Keep the container or substance to show responders.",
};

function skips(answers: TriageAnswers, step: TriageStepId): boolean {
  if (step === "pregnancy") {
    // Only meaningful for adolescent/adult ages; the obstetric path implies yes.
    if (answers.situation === "OBSTETRIC") return true;
    return answers.age === "infant" || answers.age === "child";
  }
  return false;
}

/** The next unanswered step, honoring danger escalation and skip rules. */
export function nextStep(answers: TriageAnswers): TriageStepId {
  const { danger } = assessDanger(answers);
  const order = danger ? ESCALATED_ORDER : FULL_ORDER;
  for (const step of order) {
    if (skips(answers, step)) continue;
    if (step === "review") return "review";
    if (step === "callback") {
      if (!answers.callbackNumber) return step;
      continue;
    }
    if (step === "location") {
      const captured =
        Boolean(answers.locationDescription) ||
        answers.lat !== undefined ||
        Boolean(answers.province) ||
        Boolean(answers.atFacility);
      if (!captured) return step;
      continue;
    }
    if (answers[step as keyof TriageAnswers] === undefined) return step;
  }
  return "review";
}

function label(step: TriageStepId, value: string): string {
  const opt = TRIAGE_QUESTIONS[step].options?.find((o) => o.value === value);
  return opt?.label ?? value;
}

/** Structured, human-readable summary handed to the responder via the request. */
export function buildSummary(answers: TriageAnswers): string {
  const { danger, reasons } = assessDanger(answers);
  const lines: string[] = ["[Nompilo guided triage]"];
  lines.push(`Priority: ${danger ? "RED — danger signs reported" : "Standard — no danger signs reported"}`);
  if (reasons.length) {
    lines.push(`Danger signs: ${reasons.map((r) => r.replace(/_/g, " ")).join(", ")}`);
  }
  if (answers.situation) lines.push(`Situation: ${label("situation", answers.situation)}`);
  if (answers.conscious) lines.push(`Conscious: ${label("conscious", answers.conscious)}`);
  if (answers.breathing) lines.push(`Breathing: ${label("breathing", answers.breathing)}`);
  if (answers.bleeding) lines.push(`Bleeding: ${label("bleeding", answers.bleeding)}`);
  if (answers.onset) lines.push(`Started: ${label("onset", answers.onset)}`);
  if (answers.age) lines.push(`Age: ${label("age", answers.age)}`);
  if (answers.pregnancy && answers.pregnancy !== "no") {
    lines.push(`Pregnancy: ${label("pregnancy", answers.pregnancy)}`);
  }
  const conditions = (answers.conditions ?? []).filter((c) => c !== "none");
  if (conditions.length) {
    lines.push(`Conditions: ${conditions.map((c) => label("conditions", c)).join(", ")}`);
  }
  if (answers.alone) lines.push(`Alone: ${answers.alone === "yes" ? "yes" : "someone present"}`);
  if (answers.atFacility) lines.push(`At facility: ${answers.atFacility}`);
  if (answers.province) lines.push(`Province: ${answers.province}`);
  return lines.join("\n");
}

/** Map the finished triage onto the real anonymous SOS intake payload. */
export function toSosPayload(answers: TriageAnswers): Record<string, unknown> {
  const locationBits = [
    answers.atFacility ? `At facility: ${answers.atFacility}` : null,
    answers.locationDescription || null,
    answers.province || null,
  ].filter(Boolean);
  return {
    callbackNumber: answers.callbackNumber?.trim(),
    emergencyCategory: answers.situation ?? "OTHER",
    locationDescription: locationBits.length ? locationBits.join(" · ") : undefined,
    description: buildSummary(answers),
    lat: answers.lat,
    lng: answers.lng,
  };
}
