/**
 * PharmacyDispensingScreen Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
  Screen: ({ children }: any) => children,
  Header: ({ title }: any) => title,
  Badge: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  LoadingSpinner: () => null,
  };
});

vi.mock("@tanstack/react-query", () => ({
  useQuery: vi.fn().mockReturnValue({ data: [], isLoading: false }),
  useMutation: vi.fn().mockReturnValue({ mutate: vi.fn(), isPending: false }),
  useQueryClient: vi.fn().mockReturnValue({ invalidateQueries: vi.fn() }),
}));

vi.mock("../../services/queueService", () => ({
  fetchPendingDispensing: vi.fn().mockResolvedValue([]),
  dispense: vi.fn().mockResolvedValue({}),
  verifyFiveRights: vi.fn().mockResolvedValue({ rightPatient: true, rightDrug: true, rightDose: true, rightRoute: true, rightTime: true }),
}));

vi.mock("../../stores/appStore", () => ({
  useAppStore: vi.fn().mockReturnValue({}),
}));

describe("PharmacyDispensingScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/PharmacyDispensingScreen");
    expect(typeof mod.PharmacyDispensingScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/provider/PharmacyDispensingScreen");
    const element = React.createElement(mod.PharmacyDispensingScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.PharmacyDispensingScreen);
  });
});
