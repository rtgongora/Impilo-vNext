/**
 * MonitoringSection Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", () => ({
  Button: ({ title, onPress }: any) => null,
  Badge: ({ children }: any) => children,
  LoadingSpinner: () => null,
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: vi.fn().mockReturnValue({ data: [], isLoading: false }),
  useMutation: vi.fn().mockReturnValue({ mutate: vi.fn(), isPending: false }),
  useQueryClient: vi.fn().mockReturnValue({ invalidateQueries: vi.fn() }),
}));

vi.mock("../../services/monitoringService", () => ({
  fetchDevices: vi.fn().mockResolvedValue([]),
  pairDevice: vi.fn().mockResolvedValue({}),
  syncDevice: vi.fn().mockResolvedValue({}),
}));

vi.mock("../../stores/appStore", () => ({
  useAppStore: vi.fn().mockReturnValue({ profile: { cpid: "CPID-TEST" } }),
}));

describe("MonitoringSection", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/personal/MonitoringSection");
    expect(typeof mod.MonitoringSection).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/personal/MonitoringSection");
    const element = React.createElement(mod.MonitoringSection);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.MonitoringSection);
  });
});
