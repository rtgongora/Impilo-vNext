/**
 * QueueManagementScreen Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
  Screen: ({ children }: any) => children,
  Header: ({ title }: any) => title,
  Card: ({ children }: any) => children,
  CardBody: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  Badge: ({ children }: any) => children,
  LoadingSpinner: () => null,
  };
});

vi.mock("@tanstack/react-query", () => ({
  useQuery: vi.fn().mockReturnValue({ data: [], isLoading: false }),
  useMutation: vi.fn().mockReturnValue({ mutate: vi.fn(), isPending: false }),
  useQueryClient: vi.fn().mockReturnValue({ invalidateQueries: vi.fn() }),
}));

vi.mock("../../services/queueService", () => ({
  fetchQueue: vi.fn().mockResolvedValue([]),
  callNext: vi.fn().mockResolvedValue({}),
  completeEntry: vi.fn().mockResolvedValue({}),
  fetchQueueStats: vi.fn().mockResolvedValue({ waiting: 0, inProgress: 0, completedToday: 0 }),
}));

describe("QueueManagementScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/QueueManagementScreen");
    expect(typeof mod.QueueManagementScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/provider/QueueManagementScreen");
    const element = React.createElement(mod.QueueManagementScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.QueueManagementScreen);
  });
});
