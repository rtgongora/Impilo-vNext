import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import type { ForecastDose, ImmunisationForecast } from "@/hooks/queries/usePaediatricDecisionSupport";
import {
  actionableDoses,
  doseRank,
  forecastSummary,
  presentDose,
  sortDoses,
  withheldDoses,
} from "../forecast-presentation";
import { ImmunisationForecastView } from "../ImmunisationForecastPanel";
import { computeDueToday } from "@/features/paediatrics/workspace/due-today";
import { ageFactsFor } from "@/features/paediatrics/workspace/paediatric-age";

/**
 * The property under test: a dose that would not immunise the child must never look actionable,
 * and must always say why not.
 */

function dose(over: Partial<ForecastDose> = {}): ForecastDose {
  return {
    series: "PENTA",
    antigen: "Pentavalent (DTP-HepB-Hib)",
    doseNumber: 2,
    status: "DUE",
    actionableToday: true,
    earliestDate: null,
    recommendedDate: null,
    overdueAfter: null,
    latestUsefulDate: null,
    daysOverdue: null,
    givenOn: null,
    rationale: null,
    ...over,
  };
}

function forecast(over: Partial<ImmunisationForecast> = {}): ImmunisationForecast {
  return {
    dateOfBirth: "2026-01-01",
    asOf: "2026-08-02",
    ageDays: 213,
    doses: [],
    dueNow: [],
    anyOverdue: false,
    provisional: false,
    provisionalReasons: [],
    scheduleId: "zw-epi",
    scheduleVersion: "engineering-seed-1.0.0",
    approvalStatus: "ENGINEERING_SEED",
    note: null,
    ...over,
  };
}

describe("a dose that would not immunise is never actionable", () => {
  it("shows BLOCKED_BY_INTERVAL with the date the gate opens", () => {
    const p = presentDose(dose({ status: "BLOCKED_BY_INTERVAL", actionableToday: false, earliestDate: "2026-08-20" }));
    expect(p.actionable).toBe(false);
    expect(p.tone).toBe("blocked");
    expect(p.detail).toContain("20 Aug 2026");
    // The reason matters as much as the date: old enough, but it would not immunise them.
    expect(p.detail).toContain("would not immunise");
  });

  it("shows AGED_OUT with the safety reason the engine gave", () => {
    const p = presentDose(
      dose({
        series: "ROTA",
        status: "AGED_OUT",
        actionableToday: false,
        latestUsefulDate: "2026-06-01",
        rationale:
          "Rotavirus is not given after 32 weeks of age; the excess risk of intussusception rises with age at first dose.",
      }),
    );
    expect(p.actionable).toBe(false);
    expect(p.tone).toBe("closed");
    expect(p.detail).toContain("intussusception");
  });

  it("falls back to naming the closed window as a safety limit when no rationale is supplied", () => {
    const p = presentDose(dose({ status: "AGED_OUT", actionableToday: false, latestUsefulDate: "2026-06-01" }));
    expect(p.detail).toContain("safety limit");
    expect(p.detail).toContain("1 Jun 2026");
  });

  it("takes actionable from the engine, never from the tone", () => {
    // If the engine ever said a blocked dose was actionable, the display would follow it rather
    // than silently overriding a clinical verdict with a presentation choice.
    const p = presentDose(dose({ status: "BLOCKED_BY_INTERVAL", actionableToday: true }));
    expect(p.actionable).toBe(true);
  });

  it("never lets a blocked or closed dose into the give-today list", () => {
    const f = forecast({
      doses: [
        dose({ series: "PENTA", status: "DUE", actionableToday: true }),
        dose({ series: "OPV", status: "BLOCKED_BY_INTERVAL", actionableToday: false, earliestDate: "2026-08-20" }),
        dose({ series: "ROTA", status: "AGED_OUT", actionableToday: false }),
      ],
    });
    expect(actionableDoses(f).map((d) => d.series)).toEqual(["PENTA"]);
    expect(withheldDoses(f).map((d) => d.series)).toEqual(["OPV", "ROTA"]);
  });

  it("does not render an unrecognised status as giveable", () => {
    const p = presentDose(dose({ status: "SOME_NEW_STATUS", actionableToday: false }));
    expect(p.actionable).toBe(false);
    expect(p.tone).toBe("blocked");
  });

  it("puts overdue first and closed windows below the live actions", () => {
    const order = sortDoses([
      dose({ series: "ROTA", status: "AGED_OUT", actionableToday: false }),
      dose({ series: "MR", status: "NOT_YET_DUE", actionableToday: false }),
      dose({ series: "PENTA", status: "OVERDUE", actionableToday: true }),
      dose({ series: "OPV", status: "BLOCKED_BY_INTERVAL", actionableToday: false }),
    ]).map((d) => d.series);
    expect(order).toEqual(["PENTA", "OPV", "ROTA", "MR"]);
    expect(doseRank(dose({ status: "OVERDUE" }))).toBeLessThan(doseRank(dose({ status: "DUE" })));
  });
});

describe("the due-today line", () => {
  it("says nothing can be given today, and does not imply the child is up to date", () => {
    const s = forecastSummary(
      forecast({
        doses: [dose({ status: "BLOCKED_BY_INTERVAL", actionableToday: false, earliestDate: "2026-08-20" })],
      }),
    );
    expect(s?.urgency).toBe("watch");
    expect(s?.detail).toContain("No dose can be given today");
  });

  it("names a permanently closed window rather than filing it as merely not-due", () => {
    const s = forecastSummary(
      forecast({ doses: [dose({ series: "ROTA", status: "AGED_OUT", actionableToday: false })] }),
    );
    expect(s?.detail).toContain("window has closed");
  });

  it("returns null only when the schedule genuinely has nothing outstanding", () => {
    expect(forecastSummary(forecast({ doses: [dose({ status: "GIVEN", actionableToday: false })] }))).toBeNull();
  });
});

