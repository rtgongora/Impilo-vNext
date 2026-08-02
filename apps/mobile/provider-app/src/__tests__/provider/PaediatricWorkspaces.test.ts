import { describe, expect, it } from "vitest";
import { doseDetail, imnciRank, imnciTone } from "../../screens/provider/PaediatricWorkspaces";
import {
  giveableToday,
  withheldToday,
  unassessedSigns,
  ageDaysFromDateOfBirth,
  isEngineUnavailable,
  type ForecastDose,
  type ImmunisationForecast,
  type ImnciOutcome,
} from "../../services/paediatricService";

/**
 * Parity is about the clinical safety properties, not the layout. These assert that the phone
 * makes the same distinctions the web surface and the engines make — an unanswered question is
 * never a reassuring answer, and a dose that would not immunise never looks giveable.
 */

function outcome(over: Partial<ImnciOutcome> = {}): ImnciOutcome {
  return {
    table_id: "IMNCI_PNEUMONIA",
    axis: "Pneumonia severity",
    status: "OK",
    classification: "NO_PNEUMONIA",
    classification_name: "No pneumonia: cough or cold",
    colour: "GREEN",
    provisional: false,
    actionable: true,
    unresolved_tiers: [],
    missing_inputs: [],
    waived_criteria: [],
    action: "Home care and advice.",
    treatments: [],
    referral_required: false,
    urgent_referral: false,
    rationale: null,
    ...over,
  };
}

function dose(over: Partial<ForecastDose> = {}): ForecastDose {
  return {
    series: "PENTA",
    antigen: "Pentavalent (DTP-HepB-Hib)",
    dose_number: 2,
    status: "DUE",
    actionable_today: true,
    earliest_date: null,
    recommended_date: null,
    overdue_after: null,
    latest_useful_date: null,
    days_overdue: null,
    given_on: null,
    rationale: null,
    ...over,
  };
}

function forecast(doses: ForecastDose[]): ImmunisationForecast {
  return {
    date_of_birth: "2026-01-01",
    as_of: "2026-08-02",
    age_days: 213,
    doses,
    due_now: [],
    any_overdue: false,
    provisional: false,
    provisional_reasons: [],
    schedule_id: "zw-epi",
    schedule_version: "engineering-seed-1.0.0",
    approval_status: "ENGINEERING_SEED",
    note: null,
  };
}

describe("an unresolved classification is never the green row on a phone either", () => {
  it("tones INDETERMINATE as unresolved", () => {
    expect(imnciTone(outcome({ status: "INDETERMINATE", classification: null, colour: null }))).toBe(
      "unresolved",
    );
  });

  it("stays unresolved when a colour is present alongside INDETERMINATE", () => {
    expect(imnciTone(outcome({ status: "INDETERMINATE", colour: "GREEN" }))).toBe("unresolved");
  });

  it("treats a resolved row with no colour as unresolved rather than guessing a band", () => {
    expect(imnciTone(outcome({ colour: null }))).toBe("unresolved");
  });

  it("sorts unresolved above treat-here so a phone screen cannot bury it below the fold", () => {
    const unresolved = outcome({ status: "INDETERMINATE", classification: null, colour: null });
    const treat = outcome({ colour: "YELLOW", classification: "PNEUMONIA" });
    const critical = outcome({ colour: "PINK", classification: "SEVERE_PNEUMONIA" });
    expect(imnciRank(unresolved)).toBeLessThan(imnciRank(treat));
    expect(imnciRank(critical)).toBeLessThan(imnciRank(unresolved));
  });
});

describe("a dose that would not immunise never looks giveable", () => {
  it("filters the give-now list on the engine's verdict alone", () => {
    const f = forecast([
      dose({ series: "PENTA", status: "DUE", actionable_today: true }),
      dose({ series: "OPV", status: "BLOCKED_BY_INTERVAL", actionable_today: false }),
      dose({ series: "ROTA", status: "AGED_OUT", actionable_today: false }),
    ]);
    expect(giveableToday(f).map((d) => d.series)).toEqual(["PENTA"]);
    expect(withheldToday(f).map((d) => d.series)).toEqual(["OPV", "ROTA"]);
  });

  it("carries the date the interval gate opens", () => {
    const detail = doseDetail(
      dose({ status: "BLOCKED_BY_INTERVAL", actionable_today: false, earliest_date: "2026-08-20" }),
    );
    expect(detail).toContain("2026-08-20");
    expect(detail).toContain("would not immunise");
  });

  it("carries the safety reason for a closed window", () => {
    const detail = doseDetail(
      dose({
        series: "ROTA",
        status: "AGED_OUT",
        actionable_today: false,
        rationale: "Rotavirus is not given after 32 weeks; the risk of intussusception rises with age.",
      }),
    );
    expect(detail).toContain("intussusception");
  });

  it("names a closed window as a safety limit when no rationale is given", () => {
    expect(doseDetail(dose({ status: "AGED_OUT", actionable_today: false }))).toContain("safety limit");
  });
});

describe("the danger-sign screen keeps its unassessed list", () => {
  it("reads either field name the engine may use", () => {
    expect(unassessedSigns({ incomplete: true, unassessed_signs: ["convulsions"] })).toEqual([
      "convulsions",
    ]);
    expect(unassessedSigns({ incomplete: true, unassessed: ["stiffNeck"] })).toEqual(["stiffNeck"]);
    expect(unassessedSigns({ incomplete: false })).toEqual([]);
  });
});

describe("age routing", () => {
  it("computes age in days and refuses a future date of birth", () => {
    const now = new Date("2026-08-02T00:00:00Z");
    expect(ageDaysFromDateOfBirth("2026-08-01", now)).toBe(1);
    expect(ageDaysFromDateOfBirth("2026-01-01", now)).toBe(213);
    expect(ageDaysFromDateOfBirth("2027-01-01", now)).toBeNull();
    expect(ageDaysFromDateOfBirth(null, now)).toBeNull();
  });
});

describe("an unreachable engine is distinguishable from a clinical answer", () => {
  it("treats 502 and above as the engine being unavailable", () => {
    expect(isEngineUnavailable({ status: 502 })).toBe(true);
    expect(isEngineUnavailable({ status: 503 })).toBe(true);
    // A 400 is the caller's to fix, not an outage.
    expect(isEngineUnavailable({ status: 400 })).toBe(false);
    expect(isEngineUnavailable(new Error("boom"))).toBe(false);
  });
});
