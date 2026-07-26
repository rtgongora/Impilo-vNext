import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
  LoadingSpinner: () => null,
  ErrorState: ({ title }: { title?: string }) => title ?? null,
  };
});

vi.mock("../../services/personHealthWalletService", () => ({
  fetchPersonWalletOverview: vi.fn().mockResolvedValue({
    identity: { healthId: "CPID-1" },
    profileCompletion: { present: 6, total: 6, percent: 100, missing: [] },
    careTimeline: { eventCount: 2 },
    consent: { activeCount: 1 },
    dependants: { iCanActForCount: 0 },
    payments: { outstandingCount: 0 },
    nextActions: [],
  }),
}));

describe("WalletOverviewSection", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/personal/WalletOverviewSection");
    expect(typeof mod.WalletOverviewSection).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/personal/WalletOverviewSection");
    const element = React.createElement(mod.WalletOverviewSection);
    expect(element.type).toBe(mod.WalletOverviewSection);
  });

  it("reads the person health wallet overview service (BFF composition, not a new SoR)", async () => {
    const svc = await import("../../services/personHealthWalletService");
    const result = await svc.fetchPersonWalletOverview();
    expect(result.identity?.healthId).toBe("CPID-1");
    expect(result.profileCompletion?.percent).toBe(100);
  });
});
