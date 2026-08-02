import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import type { ImnciAssessment, ImnciClassification } from "@/hooks/queries/usePaediatricDecisionSupport";
import {
  assessmentHeadline,
  observationList,
  outcomeRank,
  outcomeTone,
  presentOutcome,
  sortOutcomes,
} from "../imnci-presentation";
import { ImnciAssessmentView } from "../ImnciClassificationPanel";

/**
 * The property under test throughout: an unanswered question must never read as a reassuring
 * answer. The engine goes to considerable lengths to distinguish the two, and every one of those
 * distinctions can be destroyed by the last component in the chain.
 */

function outcome(over: Partial<ImnciClassification> = {}): ImnciClassification {
  return {
    tableId: "IMNCI_PNEUMONIA",
    axis: "Pneumonia severity",
    status: "OK",
    classification: "NO_PNEUMONIA",
    classificationName: "No pneumonia: cough or cold",
    colour: "GREEN",
    provisional: false,
    actionable: true,
    unresolvedTiers: [],
    missingInputs: [],
    waivedCriteria: [],
    action: "Home care and advice.",
    treatments: [],
    referralRequired: false,
    urgentReferral: false,
    rationale: null,
    ...over,
  };
}

function assessment(over: Partial<ImnciAssessment> = {}): ImnciAssessment {
  return {
    ageDays: 420,
    pathway: "CHILD",
    applicable: true,
    classifications: [outcome()],
    urgentReferralRequired: false,
    referralRequired: false,
    incomplete: false,
    outstandingAssessments: [],
    unavailableCriteria: [],
    treatmentPlan: [],
    disposition: "Manage at this facility",
    contentVersion: "engineering-seed-1.0.0",
    contentSource: "IMNCI classification tables",
    approvalStatus: "ENGINEERING_SEED",
    note: null,
    ...over,
  };
}

describe("an unresolved classification is never the green row", () => {
  it("tones INDETERMINATE as unresolved, not as well", () => {
    const tone = outcomeTone(outcome({ status: "INDETERMINATE", classification: null, colour: null }));
    expect(tone).toBe("unresolved");
    expect(tone).not.toBe("well");
  });

  it("stays unresolved even when a colour is present on an INDETERMINATE row", () => {
    // The status is the authority on whether a conclusion was reached. A row carrying a stale or
    // defaulted GREEN alongside INDETERMINATE must not be painted green.
    const tone = outcomeTone(outcome({ status: "INDETERMINATE", colour: "GREEN", classification: "NO_PNEUMONIA" }));
    expect(tone).toBe("unresolved");
  });

  it("treats a resolved row with no colour as unresolved rather than guessing a band", () => {
    expect(outcomeTone(outcome({ colour: null }))).toBe("unresolved");
  });

  it("names the outstanding observation in the caveat", () => {
    const p = presentOutcome(
      outcome({ status: "INDETERMINATE", classification: null, colour: null, missingInputs: ["chestIndrawing"] }),
    );
    expect(p.tone).toBe("unresolved");
    expect(p.colourLabel).toBeNull();
    expect(p.caveat).toContain("chest indrawing");
    expect(p.caveat).toContain("not a finding that the child is well");
  });

  it("still says it is not a well finding when the engine named nothing", () => {
    const p = presentOutcome(outcome({ status: "INDETERMINATE", classification: null, colour: null }));
    expect(p.caveat).toContain("not a finding that the child is well");
  });

  it("labels an observation key the table has not caught up with rather than dropping it", () => {
    // "cannot classify: complete " with nothing named is the one thing an unresolved row must
    // never be, so an unknown key is de-camel-cased rather than hidden.
    expect(observationList(["someBrandNewSign"])).toBe("some brand new sign");
  });

  it("sorts unresolved above treat-here, because it carries an outstanding action", () => {
    const unresolved = outcome({ tableId: "A", status: "INDETERMINATE", classification: null, colour: null });
    const treat = outcome({ tableId: "B", colour: "YELLOW", classification: "PNEUMONIA" });
    const critical = outcome({ tableId: "C", colour: "PINK", classification: "SEVERE_PNEUMONIA" });
    const well = outcome({ tableId: "D", colour: "GREEN" });

    expect(outcomeRank(unresolved)).toBeLessThan(outcomeRank(treat));
    expect(sortOutcomes([well, treat, unresolved, critical]).map((o) => o.tableId)).toEqual([
      "C",
      "A",
      "B",
      "D",
    ]);
  });
});

describe("a provisional classification is not a conclusion", () => {
  it("chips as provisional rather than as its colour", () => {
    const p = presentOutcome(outcome({ colour: "YELLOW", classification: "SOME_DEHYDRATION", provisional: true }));
    expect(p.chipLabel).toBe("Provisional");
    expect(p.caveat).toContain("could not be excluded");
    expect(p.caveat).toContain("the least this child has");
  });

  it("carries no caveat when the row resolved outright", () => {
    expect(presentOutcome(outcome()).caveat).toBeNull();
  });
});

