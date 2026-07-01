/**
 * PlaceModeDashboardScreen Tests — Verifies export and basic instantiation.
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

vi.mock("../../services/placeModeService", () => ({
  fetchPlaceModeSummary: vi.fn().mockResolvedValue({ openAlerts: 0, activeOutbreaks: 0, teams: 0 }),
  fetchSurveillanceAlerts: vi.fn().mockResolvedValue([]),
  fetchOutbreaks: vi.fn().mockResolvedValue([]),
  fetchFieldTeams: vi.fn().mockResolvedValue([]),
  createOutbreak: vi.fn().mockResolvedValue({ outbreakId: "o1" }),
  deployFieldTeam: vi.fn().mockResolvedValue(undefined),
}));

describe("PlaceModeDashboardScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/outreach/PlaceModeDashboardScreen");
    expect(typeof mod.PlaceModeDashboardScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/outreach/PlaceModeDashboardScreen");
    const element = React.createElement(mod.PlaceModeDashboardScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.PlaceModeDashboardScreen);
  });
});
