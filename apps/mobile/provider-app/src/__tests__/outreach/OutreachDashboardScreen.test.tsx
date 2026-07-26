/**
 * OutreachDashboardScreen Tests — Verifies export and basic instantiation.
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
  CardHeader: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  Badge: ({ children }: any) => children,
  LoadingSpinner: () => null,
  ErrorState: ({ title }: any) => title,
  };
});

vi.mock("../../stores/appStore", () => ({
  useAppStore: vi.fn().mockReturnValue({ facilityId: "fac-1", isOnline: true }),
}));

vi.mock("../../services/householdService", () => ({
  getHouseholds: vi.fn().mockResolvedValue({ households: [], totalElements: 0 }),
}));

vi.mock("../../services/taskService", () => ({
  getMyTasks: vi.fn().mockResolvedValue({ tasks: [], totalElements: 0 }),
}));

vi.mock("@impilo/mobile-offline", () => ({
  useSyncEngine: vi.fn().mockReturnValue({ status: "idle", pendingCount: 0 }),
}));

describe("OutreachDashboardScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/outreach/OutreachDashboardScreen");
    expect(typeof mod.OutreachDashboardScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/outreach/OutreachDashboardScreen");
    const element = React.createElement(mod.OutreachDashboardScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.OutreachDashboardScreen);
  });
});
