import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  ageFactsFor,
  bandFor,
  describeAge,
  recommendJourney,
  SICK_YOUNG_INFANT_MAX_AGE_DAYS,
  UNDER_FIVE_MAX_AGE_DAYS,
} from "../paediatric-age";
import { computeDueToday, sortDueItems } from "../due-today";
import { ChildContextBar } from "../ChildContextBar";
import { DueTodayPanel } from "../DueTodayPanel";
import { JourneyRouterCard } from "../JourneyRouterCard";
import type { PaediatricContext } from "../usePaediatricContext";

const NOW = new Date("2026-07-26T09:00:00Z");
const dobDaysAgo = (days: number) => new Date(NOW.getTime() - days * 86_400_000).toISOString();

describe("age bands", () => {
  // These boundaries mirror libs/paediatric-domain PaediatricAgeBand. If the Java side
  // moves and this does not, this test is what surfaces the divergence.
  it.each([
    [0, "NEWBORN_EARLY"],
    [7, "NEWBORN_EARLY"],
    [8, "NEWBORN_LATE"],
    [28, "NEWBORN_LATE"],
    [29, "INFANT"],
    [364, "INFANT"],
    [365, "YOUNG_CHILD"],
    [1824, "YOUNG_CHILD"],
    [1825, "SCHOOL_AGE"],
    [3650, "ADOLESCENT_YOUNG"],
    [5475, "ADOLESCENT_OLDER"],
    [6575, "ADULT_TRANSITION"],
  ])("day %i falls in %s", (ageDays, band) => {
    expect(bandFor(ageDays)).toBe(band);
  });

  it("keeps the sick-young-infant and under-five windows where the Java router puts them", () => {
    expect(SICK_YOUNG_INFANT_MAX_AGE_DAYS).toBe(59);
    expect(UNDER_FIVE_MAX_AGE_DAYS).toBe(1824);
  });

  it("returns no band when age is unknown", () => {
    expect(bandFor(null)).toBeNull();
  });
});

describe("age display", () => {
  it("uses the unit a clinician thinks in at that age", () => {
    // "0 years" for a ten-day-old is how an adult dose reaches a neonate.
    expect(describeAge(0)).toBe("Born today");
    expect(describeAge(1)).toBe("1 day");
    expect(describeAge(10)).toBe("10 days");
    expect(describeAge(21)).toBe("3 weeks");
    expect(describeAge(400)).toBe("13 months");
    expect(describeAge(2600)).toBe("7 years");
    expect(describeAge(null)).toBe("Age unknown");
  });
});

describe("journey recommendation", () => {
  it("routes an unwell infant under 60 days to the young-infant pathway", () => {
    const facts = ageFactsFor(dobDaysAgo(10), NOW);
    const rec = recommendJourney(facts, "ACUTE");
    expect(rec.journey).toBe("SICK_YOUNG_INFANT");
    expect(rec.rationale).toContain("first 59 days");
  });

  it("routes an unwell child just over that window to IMNCI instead", () => {
    const facts = ageFactsFor(dobDaysAgo(60), NOW);
    expect(recommendJourney(facts, "ACUTE").journey).toBe("IMNCI_UNDER_FIVE");
  });

  it("distinguishes a routine contact from an unwell one at the same age", () => {
    const facts = ageFactsFor(dobDaysAgo(400), NOW);
    expect(recommendJourney(facts, "ROUTINE").journey).toBe("WELL_CHILD");
    expect(recommendJourney(facts, "ACUTE").journey).toBe("IMNCI_UNDER_FIVE");
  });

  it("moves school-age children from classification to full clerking", () => {
    const facts = ageFactsFor(dobDaysAgo(2600), NOW);
    expect(recommendJourney(facts, "ACUTE").journey).toBe("GENERAL_PAEDIATRIC");
  });

  it("gives adolescents their own confidential pathway", () => {
    expect(recommendJourney(ageFactsFor(dobDaysAgo(4000), NOW)).journey).toBe("ADOLESCENT");
  });

  it("recommends nothing when the date of birth is missing", () => {
    const rec = recommendJourney(ageFactsFor(null, NOW));
    expect(rec.journey).toBe("UNDETERMINED");
    expect(rec.rationale).toContain("Date of birth");
  });

  it("always explains itself so it can be disagreed with", () => {
    const rec = recommendJourney(ageFactsFor(dobDaysAgo(10), NOW), "ACUTE");
    expect(rec.rationale.length).toBeGreaterThan(20);
  });
});

