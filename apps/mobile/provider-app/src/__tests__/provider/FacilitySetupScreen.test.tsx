/**
 * FacilitySetupScreen Tests — export + basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
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
  };
});

vi.mock("../../services/controlTowerService", () => ({
  fetchControlTowerAggregate: vi.fn().mockResolvedValue({
    facilityCount: 0,
    openAlertCount: 0,
    visibility: "ROW_DETAIL",
    facilities: [],
  }),
}));

vi.mock("../../services/facilitySetupService", () => ({
  fetchSetupState: vi.fn().mockResolvedValue({ facilityId: 1, readyForGoLive: false }),
  fetchFacilityUnits: vi.fn().mockResolvedValue([]),
  createFacilityUnit: vi.fn().mockResolvedValue({ id: 1 }),
  fetchServicePoints: vi.fn().mockResolvedValue([]),
  createServicePoint: vi.fn().mockResolvedValue({ id: "sp1" }),
  advanceSetupStep: vi.fn().mockResolvedValue({ facilityId: 1 }),
}));

describe("FacilitySetupScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/FacilitySetupScreen");
    expect(typeof mod.FacilitySetupScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/provider/FacilitySetupScreen");
    const element = React.createElement(mod.FacilitySetupScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.FacilitySetupScreen);
  });
});
