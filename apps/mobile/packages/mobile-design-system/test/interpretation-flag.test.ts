import { describe, it, expect } from "vitest";
import {
  interpretationFlag,
  isAbnormalInterpretation,
  isCriticalInterpretation,
  trendArrow,
} from "../src/clinical/interpretation-flag";

describe("interpretation flag mapping", () => {
  it("maps each interpretation code to a labelled style", () => {
    expect(interpretationFlag("CRITICAL_HIGH").label).toBe("Critical high");
    expect(interpretationFlag("LOW").label).toBe("Low");
    expect(interpretationFlag("NORMAL").label).toBe("Normal");
  });

  it("renders NO_REFERENCE_RANGE explicitly (never as normal)", () => {
    const f = interpretationFlag("NO_REFERENCE_RANGE");
    expect(f.label).toBe("No reference range");
    expect(f.label).not.toBe("Normal");
  });

  it("classifies abnormal and critical correctly", () => {
    expect(isAbnormalInterpretation("HIGH")).toBe(true);
    expect(isAbnormalInterpretation("NORMAL")).toBe(false);
    expect(isAbnormalInterpretation("NO_REFERENCE_RANGE")).toBe(false);
    expect(isCriticalInterpretation("CRITICAL_LOW")).toBe(true);
    expect(isCriticalInterpretation("HIGH")).toBe(false);
  });

  it("renders trend arrows", () => {
    expect(trendArrow("RISING")).toBe("▲");
    expect(trendArrow("FALLING")).toBe("▼");
    expect(trendArrow("STABLE")).toBe("▬");
    expect(trendArrow(null)).toBe("");
  });
});
