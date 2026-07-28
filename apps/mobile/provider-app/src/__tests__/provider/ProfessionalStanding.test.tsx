/**
 * Phase G5 — proves the Professional Standing section does not launder a failed
 * or degraded professional-standing read into a calm "nothing outstanding".
 * The service-layer test (professionalAlertsService.test.ts) proves the data
 * contract; this file proves the UI honours it.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";

// Stubbed the same way every other screen test in this app does — the real
// design-system components carry RN style objects react-dom rejects. Kept
// faithful where this test asserts on it: EmptyState must still surface its
// title, so a stubbed-away "Nothing outstanding" can't fake a pass.
vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  const React = await import("react");
  return {
    ...actual,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    Screen: ({ children }: any) => React.createElement("div", null, children),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    Header: ({ title }: any) => React.createElement("div", null, title),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    Card: ({ children }: any) => React.createElement("div", null, children),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    CardHeader: ({ children }: any) => React.createElement("div", null, children),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    CardBody: ({ children }: any) => React.createElement("div", null, children),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    Badge: ({ children }: any) => React.createElement("span", null, children),
    Button: () => null,
    LoadingSpinner: () => null,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    EmptyState: ({ title, message }: any) => React.createElement("div", null, `${title} ${message ?? ""}`),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ErrorState: ({ title, message }: any) => React.createElement("div", null, `${title} ${message ?? ""}`),
  };
});

const profileMocks = vi.hoisted(() => ({
  getProfile: vi.fn(),
  getNotices: vi.fn(),
  updateProfile: vi.fn(),
}));
vi.mock("../../services/profileService", () => profileMocks);

const alertsMocks = vi.hoisted(() => ({ getProfessionalAlerts: vi.fn() }));
vi.mock("../../services/professionalAlertsService", () => alertsMocks);

function render(element: React.ReactElement) {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(element);
  });
  return { container, root };
}

async function flush() {
  for (let i = 0; i < 6; i += 1) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
}

function byTestId(container: HTMLElement, testId: string) {
  return container.querySelector(`[data-testid="${testId}"]`);
}

describe("ProfessionalProfileScreen — Professional Standing (G5)", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    profileMocks.getProfile.mockResolvedValue({
      givenName: "Tendai",
      familyName: "Moyo",
      email: "t@example.org",
      phone: "0771000000",
      officePhone: null,
    });
    profileMocks.getNotices.mockResolvedValue([]);
  });

  afterEach(() => {
    if (mounted) {
      const { root } = mounted;
      act(() => root.unmount());
      mounted = undefined;
    }
  });

  it("renders a degraded warning — not an all-clear — when the alerts read throws", async () => {
    alertsMocks.getProfessionalAlerts.mockRejectedValue(new Error("network down"));
    const mod = await import("../../screens/provider/ProfessionalProfileScreen");
    mounted = render(React.createElement(mod.ProfessionalProfileScreen));
    await flush();

    expect(byTestId(mounted.container, "professional-alerts-degraded")).not.toBeNull();
    expect(mounted.container.textContent).toContain("not an all-clear");
    expect(mounted.container.textContent).not.toContain("Nothing outstanding");
  });

  it("renders a degraded warning when the BFF itself reports DEGRADED with zero items", async () => {
    alertsMocks.getProfessionalAlerts.mockResolvedValue({
      status: "DEGRADED",
      items: [],
      note: "VARAPI did not answer in time",
    });
    const mod = await import("../../screens/provider/ProfessionalProfileScreen");
    mounted = render(React.createElement(mod.ProfessionalProfileScreen));
    await flush();

    expect(byTestId(mounted.container, "professional-alerts-degraded")).not.toBeNull();
    expect(mounted.container.textContent).toContain("VARAPI did not answer in time");
    expect(mounted.container.textContent).not.toContain("Nothing outstanding");
  });

  it("renders real licence/CPD alert items when the read succeeds", async () => {
    alertsMocks.getProfessionalAlerts.mockResolvedValue({
      status: "OK",
      items: [
        {
          id: "licence:l1",
          kind: "LICENCE_ALERT",
          source: "varapi",
          title: "Nursing licence — Nurses Council",
          description: "Expires in 21 days",
          priority: "HIGH",
          due_at: "2026-08-18T00:00:00Z",
        },
        {
          id: "cpd:c1",
          kind: "CPD_ALERT",
          source: "varapi",
          title: "CPD points outstanding",
          description: "12 of 30 points earned",
          priority: "HIGH",
        },
      ],
    });
    const mod = await import("../../screens/provider/ProfessionalProfileScreen");
    mounted = render(React.createElement(mod.ProfessionalProfileScreen));
    await flush();

    expect(byTestId(mounted.container, "professional-alert-licence:l1")).not.toBeNull();
    expect(byTestId(mounted.container, "professional-alert-cpd:c1")).not.toBeNull();
    expect(mounted.container.textContent).toContain("Nursing licence");
    expect(mounted.container.textContent).toContain("CPD points outstanding");
    expect(byTestId(mounted.container, "professional-alerts-degraded")).toBeNull();
  });

  it("shows 'nothing outstanding' only when the read genuinely succeeded with no items", async () => {
    alertsMocks.getProfessionalAlerts.mockResolvedValue({ status: "EMPTY", items: [], note: "No items to show" });
    const mod = await import("../../screens/provider/ProfessionalProfileScreen");
    mounted = render(React.createElement(mod.ProfessionalProfileScreen));
    await flush();

    expect(byTestId(mounted.container, "professional-alerts-degraded")).toBeNull();
    expect(mounted.container.textContent).toContain("Nothing outstanding");
  });
});
