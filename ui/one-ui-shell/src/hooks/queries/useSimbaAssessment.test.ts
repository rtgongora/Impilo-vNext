import { describe, expect, it } from "vitest";
import { summarizeBand } from "./useSimbaAssessment";

describe("summarizeBand", () => {
  it("treats LOW as positive and not escalated", () => {
    const s = summarizeBand("LOW");
    expect(s.tone).toBe("positive");
    expect(s.escalated).toBe(false);
    expect(s.label).toBe("Low risk");
  });

  it("treats MODERATE as info and not escalated", () => {
    expect(summarizeBand("MODERATE")).toMatchObject({ tone: "info", escalated: false });
  });

  it("escalates HIGH and NEEDS_CLINICAL_REVIEW", () => {
    expect(summarizeBand("HIGH").escalated).toBe(true);
    expect(summarizeBand("NEEDS_CLINICAL_REVIEW").escalated).toBe(true);
  });

  it("marks URGENT_RED_FLAG urgent + escalated", () => {
    const s = summarizeBand("URGENT_RED_FLAG");
    expect(s.tone).toBe("urgent");
    expect(s.escalated).toBe(true);
  });

  it("falls back gracefully for null/unknown bands", () => {
    expect(summarizeBand(null).label).toBe("Not assessed");
    expect(summarizeBand("WHATEVER").label).toBe("WHATEVER");
    expect(summarizeBand(undefined).escalated).toBe(false);
  });
});
