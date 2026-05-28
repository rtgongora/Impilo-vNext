import { describe, expect, it } from "vitest";
import { summarizeFundoMyLearning } from "./useFundoLms";

describe("summarizeFundoMyLearning", () => {
  it("derives KPIs from array payloads", () => {
    const kpis = summarizeFundoMyLearning({
      inProgress: [{ id: "ip-1" }, { id: "ip-2" }],
      required: [{ id: "req-1" }, { id: "req-2" }, { id: "req-3" }],
      overdue: [{ id: "od-1" }],
      cpdEligibleCompletions: [{ id: "cpd-1" }, { id: "cpd-2" }],
    });

    expect(kpis).toEqual({
      inProgress: 2,
      required: 3,
      overdue: 1,
      cpdEligible: 2,
    });
  });

  it("falls back to numeric aggregates when arrays are absent", () => {
    const kpis = summarizeFundoMyLearning({
      inProgressCount: 5,
      requiredCount: "4",
      overdueCount: 2,
      cpdEligibleCount: "3",
    });

    expect(kpis).toEqual({
      inProgress: 5,
      required: 4,
      overdue: 2,
      cpdEligible: 3,
    });
  });

  it("uses overdue as required baseline when required is missing", () => {
    const kpis = summarizeFundoMyLearning({
      overdue: [{ id: "od-1" }, { id: "od-2" }],
    });

    expect(kpis.required).toBe(2);
    expect(kpis.overdue).toBe(2);
  });
});
