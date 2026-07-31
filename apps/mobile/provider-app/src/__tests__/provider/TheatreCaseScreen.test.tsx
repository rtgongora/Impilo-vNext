/**
 * TheatreCaseScreen honesty — a failed case GET must never look like a ready/empty case.
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

const theatreMocks = vi.hoisted(() => ({
  getTheatreCase: vi.fn(),
  evaluateTheatreReadiness: vi.fn(),
  bookTheatreCase: vi.fn(),
  startTheatreCase: vi.fn(),
  draftTheatreNote: vi.fn(),
  signTheatreNote: vi.fn(),
  getTheatreNote: vi.fn(),
  recordTheatrePacuDisposition: vi.fn(),
  cancelTheatreCase: vi.fn(),
  reportTheatreSafetyEvent: vi.fn(),
  listTheatreSafetyEvents: vi.fn(),
  routeTheatreDeath: vi.fn(),
}));

vi.mock("../../services/procedureService", () => ({
  getTheatreCase: (...a: unknown[]) => theatreMocks.getTheatreCase(...a),
  evaluateTheatreReadiness: (...a: unknown[]) => theatreMocks.evaluateTheatreReadiness(...a),
  bookTheatreCase: (...a: unknown[]) => theatreMocks.bookTheatreCase(...a),
  startTheatreCase: (...a: unknown[]) => theatreMocks.startTheatreCase(...a),
  draftTheatreNote: (...a: unknown[]) => theatreMocks.draftTheatreNote(...a),
  signTheatreNote: (...a: unknown[]) => theatreMocks.signTheatreNote(...a),
  getTheatreNote: (...a: unknown[]) => theatreMocks.getTheatreNote(...a),
  recordTheatrePacuDisposition: (...a: unknown[]) => theatreMocks.recordTheatrePacuDisposition(...a),
  cancelTheatreCase: (...a: unknown[]) => theatreMocks.cancelTheatreCase(...a),
  reportTheatreSafetyEvent: (...a: unknown[]) => theatreMocks.reportTheatreSafetyEvent(...a),
  listTheatreSafetyEvents: (...a: unknown[]) => theatreMocks.listTheatreSafetyEvents(...a),
  routeTheatreDeath: (...a: unknown[]) => theatreMocks.routeTheatreDeath(...a),
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

describe("TheatreCaseScreen (honesty)", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;
  const onBack = vi.fn();
  const onOpenProcedure = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    theatreMocks.listTheatreSafetyEvents.mockResolvedValue([]);
    theatreMocks.getTheatreNote.mockResolvedValue({ status: "NONE" });
  });

  afterEach(() => {
    if (mounted) {
      const { root } = mounted;
      act(() => root.unmount());
      mounted = undefined;
    }
  });

  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/TheatreCaseScreen");
    expect(typeof mod.TheatreCaseScreen).toBe("function");
  });

  it("shows an error on case GET failure — never fake readiness or actions as loaded", async () => {
    theatreMocks.getTheatreCase.mockRejectedValue(new Error("404"));
    const mod = await import("../../screens/provider/TheatreCaseScreen");
    mounted = render(
      React.createElement(mod.TheatreCaseScreen, {
        caseId: "missing",
        onBack,
        onOpenProcedure,
      }),
    );
    await flush();

    expect(mounted.container.querySelector('[data-testid="theatre-case-error"]')).toBeTruthy();
    expect(mounted.container.textContent).toMatch(/unavailable|Could not load/i);
    expect(mounted.container.querySelector('[data-testid="theatre-case-screen"]')).toBeFalsy();
    expect(mounted.container.querySelector('[data-testid="theatre-evaluate-readiness"]')).toBeFalsy();
    expect(mounted.container.querySelector('[data-testid="theatre-open-procedure"]')).toBeFalsy();
  });

  it("wires case actions and links to the procedure wizard when the episode exists", async () => {
    theatreMocks.getTheatreCase.mockResolvedValue({
      id: "ep-9",
      procedure_name: "Cholecystectomy",
      status: "READY",
      triage_priority: "URGENT",
      patient_id: "CPID-9",
    });
    theatreMocks.evaluateTheatreReadiness.mockResolvedValue({
      bookable: false,
      blockers: [{ code: "NO_ROOM", message: "No theatre room assigned" }],
    });
    theatreMocks.bookTheatreCase.mockResolvedValue({ status: "BOOKED" });

    const mod = await import("../../screens/provider/TheatreCaseScreen");
    mounted = render(
      React.createElement(mod.TheatreCaseScreen, {
        caseId: "ep-9",
        onBack,
        onOpenProcedure,
      }),
    );
    await flush();

    expect(mounted.container.querySelector('[data-testid="theatre-case-screen"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="theatre-open-procedure"]')).toBeTruthy();
    expect(mounted.container.textContent).toContain("Cholecystectomy");

    const openProc = mounted.container.querySelector(
      '[data-testid="theatre-open-procedure"]',
    ) as HTMLElement;
    await act(async () => {
      openProc.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(onOpenProcedure).toHaveBeenCalled();

    const readinessBtn = mounted.container.querySelector(
      '[data-testid="theatre-evaluate-readiness"]',
    ) as HTMLElement;
    await act(async () => {
      readinessBtn.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();
    expect(theatreMocks.evaluateTheatreReadiness).toHaveBeenCalledWith("ep-9");
    expect(mounted.container.querySelector('[data-testid="theatre-readiness-result"]')).toBeTruthy();
    expect(mounted.container.textContent).toMatch(/Blocked|No theatre room/);

    const bookBtn = mounted.container.querySelector('[data-testid="theatre-book"]') as HTMLElement;
    await act(async () => {
      bookBtn.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();
    expect(theatreMocks.bookTheatreCase).toHaveBeenCalledWith("ep-9");
  });

  it("surfaces note load failure honestly (not as an empty draft)", async () => {
    theatreMocks.getTheatreCase.mockResolvedValue({
      id: "ep-3",
      procedure_name: "ORIF",
      status: "IN_PROGRESS",
    });
    theatreMocks.getTheatreNote.mockRejectedValue(new Error("note 503"));

    const mod = await import("../../screens/provider/TheatreCaseScreen");
    mounted = render(
      React.createElement(mod.TheatreCaseScreen, {
        caseId: "ep-3",
        onBack,
        onOpenProcedure,
      }),
    );
    await flush();

    expect(mounted.container.querySelector('[data-testid="theatre-note-unavailable"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="theatre-note-draft"]')).toBeFalsy();
  });
});
