import type { AgeFacts } from "./paediatric-age";

/**
 * What is due at this contact.
 *
 * A child brought in for fever is also a child who may be overdue a weight, a vaccine or a
 * follow-up, and the commonest way those are missed is that nobody was told to look. This
 * composes the answer from what the record already knows, so the opportunity is taken while
 * the child is in the room.
 *
 * Everything here derives from data the platform actually holds. Where a determination needs
 * something not yet built — the national immunisation schedule and its forecast — the item
 * says what it cannot determine rather than implying nothing is due. An empty panel must mean
 * "nothing outstanding", never "not checked".
 */

export type DueUrgency = "overdue" | "due" | "watch" | "unknown";

export interface DueItem {
  id: string;
  label: string;
  detail: string;
  urgency: DueUrgency;
  /** Where the clinician goes to act on it, when there is somewhere to go. */
  href?: string;
}

/**
 * What the workspace knows about the child's nutrition treatment.
 *
 * Only the facts the episode header carries. The attendance banding — traced, defaulting — is a
 * governed judgement made server-side against the rules the child was admitted under, and is
 * shown on the treatment screen. Re-deriving it here from a date would let two surfaces disagree
 * about whether the same child had defaulted.
 */
export interface ImamSummary {
  enrolled: boolean;
  programme: string | null;
  nextReviewDue: string | null;
  /** The episode list could not be read; nothing about enrolment has been established. */
  unavailable: boolean;
}

export interface DueTodayInput {
  patientId: string;
  age: AgeFacts;
  lastMeasuredAt?: string | null;
  hasAnyGrowthMeasurement: boolean;
  latestWeightForAgeZ?: number | null;
  immunisationCount: number;
  imam?: ImamSummary;
  now?: Date;
}

/**
 * How long a growth measurement stays current, by age. Growth is fastest in infancy, so a
 * measurement goes stale fastest there — a six-week-old weighed two months ago has no usable
 * current weight, and a weight is what every paediatric dose is calculated from.
 */
function growthValidityDays(age: AgeFacts): number {
  if (age.ageDays === null) return 90;
  if (age.ageDays <= 59) return 14;
  if (age.ageDays <= 364) return 30;
  if (age.ageDays <= 1824) return 90;
  return 180;
}

