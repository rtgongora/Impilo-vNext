/**
 * OfflineDashboardScreen Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", () => ({
  Screen: ({ children }: any) => children,
  Header: ({ title }: any) => title,
  Card: ({ children }: any) => children,
  CardBody: ({ children }: any) => children,
  CardHeader: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  Badge: ({ children }: any) => children,
  TextField: () => null,
  LoadingSpinner: () => null,
  ErrorState: ({ title }: any) => title,
}));

vi.mock("@impilo/mobile-offline", () => ({
  useSyncEngine: vi.fn().mockReturnValue({ status: "idle", pendingCount: 0, lastSyncAt: null, triggerSync: vi.fn() }),
  useEdgeSnapshot: vi.fn().mockReturnValue({ snapshot: null, loading: false, download: vi.fn() }),
  useConflicts: vi.fn().mockReturnValue({ conflicts: [] }),
}));

vi.mock("../../stores/appStore", () => ({
  useAppStore: Object.assign(
    vi.fn().mockReturnValue({ isOnline: true, facilityId: "fac-1", pendingSyncCount: 0 }),
    { setPendingSyncCount: vi.fn() },
  ),
}));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue({ data: { data: {} } }),
  },
}));

describe("OfflineDashboardScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/offline/OfflineDashboardScreen");
    expect(typeof mod.OfflineDashboardScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/offline/OfflineDashboardScreen");
    const element = React.createElement(mod.OfflineDashboardScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.OfflineDashboardScreen);
  });
});
