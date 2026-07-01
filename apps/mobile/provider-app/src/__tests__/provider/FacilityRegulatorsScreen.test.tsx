/**
 * FacilityRegulatorsScreen Tests — export + basic instantiation.
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
    facilityCount: 0,
    openAlertCount: 0,
    visibility: "ROW_DETAIL",
    facilities: [],
  }),
}));

vi.mock("../../services/regulatorService", () => ({
  fetchFacilityRegulators: vi.fn().mockResolvedValue([]),
  linkRegulator: vi.fn().mockResolvedValue({ id: "r1" }),
}));

describe("FacilityRegulatorsScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/FacilityRegulatorsScreen");
    expect(typeof mod.FacilityRegulatorsScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/provider/FacilityRegulatorsScreen");
    const element = React.createElement(mod.FacilityRegulatorsScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.FacilityRegulatorsScreen);
  });
});
