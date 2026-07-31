/**
 * TheatreOpsHubScreen honesty — board / referrals / waitlist / lists failures ≠ empty.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  const React = await import("react");
  return {
    ...actual,
    /* eslint-disable @typescript-eslint/no-explicit-any */
    Screen: ({ children }: any) => React.createElement("div", null, children),
    Header: ({ title, onBack }: any) =>
      React.createElement(
        "div",
        null,
        title,
        onBack
          ? React.createElement("button", { "data-testid": "header-back", onClick: onBack }, "Back")
          : null,
      ),
    Badge: ({ label }: any) => React.createElement("span", null, label),
    Button: ({ title, testID, onPress }: any) =>
      React.createElement("button", { "data-testid": testID, onClick: onPress }, title),
    LoadingSpinner: () => React.createElement("div", { "data-testid": "loading" }, "loading"),
    ErrorState: ({ title, message, testID, onRetry }: any) =>
      React.createElement(
        "div",
        { "data-testid": testID },
        React.createElement("div", null, title),
        React.createElement("div", null, message),
        onRetry
          ? React.createElement("button", { "data-testid": `${testID}-retry`, onClick: onRetry }, "Retry")
          : null,
      ),
    /* eslint-enable @typescript-eslint/no-explicit-any */
  };
});

const opsMocks = vi.hoisted(() => ({
  fetchTheatreReadinessBoard: vi.fn(),
  listSurgicalReferralWorklist: vi.fn(),
  decideSurgicalReferral: vi.fn(),
  listSurgicalWaitlist: vi.fn(),
  listTheatreSessions: vi.fn(),
  getTheatreSession: vi.fn(),
  runTheatreUtilisationReport: vi.fn(),
  getAnaesthesiaChart: vi.fn(),
  addAnaesthesiaChartEntry: vi.fn(),
  listInstrumentSets: vi.fn(),
  issueInstrumentSet: vi.fn(),
  returnInstrumentSet: vi.fn(),
  listControlledDrugs: vi.fn(),
  recordControlledDrug: vi.fn(),
}));

vi.mock("../../services/theatreOpsService", () => opsMocks);

function render(element: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() =>
    root.render(React.createElement(QueryClientProvider, { client: queryClient }, element)),
  );
  return { container, root };
}

async function flush() {
  for (let i = 0; i < 10; i += 1) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
}

describe("TheatreOpsHubScreen (honesty)", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    if (mounted) {
      act(() => mounted!.root.unmount());
      mounted = undefined;
    }
  });

  it("renders the ops hub links", async () => {
    const mod = await import("../../screens/provider/TheatreOpsHubScreen");
    mounted = render(React.createElement(mod.TheatreOpsHubScreen, { onBack: () => undefined }));
    await flush();
    expect(mounted.container.querySelector('[data-testid="theatre-ops-hub"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="theatre-ops-readiness"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="theatre-ops-referrals"]')).toBeTruthy();
  });

  it("surfaces readiness board failure — never empty day", async () => {
    opsMocks.fetchTheatreReadinessBoard.mockRejectedValue(new Error("down"));
    const mod = await import("../../screens/provider/TheatreOpsHubScreen");
    mounted = render(React.createElement(mod.TheatreOpsHubScreen, { onBack: () => undefined }));
    await flush();

    await act(async () => {
      mounted!.container
        .querySelector('[data-testid="theatre-ops-readiness"]')!
        .dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(mounted.container.querySelector('[data-testid="theatre-readiness-board-error"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="theatre-readiness-board-empty"]')).toBeFalsy();
  });

  it("surfaces referrals inbox failure — never empty inbox", async () => {
    opsMocks.listSurgicalReferralWorklist.mockRejectedValue(new Error("down"));
    const mod = await import("../../screens/provider/TheatreOpsHubScreen");
    mounted = render(React.createElement(mod.TheatreOpsHubScreen, { onBack: () => undefined }));
    await flush();

    await act(async () => {
      mounted!.container
        .querySelector('[data-testid="theatre-ops-referrals"]')!
        .dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(mounted.container.querySelector('[data-testid="theatre-referrals-error"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="theatre-referrals-empty"]')).toBeFalsy();
  });
});
