/**
 * Phase G4 — proves Work Home renders BFF section status honestly (a DEGRADED
 * section must never read as an empty one) and that the workplace switcher
 * surfaces a refused switch rather than failing silently.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  const React = await import("react");
  return {
    ...actual,
    /* eslint-disable @typescript-eslint/no-explicit-any */
    Screen: ({ children }: any) => React.createElement("div", null, children),
    Header: ({ title }: any) => React.createElement("div", null, title),
    Card: ({ children }: any) => React.createElement("div", null, children),
    CardHeader: ({ children }: any) => React.createElement("div", null, children),
    CardBody: ({ children }: any) => React.createElement("div", null, children),
    Badge: ({ children }: any) => React.createElement("span", null, children),
    Button: ({ title, testID, onPress }: any) =>
      React.createElement("button", { "data-testid": testID, onClick: onPress }, title),
    LoadingSpinner: () => null,
    EmptyState: ({ title, message }: any) => React.createElement("div", null, `${title} ${message ?? ""}`),
    ErrorState: ({ title, message }: any) => React.createElement("div", null, `${title} ${message ?? ""}`),
    /* eslint-enable @typescript-eslint/no-explicit-any */
  };
});

const workHomeMocks = vi.hoisted(() => ({ getWorkHome: vi.fn(), getWorkHomeSection: vi.fn() }));
vi.mock("../../services/workHomeService", () => workHomeMocks);

const switchMocks = vi.hoisted(() => ({ switchWorkContext: vi.fn(), switching: false, error: null as string | null }));
vi.mock("../../hooks/useSwitchWorkContext", () => ({
  useSwitchWorkContext: () => switchMocks,
}));

const authState = vi.hoisted(() => ({
  session: { workContextId: "ctx-1", workMode: "CLINICAL_CARE" } as Record<string, unknown> | null,
}));
vi.mock("@impilo/mobile-auth", () => ({ useAuth: () => authState }));

const appState = vi.hoisted(() => ({ resolvedWorkContexts: null as unknown }));
vi.mock("../../stores/appStore", () => ({
  useAppStore: () => appState,
  appStore: { getState: () => appState, subscribe: vi.fn(() => () => {}) },
}));

function render(element: React.ReactElement) {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => root.render(element));
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

describe("WorkHomeScreen (G4)", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    authState.session = { workContextId: "ctx-1", workMode: "CLINICAL_CARE" };
    appState.resolvedWorkContexts = null;
    switchMocks.error = null;
    switchMocks.switching = false;
  });

  afterEach(() => {
    if (mounted) {
      const { root } = mounted;
      act(() => root.unmount());
      mounted = undefined;
    }
  });

  it("renders a DEGRADED section as unavailable with a retry — never as an empty list", async () => {
    workHomeMocks.getWorkHome.mockResolvedValue({
      contextId: "ctx-1",
      family: "FACILITY_CLINICAL",
      mode: "CLINICAL_CARE",
      sections: [
        { sectionId: "clinical-worklist", title: "Worklist", status: "DEGRADED", items: [], note: "PCT timed out" },
      ],
    });
    const mod = await import("../../screens/provider/WorkHomeScreen");
    mounted = render(React.createElement(mod.WorkHomeScreen));
    await flush();

    expect(byTestId(mounted.container, "work-home-degraded-clinical-worklist")).not.toBeNull();
    expect(byTestId(mounted.container, "work-home-retry-clinical-worklist")).not.toBeNull();
    expect(mounted.container.textContent).toContain("PCT timed out");
    expect(mounted.container.textContent).toContain("not an empty list");
  });

  it("renders real section items when the composition is healthy", async () => {
    workHomeMocks.getWorkHome.mockResolvedValue({
      contextId: "ctx-1",
      family: "FACILITY_CLINICAL",
      mode: "CLINICAL_CARE",
      sections: [
        {
          sectionId: "clinical-worklist",
          title: "Worklist",
          status: "OK",
          items: [{ id: "p1", title: "Chipo Ncube", description: "Awaiting review" }],
        },
      ],
    });
    const mod = await import("../../screens/provider/WorkHomeScreen");
    mounted = render(React.createElement(mod.WorkHomeScreen));
    await flush();

    expect(byTestId(mounted.container, "work-home-item-p1")).not.toBeNull();
    expect(mounted.container.textContent).toContain("Chipo Ncube");
    expect(byTestId(mounted.container, "work-home-degraded-clinical-worklist")).toBeNull();
  });

  it("shows the workplace picker instead of Work Home when no context is active", async () => {
    authState.session = { workContextId: undefined, workMode: undefined };
    appState.resolvedWorkContexts = [
      {
        contextId: "ctx-2",
        contextKind: "facility",
        sourceSystem: "VASHANDI",
        availableModes: ["CLINICAL_CARE"],
        defaultMode: "CLINICAL_CARE",
        restrictions: [],
        label: "Harare Central",
        groupHint: "today",
      },
    ];
    const mod = await import("../../screens/provider/WorkHomeScreen");
    mounted = render(React.createElement(mod.WorkHomeScreen));
    await flush();

    expect(byTestId(mounted.container, "work-context-switcher")).not.toBeNull();
    expect(byTestId(mounted.container, "select-context-ctx-2")).not.toBeNull();
    expect(mounted.container.textContent).toContain("Harare Central");
    expect(workHomeMocks.getWorkHome).not.toHaveBeenCalled();
  });

  it("surfaces a refused switch (e.g. unsynced work) rather than failing silently", async () => {
    authState.session = { workContextId: undefined, workMode: undefined };
    appState.resolvedWorkContexts = [];
    switchMocks.error = "You have 2 unsynced changes. Sync before switching workplace.";
    const mod = await import("../../screens/provider/WorkHomeScreen");
    mounted = render(React.createElement(mod.WorkHomeScreen));
    await flush();

    expect(byTestId(mounted.container, "work-switch-error")).not.toBeNull();
    expect(mounted.container.textContent).toContain("2 unsynced changes");
  });
});
