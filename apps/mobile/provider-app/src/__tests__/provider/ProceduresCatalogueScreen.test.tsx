/**
 * ProceduresCatalogueScreen honesty — catalogue failure ≠ empty catalogue.
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
    Header: ({ title }: any) => React.createElement("div", null, title),
    Badge: ({ label }: any) => React.createElement("span", null, label),
    Button: ({ title, testID, onPress, disabled }: any) =>
      React.createElement(
        "button",
        { "data-testid": testID, onClick: onPress, disabled },
        title,
      ),
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

vi.mock("@impilo/mobile-auth", () => ({
  authStore: { getState: () => ({ session: { actorId: "prov-1", providerId: "prov-1" } }) },
}));

const catalogueMocks = vi.hoisted(() => ({
  searchCatalogue: vi.fn(),
  getCatalogueDetail: vi.fn(),
  evaluateAppropriateness: vi.fn(),
  resolveCompetence: vi.fn(),
  getSafetyPauseTemplate: vi.fn(),
  listSedationLevels: vi.fn(),
  getSedationLevelDetail: vi.fn(),
  listRecoverySettings: vi.fn(),
  getRecoverySettingDetail: vi.fn(),
  getAftercareTemplate: vi.fn(),
  listAnalyticsIndicators: vi.fn(),
  listClavienDindoGrades: vi.fn(),
  getComplicationProfile: vi.fn(),
}));

vi.mock("../../services/proceduresCatalogueService", () => catalogueMocks);

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

describe("ProceduresCatalogueScreen (honesty)", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    catalogueMocks.listSedationLevels.mockResolvedValue([]);
    catalogueMocks.listRecoverySettings.mockResolvedValue([]);
    catalogueMocks.listAnalyticsIndicators.mockResolvedValue({ indicators: [] });
    catalogueMocks.listClavienDindoGrades.mockResolvedValue([]);
  });

  afterEach(() => {
    if (mounted) {
      act(() => mounted!.root.unmount());
      mounted = undefined;
    }
  });

  it("shows unavailable on catalogue failure — never empty", async () => {
    catalogueMocks.searchCatalogue.mockRejectedValue(new Error("BFF down"));
    const mod = await import("../../screens/provider/ProceduresCatalogueScreen");
    mounted = render(React.createElement(mod.ProceduresCatalogueScreen));
    await flush();

    expect(mounted.container.querySelector('[data-testid="procedures-catalogue-error"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="procedures-catalogue-empty"]')).toBeFalsy();
    expect(mounted.container.textContent).toMatch(/unavailable|Could not load/i);
  });

  it("renders catalogue rows when search succeeds", async () => {
    catalogueMocks.searchCatalogue.mockResolvedValue({
      items: [
        {
          definitionCode: "PROC-LAP",
          clinicalName: "Laparotomy",
          owningSpecialty: "GENERAL_SURGERY",
          category: "THEATRE",
        },
      ],
      matched: 1,
      catalogueSize: 10,
    });
    const mod = await import("../../screens/provider/ProceduresCatalogueScreen");
    mounted = render(React.createElement(mod.ProceduresCatalogueScreen));
    await flush();

    expect(mounted.container.querySelector('[data-testid="procedures-catalogue-screen"]')).toBeTruthy();
    expect(
      mounted.container.querySelector('[data-testid="procedures-catalogue-row-PROC-LAP"]'),
    ).toBeTruthy();
    expect(mounted.container.textContent).toContain("Laparotomy");
    expect(mounted.container.querySelector('[data-testid="procedures-catalogue-error"]')).toBeFalsy();
  });
});
