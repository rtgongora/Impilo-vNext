import type { ForecastDose, ImmunisationForecast } from "@/hooks/queries/usePaediatricDecisionSupport";

/**
 * How a forecast dose is presented.
 *
 * The engine's own documentation states the two asymmetric failure modes this display has to
 * preserve, and both are about a dose looking giveable when it is not:
 *
 * - **A dose given before its minimum age, or too soon after the previous one, does not
 *   immunise.** It is discarded and the child must be re-vaccinated. So a `BLOCKED_BY_INTERVAL`
 *   dose must never sit in a "give today" list — and, because a health worker who is only told
 *   "not yet" will bring the child back tomorrow, it must carry **the date the gate opens**.
 * - **A dose whose window has closed is closed.** The rotavirus ceilings are a safety limit: the
 *   excess risk of intussusception rises with age at first dose, so an incomplete course is not
 *   caught up. An `AGED_OUT` dose must show the safety reason, not merely disappear.
 *
 * The single rule that follows: **`actionableToday` is the engine's verdict and this module never
 * second-guesses it.** Nothing here derives giveability from a status name or a date comparison.
 * A local re-derivation would be a second implementation of the interval and ceiling rules in the
 * browser, and the day it drifted from the governed schedule is the day a child is recorded as
 * protected who is not.
 */

export type DoseTone = "give" | "overdue" | "blocked" | "closed" | "given" | "invalid" | "future" | "inapplicable";

export interface DosePresentation {
  tone: DoseTone;
  label: string;
  /** The chip a health worker reads first. */
  chipLabel: string;
  chipClass: string;
  containerClass: string;
  /**
   * The sentence that explains what to do, or why nothing can be done. Never empty for a dose
   * that is not giveable — "not due" with no reason is what sends a child back tomorrow.
   */
  detail: string;
  /** Whether this dose belongs in a "give at this contact" list. Mirrors the engine, never derived. */
  actionable: boolean;
}

const STYLES: Record<DoseTone, { container: string; chip: string }> = {
  overdue: {
    container: "border-rose-400 bg-rose-50 dark:border-rose-600 dark:bg-rose-950/40",
    chip: "bg-rose-600 text-white",
  },
  give: {
    container: "border-emerald-400 bg-emerald-50 dark:border-emerald-600 dark:bg-emerald-950/40",
    chip: "bg-emerald-600 text-white",
  },
  invalid: {
    container: "border-amber-400 bg-amber-50 dark:border-amber-600 dark:bg-amber-950/40",
    chip: "bg-amber-500 text-amber-950",
  },
  // Blocked and closed are deliberately visually quiet and deliberately not green. Neither is an
  // action, and a health worker scanning for something to do must not land on one.
  blocked: {
    container: "border-slate-400 border-dashed bg-slate-50 dark:border-slate-500 dark:bg-slate-900/60",
    chip: "bg-slate-700 text-white",
  },
  closed: {
    container: "border-slate-400 bg-slate-100 dark:border-slate-600 dark:bg-slate-900/80",
    chip: "bg-slate-600 text-white",
  },
  given: {
    container: "border-border bg-background",
    chip: "bg-muted text-muted-foreground",
  },
  future: {
    container: "border-border bg-background",
    chip: "bg-muted text-muted-foreground",
  },
  inapplicable: {
    container: "border-border bg-muted/40",
    chip: "bg-muted text-muted-foreground",
  },
};

export function doseName(dose: ForecastDose): string {
  const antigen = dose.antigen ?? dose.series;
  return dose.doseNumber !== null ? `${antigen} dose ${dose.doseNumber}` : antigen;
}

/**
 * Dates are formatted day-month-year and the locale is pinned rather than taken from the runtime.
 *
 * A clinical date must not be ambiguous. Left to the ambient locale the same gate-open date reads
 * as 03/04/2026 on one machine and 04/03/2026 on another, and a health worker reading the second
 * brings the child back a month early for a dose that will not immunise them. Spelling the month
 * removes the ambiguity entirely, and pinning the locale means the format does not depend on how
 * the browser or the server happens to be configured.
 */
function formatDate(iso: string | null): string | null {
  if (!iso) return null;
  const d = new Date(`${iso}T00:00:00Z`);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });
}

