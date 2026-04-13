/**
 * BedManagementScreen Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", () => ({
  Screen: ({ children }: any) => children,
  Header: ({ title }: any) => title,
  Badge: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  LoadingSpinner: () => null,
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: vi.fn().mockReturnValue({ data: { wards: [], beds: [] }, isLoading: false }),
  useMutation: vi.fn().mockReturnValue({ mutate: vi.fn(), isPending: false }),
  useQueryClient: vi.fn().mockReturnValue({ invalidateQueries: vi.fn() }),
}));

vi.mock("../../services/queueService", () => ({
  fetchBeds: vi.fn().mockResolvedValue({ wards: [], beds: [] }),
  assignBed: vi.fn().mockResolvedValue({}),
  dischargeBed: vi.fn().mockResolvedValue({}),
}));

describe("BedManagementScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/BedManagementScreen");
    expect(typeof mod.BedManagementScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/provider/BedManagementScreen");
    const element = React.createElement(mod.BedManagementScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.BedManagementScreen);
  });
});
