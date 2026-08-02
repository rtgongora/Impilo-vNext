import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import type { GrowthInterpretation, GrowthSignal } from "@/hooks/queries/usePaediatricDecisionSupport";
import type { GrowthMeasurement } from "@/hooks/queries/useGrowth";
import { GrowthSignalsView, toEnginePoints } from "../GrowthSignalsPanel";

function signal(over: Partial<GrowthSignal> = {}): GrowthSignal {
  return {
    code: "WEIGHT_FALTERING_SEVERE",
    name: "Severe weight faltering",
    severity: "SEVERE",
    measure: "weight-for-age",
    zChange: -1.7,
    fromAgeDays: 120,
    toAgeDays: 180,
    action: "Assess for acute malnutrition and refer for nutritional assessment.",
    referralRequired: true,
    rationale: "A fall of more than 1.5 z between contacts is severe faltering.",
    ...over,
  };
}

function interpretation(over: Partial<GrowthInterpretation> = {}): GrowthInterpretation {
  return {
    assessable: true,
    notAssessableReason: null,
    interpretationBlocked: false,
    zTrendDerivable: true,
    anyConcern: false,
    referralRequired: false,
    contactsConsidered: 3,
    signals: [],
    contentVersion: "engineering-seed-1.0.0",
    contentSource: "Growth intelligence content",
    approvalStatus: "ENGINEERING_SEED",
    note: null,
    ...over,
  };
}

describe("interpretation_blocked suppresses the clinical signals on screen", () => {
  it("shows the blocked state as its own finding rather than an empty signal list", () => {
    render(
      <GrowthSignalsView
        interpretation={interpretation({
          interpretationBlocked: true,
          signals: [],
          notAssessableReason:
            "The weight fell 4.2 kg between contacts, which is implausible and is more likely a recording error.",
        })}
      />,
    );

    const blocked = screen.getByTestId("growth-signals-blocked");
    expect(blocked.textContent).toContain("implausible");
    expect(blocked.textContent).toContain("deliberately not shown");
    // The reassuring branch must not have been reached. An empty signal list under a blocked
    // interpretation means nobody interpreted this child's growth, not that it is fine.
    expect(screen.queryByTestId("growth-signals-none")).toBeNull();
    expect(screen.queryByTestId("growth-signal-list")).toBeNull();
  });

  it("suppresses signals even if the engine were to send some alongside the block", () => {
    // Defence in depth. The engine empties the list itself, but if that ever changed, the screen
    // must still not put a clinical finding next to a measurement that may be a typo.
    render(
      <GrowthSignalsView interpretation={interpretation({ interpretationBlocked: true, signals: [signal()] })} />,
    );
    expect(screen.getByTestId("growth-signals-blocked")).toBeTruthy();
    expect(screen.queryByTestId("growth-signal-list")).toBeNull();
    expect(screen.queryByTestId("growth-signal-WEIGHT_FALTERING_SEVERE")).toBeNull();
  });

  it("does not render the blocked state as a well child", () => {
    render(<GrowthSignalsView interpretation={interpretation({ interpretationBlocked: true })} />);
    expect(screen.getByTestId("growth-signals-blocked").getAttribute("data-tone")).toBe("blocked");
  });
});

describe("an empty signal list only reassures when something was actually compared", () => {
  it("says a comparison found nothing when the trend was derivable", () => {
    render(<GrowthSignalsView interpretation={interpretation({ zTrendDerivable: true, contactsConsidered: 3 })} />);
    expect(screen.getByTestId("growth-signals-none").textContent).toContain("across 3 contacts");
  });

  it("refuses to call it a comparison when the trend could not be derived", () => {
    render(<GrowthSignalsView interpretation={interpretation({ zTrendDerivable: false })} />);
    expect(screen.getByTestId("growth-signals-none").textContent).toContain(
      "not a comparison that found nothing",
    );
  });

  it("says a single contact is not evidence of growing well", () => {
    render(
      <GrowthSignalsView
        interpretation={interpretation({
          assessable: false,
          notAssessableReason: "Two measurements are the minimum; one contact is recorded.",
        })}
      />,
    );
    expect(screen.getByTestId("growth-signals-not-assessable").textContent).toContain("minimum");
    expect(screen.queryByTestId("growth-signals-none")).toBeNull();
  });
});

describe("signals carry their action", () => {
  it("renders each signal with the action the engine named", () => {
    render(<GrowthSignalsView interpretation={interpretation({ signals: [signal()], referralRequired: true })} />);
    expect(screen.getByTestId("growth-action-WEIGHT_FALTERING_SEVERE").textContent).toContain(
      "refer for nutritional assessment",
    );
    expect(screen.getByTestId("growth-signals-headline").textContent).toContain("referral required");
    expect(screen.getByTestId("growth-signal-WEIGHT_FALTERING_SEVERE").getAttribute("data-severity")).toBe(
      "SEVERE",
    );
  });

  it("shows the z change with its interval", () => {
    render(<GrowthSignalsView interpretation={interpretation({ signals: [signal()] })} />);
    expect(screen.getByTestId("growth-signal-WEIGHT_FALTERING_SEVERE").textContent).toContain("-1.70 z");
    expect(screen.getByTestId("growth-signal-WEIGHT_FALTERING_SEVERE").textContent).toContain("day 120");
  });

  it("carries the provenance of the content that produced the reading", () => {
    render(<GrowthSignalsView interpretation={interpretation({ signals: [signal()] })} />);
    expect(screen.getByTestId("growth-signals-provenance").textContent).toContain("engineering seed");
  });
});

describe("what is sent to the engine", () => {
  function measurement(over: Record<string, unknown> = {}): GrowthMeasurement {
    return {
      id: "m1",
      measuredAt: "2026-03-01T00:00:00Z",
      recordedBy: null,
      weightKg: 7.2,
      lengthCm: null,
      heightCm: null,
      headCircumferenceCm: null,
      muacCm: null,
      bmi: null,
      measurementMode: "SUPINE",
      notes: null,
      derived: {
        ageDays: 180,
        correctedAgeDays: null,
        correctedAgeApplied: false,
        gestationalAgeWeeks: null,
        gestationalAgeSource: null,
        postmenstrualAgeWeeks: null,
        standard: "WHO_2006",
        standardLabel: null,
        standardAttribution: null,
        engineVersion: null,
        scoringNote: null,
        scoringGaps: null,
        normalizedStatureCm: null,
        normalizedStatureMode: "SUPINE",
        bodyMassIndex: null,
        weightForAge: { z_score: -1.2, percentile: 11 } as never,
        lengthHeightForAge: null,
        bodyMassIndexForAge: null,
        headCircumferenceForAge: null,
      },
      ...over,
    } as unknown as GrowthMeasurement;
  }

  it("sends the stamped z-scores and the standard, oldest contact first", () => {
    const points = toEnginePoints([
      measurement({ id: "b", measuredAt: "2026-06-01T00:00:00Z" }),
      measurement({ id: "a", measuredAt: "2026-03-01T00:00:00Z" }),
    ]);
    expect(points).toHaveLength(2);
    // Oldest first: a series compared in the wrong order would read faltering as catch-up growth.
    expect(points[0].age_days).toBe(180);
    // The standard travels with each point, so a Fenton-to-WHO handover is visible to the engine
    // rather than looking like a discontinuity in the child.
    expect(points[0].standard).toBe("WHO_2006");
    expect(points[0].measurement_mode).toBe("SUPINE");
  });
});