describe("what is due today", () => {
  const age = ageFactsFor(dobDaysAgo(400), NOW);

  it("treats a child who has never been weighed as overdue, naming the consequence", () => {
    const items = computeDueToday({
      patientId: "p1",
      age,
      hasAnyGrowthMeasurement: false,
      immunisationCount: 3,
      now: NOW,
    });
    const growth = items.find((i) => i.id === "growth-none");
    expect(growth?.urgency).toBe("overdue");
    expect(growth?.detail).toContain("dose cannot be calculated");
  });

  it("ages a weight out faster in infancy than in childhood", () => {
    // A weight 40 days old is stale for an infant and still current for a four-year-old.
    const infant = computeDueToday({
      patientId: "p1",
      age: ageFactsFor(dobDaysAgo(200), NOW),
      hasAnyGrowthMeasurement: true,
      lastMeasuredAt: new Date(NOW.getTime() - 40 * 86_400_000).toISOString(),
      immunisationCount: 1,
      now: NOW,
    });
    const child = computeDueToday({
      patientId: "p1",
      age: ageFactsFor(dobDaysAgo(1500), NOW),
      hasAnyGrowthMeasurement: true,
      lastMeasuredAt: new Date(NOW.getTime() - 40 * 86_400_000).toISOString(),
      immunisationCount: 1,
      now: NOW,
    });

    expect(infant.some((i) => i.id === "growth-stale")).toBe(true);
    expect(child.some((i) => i.id === "growth-stale")).toBe(false);
  });

  it("raises a nutrition review on a low weight-for-age already recorded", () => {
    const items = computeDueToday({
      patientId: "p1",
      age,
      hasAnyGrowthMeasurement: true,
      lastMeasuredAt: NOW.toISOString(),
      latestWeightForAgeZ: -2.4,
      immunisationCount: 2,
      now: NOW,
    });
    expect(items.find((i) => i.id === "nutrition-followup")?.urgency).toBe("overdue");
  });

  it("says what it cannot determine about immunisation rather than implying nothing is due", () => {
    // The schedule forecast now exists and drives this item (see the immunisation feature tests).
    // The property this test has always protected is unchanged and is the one that matters: when
    // the forecast did not run, silence would read as an all-clear, so the caller supplying no
    // forecast at all must still produce an explicit "not established" rather than nothing.
    const items = computeDueToday({
      patientId: "p1",
      age,
      hasAnyGrowthMeasurement: true,
      lastMeasuredAt: NOW.toISOString(),
      immunisationCount: 4,
      now: NOW,
    });
    const immunisation = items.find((i) => i.id === "immunisation-unknown");
    expect(immunisation?.urgency).toBe("unknown");
    expect(immunisation?.detail).toContain("has not been established");
    expect(immunisation?.detail).toContain("Do not read this as up to date");
  });

  it("prompts the age-appropriate danger signs", () => {
    const infant = computeDueToday({
      patientId: "p1",
      age: ageFactsFor(dobDaysAgo(20), NOW),
      hasAnyGrowthMeasurement: true,
      lastMeasuredAt: NOW.toISOString(),
      immunisationCount: 1,
      now: NOW,
    });
    expect(infant.some((i) => i.id === "young-infant-danger-signs")).toBe(true);
    expect(infant.some((i) => i.id === "under-five-danger-signs")).toBe(false);
  });

  it("sorts the most urgent first", () => {
    const sorted = sortDueItems([
      { id: "a", label: "a", detail: "", urgency: "unknown" },
      { id: "b", label: "b", detail: "", urgency: "overdue" },
      { id: "c", label: "c", detail: "", urgency: "due" },
    ]);
    expect(sorted.map((i) => i.id)).toEqual(["b", "c", "a"]);
  });
});