describe("what is due today, composed", () => {
  const age = ageFactsFor("2026-01-01");

  it("says the schedule could not be run rather than staying silent", () => {
    const items = computeDueToday({
      patientId: "p1",
      age,
      hasAnyGrowthMeasurement: true,
      lastMeasuredAt: new Date().toISOString(),
      immunisationCount: 3,
      immunisation: { summary: null, evaluated: false, unavailable: true },
      now: new Date("2026-08-02T00:00:00Z"),
    });
    const immunisation = items.find((i) => i.id === "immunisation-unknown");
    expect(immunisation?.urgency).toBe("unknown");
    expect(immunisation?.detail).toContain("Do not read this as up to date");
  });

  it("carries the actionable reason when the forecast could not run for a fixable cause", () => {
    const items = computeDueToday({
      patientId: "p1",
      age,
      hasAnyGrowthMeasurement: true,
      lastMeasuredAt: new Date().toISOString(),
      immunisationCount: 0,
      immunisation: {
        summary: null,
        evaluated: false,
        unavailable: true,
        unavailableReason: "This child's date of birth is not recorded, so no dose can be forecast.",
      },
      now: new Date("2026-08-02T00:00:00Z"),
    });
    expect(items.find((i) => i.id === "immunisation-unknown")?.detail).toContain("date of birth is not recorded");
  });

  it("raises nothing for immunisation when the schedule ran and found nothing outstanding", () => {
    const items = computeDueToday({
      patientId: "p1",
      age,
      hasAnyGrowthMeasurement: true,
      lastMeasuredAt: new Date().toISOString(),
      immunisationCount: 5,
      immunisation: { summary: null, evaluated: true, unavailable: false },
      now: new Date("2026-08-02T00:00:00Z"),
    });
    // An empty panel now means "nothing outstanding" — a claim the schedule actually made.
    expect(items.filter((i) => i.label === "Immunisation")).toHaveLength(0);
  });

  it("flags a child with a clean schedule but no dose ever recorded as a records gap", () => {
    const items = computeDueToday({
      patientId: "p1",
      age,
      hasAnyGrowthMeasurement: true,
      lastMeasuredAt: new Date().toISOString(),
      immunisationCount: 0,
      immunisation: { summary: null, evaluated: true, unavailable: false },
      now: new Date("2026-08-02T00:00:00Z"),
    });
    expect(items.find((i) => i.id === "immunisation-none-recorded")?.detail).toContain("child health card");
  });

  it("passes the forecast's own urgency and wording through unchanged", () => {
    const items = computeDueToday({
      patientId: "p1",
      age,
      hasAnyGrowthMeasurement: true,
      lastMeasuredAt: new Date().toISOString(),
      immunisationCount: 2,
      immunisation: {
        summary: { urgency: "overdue", detail: "PENTA dose 2. 1 overdue — give at this contact." },
        evaluated: true,
        unavailable: false,
      },
      now: new Date("2026-08-02T00:00:00Z"),
    });
    const item = items.find((i) => i.label === "Immunisation");
    expect(item?.urgency).toBe("overdue");
    expect(item?.detail).toContain("give at this contact");
  });
});

describe("rendered", () => {
  it("separates giveable doses from withheld ones and marks each row's actionability", () => {
    render(
      <ImmunisationForecastView
        forecast={forecast({
          doses: [
            dose({ series: "PENTA", doseNumber: 2, status: "OVERDUE", actionableToday: true, daysOverdue: 12 }),
            dose({
              series: "OPV",
              doseNumber: 2,
              status: "BLOCKED_BY_INTERVAL",
              actionableToday: false,
              earliestDate: "2026-08-20",
            }),
          ],
        })}
      />,
    );

    expect(screen.getByTestId("forecast-headline").getAttribute("data-actionable-count")).toBe("1");
    expect(screen.getByTestId("dose-PENTA-2").getAttribute("data-actionable")).toBe("true");
    const blocked = screen.getByTestId("dose-OPV-2");
    expect(blocked.getAttribute("data-actionable")).toBe("false");
    expect(blocked.textContent).toContain("20 Aug 2026");
    expect(screen.getByTestId("forecast-giveable").textContent).not.toContain("OPV");
  });

  it("says an empty give-list is today's answer, not a complete immunisation record", () => {
    render(
      <ImmunisationForecastView
        forecast={forecast({
          doses: [dose({ status: "BLOCKED_BY_INTERVAL", actionableToday: false, earliestDate: "2026-08-20" })],
        })}
      />,
    );
    expect(screen.getByTestId("forecast-headline").textContent).toContain("No dose can be given");
    expect(screen.getByTestId("forecast-headline").textContent).toContain("not a statement that the child");
  });

  it("marks a provisional forecast as provisional", () => {
    render(
      <ImmunisationForecastView
        forecast={forecast({
          provisional: true,
          provisionalReasons: ["sex is unknown, so sex-specific antigens were not scheduled"],
          doses: [dose()],
        })}
      />,
    );
    expect(screen.getByTestId("forecast-provisional").textContent).toContain("sex is unknown");
  });
});
