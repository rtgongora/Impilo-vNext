/**
 * ChallengesScreen Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", () => ({
  Card: ({ children }: any) => children,
  CardHeader: ({ title }: any) => title,
  CardBody: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  Badge: ({ children }: any) => children,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
}));

vi.mock("../../services/challengeService", () => ({
  fetchChallenges: vi.fn().mockResolvedValue([]),
  joinChallenge: vi.fn().mockResolvedValue({}),
  fetchMyGoals: vi.fn().mockResolvedValue([]),
  createGoal: vi.fn().mockResolvedValue({ id: "goal-1" }),
}));

describe("ChallengesScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/personal/ChallengesScreen");
    expect(typeof mod.ChallengesScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/personal/ChallengesScreen");
    const element = React.createElement(mod.ChallengesScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.ChallengesScreen);
  });
});