export function computeDueToday(input: DueTodayInput): DueItem[] {
  const now = input.now ?? new Date();
  const items: DueItem[] = [];
  const { age, patientId } = input;

  // Growth — the one item that gates safe prescribing.
  if (!input.hasAnyGrowthMeasurement) {
    items.push({
      id: "growth-none",
      label: "Weight and growth",
      detail: "No growth measurement has ever been recorded. A dose cannot be calculated without a current weight.",
      urgency: "overdue",
      href: `/ehr/${patientId}/growth-chart`,
    });
  } else if (input.lastMeasuredAt) {
    const measuredAt = new Date(input.lastMeasuredAt);
    const daysSince = Math.floor((now.getTime() - measuredAt.getTime()) / 86_400_000);
    const validFor = growthValidityDays(age);
    if (daysSince > validFor) {
      items.push({
        id: "growth-stale",
        label: "Weight and growth",
        detail: `Last measured ${daysSince} days ago; at this age a measurement older than ${validFor} days is no longer a current weight.`,
        urgency: "overdue",
        href: `/ehr/${patientId}/growth-chart`,
      });
    } else if (daysSince > Math.floor(validFor / 2)) {
      items.push({
        id: "growth-due",
        label: "Weight and growth",
        detail: `Last measured ${daysSince} days ago — worth repeating at this contact.`,
        urgency: "due",
        href: `/ehr/${patientId}/growth-chart`,
      });
    }
  }

  // Nutrition treatment. An enrolled child's review outranks everything else here, because a
  // missed one is how a severely malnourished child disappears from view.
  const imam = input.imam;
  if (imam?.unavailable) {
    items.push({
      id: "nutrition-unknown",
      label: "Nutrition treatment",
      detail:
        "Whether this child is in a nutrition programme could not be read. Do not assume they are not — check before discharging them from this contact.",
      urgency: "unknown",
      href: `/ehr/${patientId}/imam`,
    });
  } else if (imam?.enrolled) {
    const due = imam.nextReviewDue ? new Date(`${imam.nextReviewDue}T00:00:00Z`) : null;
    const daysLate =
      due && !Number.isNaN(due.getTime())
        ? Math.floor((now.getTime() - due.getTime()) / 86_400_000)
        : null;
    if (daysLate === null) {
      items.push({
        id: "nutrition-treatment-unscheduled",
        label: "Nutrition treatment",
        detail: `In ${imam.programme ?? "a nutrition programme"} with no next review scheduled. An episode with no schedule is a child nobody is expecting back.`,
        urgency: "overdue",
        href: `/ehr/${patientId}/imam`,
      });
    } else if (daysLate > 0) {
      items.push({
        id: "nutrition-treatment-overdue",
        label: "Nutrition treatment",
        detail: `In ${imam.programme ?? "a nutrition programme"}; the review was due ${imam.nextReviewDue} — ${daysLate} day${daysLate === 1 ? "" : "s"} ago. Record it or record the miss; leaving it blank looks the same as not yet due.`,
        urgency: "overdue",
        href: `/ehr/${patientId}/imam`,
      });
    } else if (daysLate === 0) {
      items.push({
        id: "nutrition-treatment-due",
        label: "Nutrition treatment",
        detail: `In ${imam.programme ?? "a nutrition programme"}; this review is due today.`,
        urgency: "due",
        href: `/ehr/${patientId}/imam`,
      });
    }
  } else if (typeof input.latestWeightForAgeZ === "number" && input.latestWeightForAgeZ < -2) {
    // Malnourished on the record and in no programme — the gap this whole feature exists to close.
    items.push({
      id: "nutrition-followup",
      label: "Nutrition treatment",
      detail: `Weight-for-age is ${input.latestWeightForAgeZ.toFixed(2)} SD and this child is in no nutrition programme. Check MUAC and oedema and enrol them.`,
      urgency: "overdue",
      href: `/ehr/${patientId}/imam`,
    });
  }

  // Immunisation. The forecast is governed content that is not built yet, so this states
  // what it can see and is explicit about what it cannot determine — an honest "unknown"
  // rather than a silent all-clear.
  if (age.paediatric) {
    items.push(
      input.immunisationCount === 0
        ? {
            id: "immunisation-none",
            label: "Immunisation",
            detail:
              "No immunisation doses are recorded for this child. Check the child health card; a dose given elsewhere still needs recording.",
            urgency: "overdue",
            href: `/ehr/${patientId}/immunizations`,
          }
        : {
            id: "immunisation-review",
            label: "Immunisation",
            detail: `${input.immunisationCount} dose${input.immunisationCount === 1 ? "" : "s"} recorded. Due and overdue vaccines cannot be determined yet — the national schedule forecast is not available in this deployment.`,
            urgency: "unknown",
            href: `/ehr/${patientId}/immunizations`,
          },
    );
  }

  // Age-specific prompts that need no stored data to be worth raising.
  if (age.isSickYoungInfantWindow) {
    items.push({
      id: "young-infant-danger-signs",
      label: "Young infant danger signs",
      detail: "Under 60 days old — check feeding, temperature, breathing and movement, whatever the presenting complaint.",
      urgency: "due",
    });
  } else if (age.isUnderFive) {
    items.push({
      id: "under-five-danger-signs",
      label: "General danger signs",
      detail: "Under five — check for inability to drink, vomiting everything, convulsions and lethargy.",
      urgency: "due",
    });
  }

  return items;
}

export function urgencyRank(urgency: DueUrgency): number {
  switch (urgency) {
    case "overdue":
      return 0;
    case "due":
      return 1;
    case "unknown":
      return 2;
    default:
      return 3;
  }
}

export function sortDueItems(items: DueItem[]): DueItem[] {
  return [...items].sort((a, b) => urgencyRank(a.urgency) - urgencyRank(b.urgency));
}
