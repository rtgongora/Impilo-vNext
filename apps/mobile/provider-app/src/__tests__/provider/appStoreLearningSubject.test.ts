import { describe, expect, it } from "vitest";
import { appStore } from "../../stores/appStore";

describe("provider appStore learning subject continuity", () => {
  it("stores learning subject identity for Fundo journeys", () => {
    appStore.getState().setLearningSubject("PROVIDER", "provider-123");
    const state = appStore.getState();
    expect(state.learningSubjectType).toBe("PROVIDER");
    expect(state.learningSubjectId).toBe("provider-123");
  });
});