function contextFixture(overrides: Partial<PaediatricContext> = {}): PaediatricContext {
  return {
    patientId: "p1",
    patientName: "Test Child",
    age: ageFactsFor(dobDaysAgo(400), NOW),
    dosingWeightKg: 8.4,
    dosingWeightMeasuredAt: NOW.toISOString(),
    latestWeightForAgeZ: -1.2,
    allergyLabels: [],
    dueItems: [],
    journey: recommendJourney(ageFactsFor(dobDaysAgo(400), NOW)),
    unavailable: [],
    isLoading: false,
    ...overrides,
  };
}

describe("ChildContextBar", () => {
  it("shows age, dosing weight and allergies together", () => {
    render(<ChildContextBar context={contextFixture({ allergyLabels: ["Penicillin"] })} />);
    expect(screen.getByTestId("child-age").textContent).toBe("13 months");
    expect(screen.getByTestId("child-weight").textContent).toContain("8.4 kg");
    expect(screen.getByTestId("child-allergies").textContent).toContain("Penicillin");
  });

  it("says no dose can be calculated when there is no weight", () => {
    render(<ChildContextBar context={contextFixture({ dosingWeightKg: null, dosingWeightMeasuredAt: null })} />);
    expect(screen.getByTestId("child-weight").textContent).toContain("no dose can be calculated");
  });

  it("distinguishes no allergies recorded from no allergies", () => {
    render(<ChildContextBar context={contextFixture()} />);
    expect(screen.getByTestId("child-allergies").textContent).toBe("None recorded");
  });

  it("flags the young-infant window", () => {
    render(<ChildContextBar context={contextFixture({ age: ageFactsFor(dobDaysAgo(20), NOW) })} />);
    expect(screen.getByTestId("child-young-infant-flag")).toBeTruthy();
  });

  it("says which blocks failed to load rather than showing a confident partial view", () => {
    render(<ChildContextBar context={contextFixture({ unavailable: ["immunisations"] })} />);
    expect(screen.getByTestId("child-context-degraded").textContent).toContain("immunisations");
  });
});

describe("DueTodayPanel", () => {
  it("carries urgency in words as well as colour", () => {
    render(
      <DueTodayPanel
        items={[{ id: "x", label: "Weight and growth", detail: "Never measured", urgency: "overdue" }]}
      />,
    );
    expect(screen.getByTestId("due-item-x").textContent).toContain("Overdue");
  });

  it("states plainly when nothing is outstanding", () => {
    render(<DueTodayPanel items={[]} />);
    expect(screen.getByTestId("due-today-empty").textContent).toContain("Nothing outstanding");
  });
});

describe("JourneyRouterCard", () => {
  it("shows the recommendation with its reasoning", () => {
    const rec = recommendJourney(ageFactsFor(dobDaysAgo(10), NOW), "ACUTE");
    render(
      <JourneyRouterCard recommendation={rec} presentation="ACUTE" onPresentationChange={() => {}} />,
    );
    expect(screen.getByTestId("journey-recommendation").textContent).toContain("Sick young infant");
    expect(screen.getByTestId("journey-rationale").textContent).toContain("59 days");
  });

  it("keeps every other journey reachable — it recommends, never forces", () => {
    const rec = recommendJourney(ageFactsFor(dobDaysAgo(400), NOW));
    const onJourneySelect = vi.fn();
    render(
      <JourneyRouterCard
        recommendation={rec}
        presentation="ROUTINE"
        onPresentationChange={() => {}}
        onJourneySelect={onJourneySelect}
      />,
    );
    expect(screen.getByTestId("journey-option-ADOLESCENT")).toBeTruthy();
    expect(screen.getByTestId("journey-option-SICK_YOUNG_INFANT")).toBeTruthy();
  });

  it("offers no start button when no journey could be recommended", () => {
    const rec = recommendJourney(ageFactsFor(null, NOW));
    render(
      <JourneyRouterCard
        recommendation={rec}
        presentation="ROUTINE"
        onPresentationChange={() => {}}
        onJourneySelect={() => {}}
      />,
    );
    expect(screen.queryByTestId("journey-start-recommended")).toBeNull();
  });
});