describe("an incomplete assessment does not summarise as a disposition", () => {
  it("reports the outstanding work instead of the computed disposition", () => {
    const h = assessmentHeadline(
      assessment({ incomplete: true, outstandingAssessments: ["chestIndrawing", "muacCm"] }),
    );
    expect(h.tone).toBe("unresolved");
    expect(h.text).toContain("incomplete");
    expect(h.detail).toContain("chest indrawing");
    expect(h.detail).toContain("mid-upper-arm circumference");
    // The disposition was computed over a partial picture and must not be the headline.
    expect(h.text).not.toContain("Manage at this facility");
  });

  it("still leads with urgent referral when one is required, incomplete or not", () => {
    // An incomplete assessment that has already found something life-threatening must not bury it
    // under a completeness message.
    const h = assessmentHeadline(assessment({ incomplete: true, urgentReferralRequired: true }));
    expect(h.tone).toBe("critical");
    expect(h.text).toBe("Urgent referral required");
  });

  it("summarises as the disposition only when nothing is outstanding", () => {
    const h = assessmentHeadline(assessment());
    expect(h.tone).toBe("well");
    expect(h.text).toBe("Manage at this facility");
  });
});

describe("rendered", () => {
  it("shows the unresolved row uncoloured, with its outstanding observation named", () => {
    render(
      <ImnciAssessmentView
        assessment={assessment({
          incomplete: true,
          outstandingAssessments: ["chestIndrawing"],
          classifications: [
            outcome({
              tableId: "IMNCI_PNEUMONIA",
              status: "INDETERMINATE",
              classification: null,
              colour: null,
              missingInputs: ["chestIndrawing"],
              action: "Home care and advice.",
            }),
          ],
        })}
      />,
    );

    const row = screen.getByTestId("imnci-row-IMNCI_PNEUMONIA");
    expect(row.getAttribute("data-tone")).toBe("unresolved");
    expect(screen.getByTestId("imnci-caveat-IMNCI_PNEUMONIA").textContent).toContain("chest indrawing");
    // No colour band is claimed for a row that did not resolve.
    expect(screen.queryByTestId("imnci-colour-IMNCI_PNEUMONIA")).toBeNull();
    // And the resolved row's action is not shown against it, which would read as a plan.
    expect(screen.queryByTestId("imnci-action-IMNCI_PNEUMONIA")).toBeNull();
    expect(screen.getByTestId("imnci-headline").getAttribute("data-tone")).toBe("unresolved");
  });

  it("shows which chart was run, because the young-infant chart is a different assessment", () => {
    render(<ImnciAssessmentView assessment={assessment({ pathway: "YOUNG_INFANT", ageDays: 10 })} />);
    expect(screen.getByTestId("imnci-pathway").textContent).toContain("Sick young infant chart");
  });

  it("shows a pink row with its colour band and its urgent referral instruction", () => {
    render(
      <ImnciAssessmentView
        assessment={assessment({
          urgentReferralRequired: true,
          classifications: [
            outcome({
              tableId: "IMNCI_PNEUMONIA",
              classification: "SEVERE_PNEUMONIA",
              classificationName: "Severe pneumonia or very severe disease",
              colour: "PINK",
              urgentReferral: true,
              referralRequired: true,
            }),
          ],
        })}
      />,
    );
    expect(screen.getByTestId("imnci-row-IMNCI_PNEUMONIA").getAttribute("data-tone")).toBe("critical");
    expect(screen.getByTestId("imnci-colour-IMNCI_PNEUMONIA").textContent).toContain("urgent referral");
  });

  it("discloses a waived criterion rather than presenting the row as fully reached", () => {
    render(
      <ImnciAssessmentView
        assessment={assessment({
          classifications: [
            outcome({
              tableId: "IMNCI_ACUTE_MALNUTRITION",
              classification: "NO_ACUTE_MALNUTRITION",
              colour: "GREEN",
              waivedCriteria: ["weightForHeightZ"],
            }),
          ],
        })}
      />,
    );
    expect(screen.getByTestId("imnci-waived-IMNCI_ACUTE_MALNUTRITION").textContent).toContain(
      "weight-for-height",
    );
  });

  it("warns that a treatment plan built on an incomplete assessment may not be complete", () => {
    render(
      <ImnciAssessmentView
        assessment={assessment({ incomplete: true, treatmentPlan: ["Give first dose of oral amoxicillin"] })}
      />,
    );
    expect(screen.getByTestId("imnci-treatment-plan").textContent).toContain("incomplete assessment");
  });
});