export function presentDose(dose: ForecastDose): DosePresentation {
  const name = doseName(dose);

  switch (dose.status) {
    case "OVERDUE": {
      const late = dose.daysOverdue !== null ? ` — ${dose.daysOverdue} day${dose.daysOverdue === 1 ? "" : "s"} overdue` : "";
      return present("overdue", name, "Overdue", `Give at this contact${late}.`, dose);
    }
    case "DUE":
      return present("give", name, "Due", "Due now — give at this contact.", dose);

    case "INVALID_REPEAT_REQUIRED":
      return present(
        "invalid",
        name,
        "Repeat required",
        dose.rationale ??
          "This dose was given before it could immunise the child and does not count. It must be repeated.",
        dose,
      );

    case "BLOCKED_BY_INTERVAL": {
      // The date the gate opens is the whole point. Without it the child comes back tomorrow and
      // is turned away again, or worse, is given a dose that will not immunise them.
      const opens = formatDate(dose.earliestDate);
      return present(
        "blocked",
        name,
        "Not yet — interval",
        opens
          ? `The child is old enough, but the minimum interval since the previous dose has not elapsed. Giving it today would not immunise them. Due from ${opens}.`
          : "The minimum interval since the previous dose has not elapsed. Giving it today would not immunise them.",
        dose,
      );
    }

    case "AGED_OUT": {
      const closed = formatDate(dose.latestUsefulDate);
      return present(
        "closed",
        name,
        "Window closed",
        // The safety reason travels with the dose. "Too old" alone reads as a technicality and
        // invites someone to give it anyway.
        dose.rationale ??
          `The window for this dose has closed${closed ? ` (after ${closed})` : ""} and it will not be given. This is a safety limit, not a scheduling one.`,
        dose,
      );
    }

    case "AWAITING_PREVIOUS_DOSE":
      return present(
        "blocked",
        name,
        "Awaiting previous dose",
        dose.rationale ?? "The previous dose in this series has not been given, so this one cannot be.",
        dose,
      );

    case "NOT_YET_DUE": {
      const from = formatDate(dose.earliestDate ?? dose.recommendedDate);
      return present("future", name, "Not yet due", from ? `Due from ${from}.` : "Not yet due.", dose);
    }

    case "GIVEN": {
      const on = formatDate(dose.givenOn);
      return present("given", name, "Given", on ? `Given ${on}.` : "Given.", dose);
    }

    case "GIVEN_UNVERIFIED": {
      const on = formatDate(dose.givenOn);
      return present(
        "given",
        name,
        "Given — unverified",
        `Recorded from a card or report that nobody has verified${on ? ` (${on})` : ""}. Verify against the child health card.`,
        dose,
      );
    }

    case "CONTRAINDICATED":
      return present("inapplicable", name, "Contraindicated", dose.rationale ?? "Recorded as contraindicated for this child.", dose);

    case "NOT_APPLICABLE":
      return present("inapplicable", name, "Not applicable", dose.rationale ?? "This antigen does not apply to this child.", dose);

    default:
      // An unrecognised status is never rendered as giveable and never rendered as fine. Deferring
      // to actionableToday keeps this consistent with the engine even for a status added later.
      return present(
        dose.actionableToday ? "give" : "blocked",
        name,
        dose.status,
        dose.rationale ?? "This dose's status is not recognised by this screen. Check the immunisation record.",
        dose,
      );
  }
}

function present(
  tone: DoseTone,
  label: string,
  chipLabel: string,
  detail: string,
  dose: ForecastDose,
): DosePresentation {
  return {
    tone,
    label,
    chipLabel,
    chipClass: STYLES[tone].chip,
    containerClass: STYLES[tone].container,
    detail,
    // Straight from the engine. Never `tone === "give"`, which would let a presentation choice
    // silently decide whether a dose is offered.
    actionable: dose.actionableToday,
  };
}

/**
 * The doses to offer at this contact.
 *
 * Filtered on the engine's verdict alone. A dose that would not immunise the child never appears
 * here, whatever its status name suggests.
 */
export function actionableDoses(forecast: ImmunisationForecast): ForecastDose[] {
  return forecast.doses.filter((d) => d.actionableToday);
}

/** Doses that are blocked or closed — shown, because a health worker needs to know why not. */
export function withheldDoses(forecast: ImmunisationForecast): ForecastDose[] {
  return forecast.doses.filter(
    (d) =>
      !d.actionableToday &&
      (d.status === "BLOCKED_BY_INTERVAL" || d.status === "AGED_OUT" || d.status === "AWAITING_PREVIOUS_DOSE"),
  );
}

export function doseRank(dose: ForecastDose): number {
  switch (dose.status) {
    case "OVERDUE":
      return 0;
    case "DUE":
      return 1;
    case "INVALID_REPEAT_REQUIRED":
      return 2;
    case "BLOCKED_BY_INTERVAL":
      return 3;
    case "AWAITING_PREVIOUS_DOSE":
      return 4;
    case "AGED_OUT":
      return 5;
    case "NOT_YET_DUE":
      return 6;
    case "GIVEN_UNVERIFIED":
      return 7;
    case "GIVEN":
      return 8;
    default:
      return 9;
  }
}

export function sortDoses(doses: ForecastDose[]): ForecastDose[] {
  return [...doses].sort((a, b) => doseRank(a) - doseRank(b) || a.series.localeCompare(b.series));
}

/**
 * The immunisation line for the "what is due today" panel.
 *
 * Returns null when there is nothing to say, so an empty panel keeps meaning "nothing
 * outstanding". It never returns a reassuring line built on a forecast that failed — that case is
 * the caller's `unavailable` branch, not this one.
 */
export function forecastSummary(forecast: ImmunisationForecast): {
  urgency: "overdue" | "due" | "watch";
  detail: string;
} | null {
  const actionable = actionableDoses(forecast);
  const withheld = withheldDoses(forecast);

  if (actionable.length === 0 && withheld.length === 0) return null;

  if (actionable.length === 0) {
    // Nothing to give, but something is being withheld and the reason matters — particularly a
    // closed window, which is a permanent gap in this child's protection.
    const closed = withheld.filter((d) => d.status === "AGED_OUT");
    return {
      urgency: "watch",
      detail: closed.length
        ? `No dose can be given today. ${closed.map(doseName).join(", ")} — the window has closed and ${closed.length === 1 ? "it" : "they"} will not be given.`
        : `No dose can be given today. ${withheld.map(doseName).join(", ")} ${withheld.length === 1 ? "is" : "are"} withheld; see the immunisation record for when ${withheld.length === 1 ? "it" : "they"} become due.`,
    };
  }

  const overdue = actionable.filter((d) => d.status === "OVERDUE");
  const names = actionable.map(doseName).join(", ");

  return {
    urgency: overdue.length > 0 ? "overdue" : "due",
    detail:
      overdue.length > 0
        ? `${names}. ${overdue.length} overdue — give at this contact.`
        : `${names} — due at this contact.`,
  };
}
