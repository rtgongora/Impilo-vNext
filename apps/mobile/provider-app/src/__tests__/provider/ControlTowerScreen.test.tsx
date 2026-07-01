/**
 * ControlTowerScreen Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", () => ({
  Screen: ({ children }: any) => children,
  Header: ({ title }: any) => title,
  Card: ({ children }: any) => children,
  CardHeader: ({ title }: any) => title,
  CardBody: ({ children }: any) => children,
  Badge: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
}));

vi.mock("../../services/controlTowerService", () => ({
  fetchControlTowerAggregate: vi.fn().mockResolvedValue({
    facilityCount: 1,
    openAlertCount: 0,
    visibility: "ROW_DETAIL",
    facilities: [],
  }),
  fetchFacilityAlerts: vi.fn().mockResolvedValue([]),
  acknowledgeAlert: vi.fn().mockResolvedValue({ id: "a1", status: "ACKNOWLEDGED" }),
}));

describe("ControlTowerScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/ControlTowerScreen");
    expect(typeof mod.ControlTowerScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/provider/ControlTowerScreen");
    const element = React.createElement(mod.ControlTowerScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.ControlTowerScreen);
  });
});
