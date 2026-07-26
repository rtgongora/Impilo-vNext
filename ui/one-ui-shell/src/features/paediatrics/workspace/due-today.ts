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

export interface DueTodayInput {
  patientId: string;
  age: AgeFacts;
  lastMeasuredAt?: string | null;
  hasAnyGrowthMeasurement: boolean;
  latestWeightForAgeZ?: number | null;
  immunisationCount: number;
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

  // Nutrition follow-up on a low score already recorded.
  if (typeof input.latestWeightForAgeZ === "number" && input.latestWeightForAgeZ < -2) {
    items.push({
      id: "nutrition-followup",
      label: "Nutrition review",
      detail: `Weight-for-age is ${input.latestWeightForAgeZ.toFixed(2)} SD. Assess feeding and check MUAC and oedema.`,
      urgency: "overdue",
      href: `/ehr/${patientId}/growth-chart`,
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
