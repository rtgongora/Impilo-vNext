import { describe, expect, it } from "vitest";
import {
  GOVERNED_SOCIAL_HISTORY_CATEGORIES,
  GOVERNED_SOCIAL_HISTORY_CODES,
  isGovernedSocialCategory,
  labelForSocialCategory,
} from "../socialHistoryCategories";

describe("governed social history categories", () => {
  it("includes occupation, exposures, lifestyle and SDOH codes", () => {
    for (const code of [
      "TOBACCO",
      "ALCOHOL",
      "SUBSTANCE",
      "OCCUPATION",
      "ENVIRONMENTAL_EXPOSURE",
      "DIET",
      "PHYSICAL_ACTIVITY",
      "SLEEP",
      "SOCIAL_SUPPORT",
      "HOUSING",
      "FOOD_SECURITY",
    ]) {
      expect(GOVERNED_SOCIAL_HISTORY_CODES).toContain(code);
    }
    expect(GOVERNED_SOCIAL_HISTORY_CATEGORIES.length).toBe(11);
  });

  it("recognises known codes and leaves unknown appendable", () => {
    expect(isGovernedSocialCategory("tobacco")).toBe(true);
    expect(isGovernedSocialCategory("CUSTOM_LOCAL")).toBe(false);
    expect(labelForSocialCategory("HOUSING")).toBe("Housing");
    expect(labelForSocialCategory("CUSTOM_LOCAL")).toBe("CUSTOM_LOCAL");
  });
});
