/**
 * SurgeryEpisodesScreen honesty — failed list must never render as empty episodes.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ApiError } from "@impilo/mobile-api-client";

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

const surgeryMocks = vi.hoisted(() => ({
  listSurgicalEpisodes: vi.fn(),
  getSurgicalAssessment: vi.fn(),
  getSurgicalDecision: vi.fn(),
  listEpisodeSpecialties: vi.fn(),
  listPrehabItems: vi.fn(),
  listComplications: vi.fn(),
  listLongitudinalObjects: vi.fn(),
  getSurgicalFollowup: vi.fn(),
  listWaitlistRevalidations: vi.fn(),
}));

vi.mock("../../services/surgeryService", async () => {
  const actual = await vi.importActual<typeof import("../../services/surgeryService")>(
    "../../services/surgeryService",
  );
  return {
    ...actual,
    listSurgicalEpisodes: (...a: unknown[]) => surgeryMocks.listSurgicalEpisodes(...a),
    getSurgicalAssessment: (...a: unknown[]) => surgeryMocks.getSurgicalAssessment(...a),
    getSurgicalDecision: (...a: unknown[]) => surgeryMocks.getSurgicalDecision(...a),
    listEpisodeSpecialties: (...a: unknown[]) => surgeryMocks.listEpisodeSpecialties(...a),
    listPrehabItems: (...a: unknown[]) => surgeryMocks.listPrehabItems(...a),
    listComplications: (...a: unknown[]) => surgeryMocks.listComplications(...a),
    listLongitudinalObjects: (...a: unknown[]) => surgeryMocks.listLongitudinalObjects(...a),
    getSurgicalFollowup: (...a: unknown[]) => surgeryMocks.getSurgicalFollowup(...a),
    listWaitlistRevalidations: (...a: unknown[]) => surgeryMocks.listWaitlistRevalidations(...a),
    openSurgicalEpisode: vi.fn(),
    recordSurgicalAssessment: vi.fn(),
    recordSurgicalDecision: vi.fn(),
    reopenSurgicalEpisode: vi.fn(),
    addEpisodeSpecialty: vi.fn(),
    recordPrehabItem: vi.fn(),
    recogniseComplication: vi.fn(),
    placeLongitudinalObject: vi.fn(),
    recordSurgicalFollowup: vi.fn(),
    appendWaitlistRevalidation: vi.fn(),
  };
});

vi.mock("../../stores/encounterStore", () => ({
  useEncounterStore: () => ({
    activeEncounter: { id: "enc-1", patientId: "CPID-1" },
  }),
}));

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

describe("SurgeryEpisodesScreen (honesty)", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    surgeryMocks.listEpisodeSpecialties.mockResolvedValue([]);
    surgeryMocks.listPrehabItems.mockResolvedValue([]);
    surgeryMocks.listComplications.mockResolvedValue([]);
    surgeryMocks.listLongitudinalObjects.mockResolvedValue([]);
    surgeryMocks.listWaitlistRevalidations.mockResolvedValue([]);
    surgeryMocks.getSurgicalAssessment.mockRejectedValue(
      new ApiError({ code: "HTTP_404", message: "not found", status: 404, correlationId: "c" }),
    );
    surgeryMocks.getSurgicalDecision.mockRejectedValue(
      new ApiError({ code: "HTTP_404", message: "not found", status: 404, correlationId: "c" }),
    );
    surgeryMocks.getSurgicalFollowup.mockRejectedValue(
      new ApiError({ code: "HTTP_404", message: "not found", status: 404, correlationId: "c" }),
    );
  });

  afterEach(() => {
    if (mounted) {
      const { root } = mounted;
      act(() => root.unmount());
      mounted = undefined;
    }
  });

  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/SurgeryEpisodesScreen");
    expect(typeof mod.SurgeryEpisodesScreen).toBe("function");
  });

  it("shows an error on list failure — never an empty episode list", async () => {
    surgeryMocks.listSurgicalEpisodes.mockRejectedValue(new Error("BFF down"));
    const mod = await import("../../screens/provider/SurgeryEpisodesScreen");
    mounted = render(React.createElement(mod.SurgeryEpisodesScreen));
    await flush();

    expect(mounted.container.querySelector('[data-testid="surgery-episodes-unavailable"]')).toBeTruthy();
    expect(mounted.container.textContent).toMatch(/unavailable|Could not read/i);
    expect(mounted.container.querySelector('[data-testid="surgery-episodes-empty"]')).toBeFalsy();
  });

  it("renders episode rows when the list succeeds", async () => {
    surgeryMocks.listSurgicalEpisodes.mockResolvedValue([
      {
        id: "ep-1",
        subjectCpid: "CPID-1",
        journeyId: "j-1",
        encounterId: null,
        procedureEpisodeRef: null,
        pctProblemRef: null,
        operativeIndication: "Appendicitis",
        nonOperativeOptionsConsidered: null,
        status: "ASSESSMENT",
        specialty: "GENERAL_SURGERY",
        pctContributed: false,
      },
    ]);
    const mod = await import("../../screens/provider/SurgeryEpisodesScreen");
    mounted = render(React.createElement(mod.SurgeryEpisodesScreen));
    await flush();

    expect(mounted.container.querySelector('[data-testid="surgery-episodes-list"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="surgery-episode-item"]')).toBeTruthy();
    expect(mounted.container.textContent).toContain("Appendicitis");
    expect(mounted.container.querySelector('[data-testid="surgery-episodes-unavailable"]')).toBeFalsy();
  });

  it("opens episode detail and surfaces specialties failure honestly", async () => {
    surgeryMocks.listSurgicalEpisodes.mockResolvedValue([
      {
        id: "ep-2",
        subjectCpid: "CPID-1",
        journeyId: null,
        encounterId: null,
        procedureEpisodeRef: null,
        pctProblemRef: null,
        operativeIndication: "Hernia",
        nonOperativeOptionsConsidered: null,
        status: "OPERATED",
        specialty: "GENERAL_SURGERY",
        pctContributed: false,
      },
    ]);
    surgeryMocks.listEpisodeSpecialties.mockRejectedValue(new Error("down"));
    surgeryMocks.listPrehabItems.mockRejectedValue(new Error("down"));

    const mod = await import("../../screens/provider/SurgeryEpisodesScreen");
    mounted = render(React.createElement(mod.SurgeryEpisodesScreen));
    await flush();

    await act(async () => {
      mounted!.container
        .querySelector('[data-testid="surgery-episode-item"]')!
        .dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(mounted.container.querySelector('[data-testid="surgery-episode-detail"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="surgery-specialties-unavailable"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="surgery-prehab-unavailable"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="surgery-specialties-empty"]')).toBeFalsy();
    expect(mounted.container.querySelector('[data-testid="surgery-assessment-not-recorded"]')).toBeTruthy();
  });
});
