/**
 * WellnessSection Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", () => ({
  Card: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  Badge: ({ children }: any) => children,
  LoadingSpinner: () => null,
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: vi.fn().mockReturnValue({ data: [], isLoading: false }),
  useMutation: vi.fn().mockReturnValue({ mutate: vi.fn(), isPending: false }),
  useQueryClient: vi.fn().mockReturnValue({ invalidateQueries: vi.fn() }),
}));

vi.mock("../../services/wellnessService", () => ({
  fetchActivities: vi.fn().mockResolvedValue([]),
  logActivity: vi.fn().mockResolvedValue({}),
  fetchVitals: vi.fn().mockResolvedValue([]),
  logVital: vi.fn().mockResolvedValue({}),
  fetchMoodLog: vi.fn().mockResolvedValue([]),
  logMood: vi.fn().mockResolvedValue({}),
  fetchChallenges: vi.fn().mockResolvedValue([]),
  joinChallenge: vi.fn().mockResolvedValue({}),
}));

vi.mock("../../stores/appStore", () => ({
  useAppStore: vi.fn().mockReturnValue({ profile: { cpid: "CPID-TEST" } }),
}));

describe("WellnessSection", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/personal/WellnessSection");
    expect(typeof mod.WellnessSection).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/personal/WellnessSection");
    const element = React.createElement(mod.WellnessSection);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.WellnessSection);
  });
});
