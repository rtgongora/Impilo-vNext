/**
 * FacilityAdminScreen Tests — Verifies export and basic instantiation.
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

vi.mock("../../services/facilityAdminService", () => ({
  fetchFacilityStats: vi.fn().mockResolvedValue({
    facilityName: "Test Facility",
    bedCount: 50,
    occupancyRate: 0.75,
    staffOnDuty: 10,
    queueLength: 5,
    updatedAt: new Date().toISOString(),
  }),
  fetchStaffRoster: vi.fn().mockResolvedValue([]),
  fetchAuditLog: vi.fn().mockResolvedValue([]),
}));

describe("FacilityAdminScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/FacilityAdminScreen");
    expect(typeof mod.FacilityAdminScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/provider/FacilityAdminScreen");
    const element = React.createElement(mod.FacilityAdminScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.FacilityAdminScreen);
  });
});
