import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  branchState,
  dangerSignStatus,
  GENERAL_DANGER_SIGN_FIELDS,
  IMNCI_STEPS,
  isAnswered,
  isPositive,
} from "../imnci-journey";
import { DangerSignBanner } from "../DangerSignBanner";

describe("IMNCI step sequence", () => {
  it("looks for emergency signs before anything else", () => {
    // The order is the guideline: treat what is immediately life-threatening first.
    expect(IMNCI_STEPS[0].sectionId).toBe("emergency");
    expect(IMNCI_STEPS[1].sectionId).toBe("danger");
  });

  it("marks the steps that apply to every child regardless of complaint", () => {
    const universal = IMNCI_STEPS.filter((s) => s.universal).map((s) => s.sectionId);
    expect(universal).toContain("danger");
    // Nutrition and immunisation are assessed in every child — that is the whole point of
    // an integrated assessment rather than a symptom-led one.
    expect(universal).toContain("nutrition");
    expect(universal).toContain("immunisation");
    expect(universal).not.toContain("cough");
  });

  it("classifies last, and says why classification is not diagnosis", () => {
    const last = IMNCI_STEPS[IMNCI_STEPS.length - 1];
    expect(last.sectionId).toBe("classify");
    expect(last.intent).toContain("not a diagnosis");
  });

  it("gives every step a stated intent", () => {
    IMNCI_STEPS.forEach((step) => {
      expect(step.intent.length, `${step.sectionId} needs an intent`).toBeGreaterThan(15);
    });
  });
});

describe("answer semantics", () => {
  it("treats unknown as an answer but not-assessed as an absence", () => {
    // "Unknown" means the question was asked and could not be answered; "not assessed"
    // means it was never asked. Only the second is a gap the clinician can close.
    expect(isAnswered("yes")).toBe(true);
    expect(isAnswered("no")).toBe(true);
    expect(isAnswered("unknown")).toBe(true);
    expect(isAnswered("not_assessed")).toBe(false);
    expect(isAnswered(undefined)).toBe(false);
  });

  it("only a yes asserts the sign is present", () => {
    expect(isPositive("yes")).toBe(true);
    expect(isPositive("unknown")).toBe(false);
    expect(isPositive("not_assessed")).toBe(false);
  });
});

describe("danger sign status", () => {
  const allNegative = Object.fromEntries(GENERAL_DANGER_SIGN_FIELDS.map((f) => [f, "no"]));

  it("is complete only when every sign carries an answer", () => {
    expect(dangerSignStatus(allNegative).complete).toBe(true);
    expect(dangerSignStatus({}).complete).toBe(false);
  });

  it("counts unknown as assessed", () => {
    const status = dangerSignStatus({ ...allNegative, convulsions: "unknown" });
    expect(status.complete).toBe(true);
    expect(status.present).toEqual([]);
  });

  it("counts an explicit not-assessed as still outstanding", () => {
    const status = dangerSignStatus({ ...allNegative, convulsions: "not_assessed" });
    expect(status.complete).toBe(false);
    expect(status.unassessed).toEqual(["convulsions"]);
  });

  it("lists the signs that are present", () => {
    const status = dangerSignStatus({ ...allNegative, vomitsEverything: "yes" });
    expect(status.present).toEqual(["vomitsEverything"]);
  });
});

describe("symptom branches", () => {
  it("opens a branch whose screening question is yes", () => {
    expect(branchState({ cough: "yes" }, "cough")).toBe("open");
  });

  it("collapses rather than hides a branch answered no", () => {
    // Hiding a negative makes it look unasked; collapsing keeps the answer visible.
    expect(branchState({ diarrhoea: "no" }, "diarrhoea")).toBe("collapsed");
  });

  it("marks an unasked branch as unasked, not as negative", () => {
    expect(branchState({}, "fever")).toBe("unasked");
  });

  it("leaves non-branching sections open", () => {
    expect(branchState({}, "nutrition")).toBe("open");
  });
});

describe("DangerSignBanner", () => {
  const allNegative = Object.fromEntries(GENERAL_DANGER_SIGN_FIELDS.map((f) => [f, "no"]));

  it("raises an alert when a danger sign is present", () => {
    render(<DangerSignBanner answers={{ ...allNegative, convulsions: "yes" }} />);
    const banner = screen.getByTestId("danger-sign-banner");
    expect(banner.getAttribute("data-state")).toBe("present");
    expect(banner.getAttribute("role")).toBe("alert");
    expect(screen.getByTestId("danger-sign-present-convulsions")).toBeTruthy();
  });

  it("shows an incomplete assessment as incomplete, never as clear", () => {
    // A quiet banner on an unfinished check reads as "no danger signs" — this is the
    // single most important display decision in the journey.
    render(<DangerSignBanner answers={{ convulsions: "no" }} />);
    const banner = screen.getByTestId("danger-sign-banner");
    expect(banner.getAttribute("data-state")).toBe("incomplete");
    expect(screen.getByTestId("danger-sign-unassessed-vomitsEverything")).toBeTruthy();
  });

  it("confirms a clear result only once all four are assessed", () => {
    render(<DangerSignBanner answers={allNegative} />);
    const banner = screen.getByTestId("danger-sign-banner");
    expect(banner.getAttribute("data-state")).toBe("clear");
    expect(banner.textContent).toContain("All four general danger signs assessed");
  });

  it("still reports the outstanding signs when one is already positive", () => {
    render(<DangerSignBanner answers={{ convulsions: "yes" }} />);
    expect(screen.getByTestId("danger-sign-banner").textContent).toContain("still unassessed");
  });
});
