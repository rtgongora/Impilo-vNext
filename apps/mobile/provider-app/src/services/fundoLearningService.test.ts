import { describe, expect, it } from "vitest";
import { parseAssessmentOptions, summarizeCpdCredits } from "./fundoLearningService";

describe("provider fundoLearningService", () => {
  it("summarizeCpdCredits counts evidence arrays", () => {
    expect(
      summarizeCpdCredits({
        cpdEligibleCompletions: [{ id: "1" }, { id: "2" }],
      }),
    ).toBe(2);
  });

  it("parseAssessmentOptions supports JSON and delimiter formats", () => {
    expect(parseAssessmentOptions('["Tab","Ctrl+K"]')).toEqual(["Tab", "Ctrl+K"]);
    expect(parseAssessmentOptions("Tab|Ctrl+K")).toEqual(["Tab", "Ctrl+K"]);
  });
});
