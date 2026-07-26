/**
 * CommunitiesScreen Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
  Badge: ({ children }: any) => children,
  LoadingSpinner: () => null,
  };
});

vi.mock("@tanstack/react-query", () => ({
  useQuery: vi.fn().mockReturnValue({ data: [], isLoading: false }),
  useMutation: vi.fn().mockReturnValue({ mutate: vi.fn(), mutateAsync: vi.fn(), isPending: false }),
  useQueryClient: vi.fn().mockReturnValue({ invalidateQueries: vi.fn() }),
}));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue({ data: { data: [] } }),
    post: vi.fn().mockResolvedValue({}),
  },
}));

vi.mock("../../stores/appStore", () => ({
  useAppStore: vi.fn().mockReturnValue({ profile: { cpid: "CPID-TEST" } }),
}));

describe("CommunitiesScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/social/CommunitiesScreen");
    expect(typeof mod.CommunitiesScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/social/CommunitiesScreen");
    const element = React.createElement(mod.CommunitiesScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.CommunitiesScreen);
  });
});
