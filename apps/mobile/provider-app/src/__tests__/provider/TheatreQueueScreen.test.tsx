/**
 * TheatreQueueScreen honesty — a failed listTheatreQueue must never render as an empty board.
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

const theatreMocks = vi.hoisted(() => ({
  listTheatreQueue: vi.fn(),
  getTheatreCase: vi.fn(),
}));

vi.mock("../../services/procedureService", () => ({
  listTheatreQueue: (...a: unknown[]) => theatreMocks.listTheatreQueue(...a),
  getTheatreCase: (...a: unknown[]) => theatreMocks.getTheatreCase(...a),
  evaluateTheatreReadiness: vi.fn(),
  bookTheatreCase: vi.fn(),
  startTheatreCase: vi.fn(),
  draftTheatreNote: vi.fn(),
  signTheatreNote: vi.fn(),
  getTheatreNote: vi.fn(),
  recordTheatrePacuDisposition: vi.fn(),
  cancelTheatreCase: vi.fn(),
  reportTheatreSafetyEvent: vi.fn(),
  listTheatreSafetyEvents: vi.fn().mockResolvedValue([]),
  routeTheatreDeath: vi.fn(),
  listProcedureEpisodes: vi.fn().mockResolvedValue([]),
  getProcedureEpisode: vi.fn(),
  createProcedureEpisode: vi.fn(),
  submitProcedurePreop: vi.fn(),
  completeChecklistPhase: vi.fn(),
  requestProcedureConsent: vi.fn(),
  provideProcedureConsentExplanation: vi.fn(),
  verifyProcedureConsentIdentity: vi.fn(),
  grantProcedureConsent: vi.fn(),
  syncProcedureConsent: vi.fn(),
  startProcedure: vi.fn(),
  recordIntraop: vi.fn(),
  enterPacu: vi.fn(),
  completePostop: vi.fn(),
  completeProcedureEpisode: vi.fn(),
}));

vi.mock("../../stores/encounterStore", () => ({
  useEncounterStore: () => ({ activeEncounter: null }),
}));

function render(element: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() =>
    root.render(
      React.createElement(QueryClientProvider, { client: queryClient }, element),
    ),
  );
  return { container, root };
}

async function flush() {
  for (let i = 0; i < 8; i += 1) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
}

describe("TheatreQueueScreen (honesty)", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    if (mounted) {
      const { root } = mounted;
      act(() => root.unmount());
      mounted = undefined;
    }
  });

  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/TheatreQueueScreen");
    expect(typeof mod.TheatreQueueScreen).toBe("function");
  });

  it("shows an error on list failure — never an empty board", async () => {
    theatreMocks.listTheatreQueue.mockRejectedValue(new Error("BFF down"));
    const mod = await import("../../screens/provider/TheatreQueueScreen");
    mounted = render(React.createElement(mod.TheatreQueueScreen));
    await flush();

    expect(mounted.container.querySelector('[data-testid="theatre-queue-error"]')).toBeTruthy();
    expect(mounted.container.textContent).toMatch(/unavailable|Could not load/i);
    expect(mounted.container.querySelector('[data-testid="theatre-queue-empty"]')).toBeFalsy();
    expect(mounted.container.querySelector('[data-testid="theatre-queue-screen"]')).toBeFalsy();
  });

  it("renders queue rows when the list succeeds", async () => {
    theatreMocks.listTheatreQueue.mockResolvedValue([
      {
        id: "ep-1",
        procedure_name: "Appendectomy",
        patient_id: "CPID-1",
        status: "READY",
        triage_priority: "URGENT",
      },
    ]);
    const mod = await import("../../screens/provider/TheatreQueueScreen");
    mounted = render(React.createElement(mod.TheatreQueueScreen));
    await flush();

    expect(mounted.container.querySelector('[data-testid="theatre-queue-screen"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="theatre-queue-row-ep-1"]')).toBeTruthy();
    expect(mounted.container.textContent).toContain("Appendectomy");
    expect(mounted.container.querySelector('[data-testid="theatre-queue-error"]')).toBeFalsy();
  });

  it("opens the case screen from a queue row", async () => {
    theatreMocks.listTheatreQueue.mockResolvedValue([
      { id: "ep-2", procedure_name: "Hernia repair", status: "BOOKED" },
    ]);
    theatreMocks.getTheatreCase.mockResolvedValue({
      id: "ep-2",
      procedure_name: "Hernia repair",
      status: "BOOKED",
      triage_priority: "ELECTIVE",
    });
    const mod = await import("../../screens/provider/TheatreQueueScreen");
    mounted = render(React.createElement(mod.TheatreQueueScreen));
    await flush();

    const row = mounted.container.querySelector(
      '[data-testid="theatre-queue-row-ep-2"]',
    ) as HTMLElement;
    await act(async () => {
      row.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(theatreMocks.getTheatreCase).toHaveBeenCalledWith("ep-2");
    expect(mounted.container.querySelector('[data-testid="theatre-case-screen"]')).toBeTruthy();
  });
});
