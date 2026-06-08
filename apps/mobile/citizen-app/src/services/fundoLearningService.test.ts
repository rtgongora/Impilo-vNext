import { describe, expect, it } from "vitest";
import { normalizeCitizenLearningSnapshot } from "./fundoLearningSummary";
import { enrolCitizenInCourse } from "./fundoLearningService";

describe("normalizeCitizenLearningSnapshot", () => {
  it("derives metrics from arrays", () => {
    const summary = normalizeCitizenLearningSnapshot({
      inProgress: [{ id: "1" }, { id: "2" }],
      completed: [{ id: "3" }],
      certificates: [{ id: "c1" }, { id: "c2" }, { id: "c3" }],
    });
    expect(summary).toEqual({
      inProgress: 2,
      completed: 1,
      certificates: 3,
    });
  });

  it("falls back to numeric counters", () => {
    const summary = normalizeCitizenLearningSnapshot({
      inProgressCount: "4",
      completedCount: 2,
      certificateCount: 1,
    });
    expect(summary).toEqual({
      inProgress: 4,
      completed: 2,
      certificates: 1,
    });
  });
});

describe("enrolCitizenInCourse", () => {
  it("is exported for citizen enrolment path", () => {
    expect(typeof enrolCitizenInCourse).toBe("function");
  });
});
