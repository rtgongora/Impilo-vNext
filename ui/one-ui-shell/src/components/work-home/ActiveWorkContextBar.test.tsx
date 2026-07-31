// @vitest-environment jsdom
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ActiveWorkContextBar } from "./ActiveWorkContextBar";
import type { ResolvedWorkContextView } from "@/lib/trust";

afterEach(() => cleanup());

const { pathnameState, contractState, switchMock, sessionState } = vi.hoisted(() => ({
  pathnameState: { value: "/work" },
  contractState: { contract: undefined as unknown },
  switchMock: vi.fn(),
  sessionState: {
    session: null as null | {
      contextId: string;
      workMode?: string;
    },
  },
}));

vi.mock("next/navigation", () => ({
  usePathname: () => pathnameState.value,
}));
vi.mock("@/hooks/useSessionExperienceContract", () => ({
  useSessionExperienceContract: () => contractState,
}));
vi.mock("@/hooks/queries/useSwitchWorkContext", () => ({
  useSwitchWorkContext: () => switchMock,
}));
vi.mock("@/hooks/useWorkSessionStore", () => ({
  useWorkSessionStore: (sel: (s: { session: typeof sessionState.session }) => unknown) =>
    sel({ session: sessionState.session }),
}));

function context(overrides: Partial<ResolvedWorkContextView>): ResolvedWorkContextView {
  return {
    contextId: "ctx-1",
    contextKind: "facility",
    sourceSystem: "VASHANDI",
    availableModes: ["CLINICAL_CARE"],
    defaultMode: "CLINICAL_CARE",
    restrictions: [],
    label: "Parirenyatwa — Oncology Ward B",
    groupHint: "today",
    ...overrides,
  };
}

describe("ActiveWorkContextBar", () => {
  beforeEach(() => {
    switchMock.mockReset();
    pathnameState.value = "/work";
    contractState.contract = undefined;
    sessionState.session = null;
  });

  it("renders nothing outside the work nav zone", () => {
    pathnameState.value = "/home";
    contractState.contract = { resolvedWorkContexts: [context({})], recommendedContextId: "ctx-1" };

    const { container } = render(<ActiveWorkContextBar />);

    expect(container.firstChild).toBeNull();
  });

  it("renders nothing when there are no resolved work contexts, even inside the work zone", () => {
    contractState.contract = { resolvedWorkContexts: [], recommendedContextId: null };

    const { container } = render(<ActiveWorkContextBar />);

    expect(container.firstChild).toBeNull();
  });

  it("shows Choose your workplace when only a recommendation exists (no minted session)", () => {
    contractState.contract = { resolvedWorkContexts: [context({})], recommendedContextId: "ctx-1" };

    render(<ActiveWorkContextBar />);

    expect(screen.getByText("Choose your workplace")).toBeTruthy();
  });

  it("shows the minted session context's label inside the work zone", () => {
    sessionState.session = { contextId: "ctx-1", workMode: "CLINICAL_CARE" };
    contractState.contract = { resolvedWorkContexts: [context({})], recommendedContextId: "ctx-1" };

    render(<ActiveWorkContextBar />);

    expect(screen.getByText(/Parirenyatwa — Oncology Ward B/)).toBeTruthy();
  });

  it("opens the switcher grouped by groupHint on click", () => {
    sessionState.session = { contextId: "a", workMode: "CLINICAL_CARE" };
    const contexts = [
      context({ contextId: "a", groupHint: "today", label: "Today's post" }),
      context({ contextId: "b", groupHint: "oversight", label: "District office" }),
    ];
    contractState.contract = { resolvedWorkContexts: contexts, recommendedContextId: "a" };

    render(<ActiveWorkContextBar />);
    fireEvent.click(screen.getByTestId("active-work-context-trigger"));

    expect(screen.getByText("Today")).toBeTruthy();
    expect(screen.getByText("Oversight roles")).toBeTruthy();
    expect(screen.getByText("District office")).toBeTruthy();
  });

  it("calls switchWorkContext with the chosen context and its default mode", async () => {
    switchMock.mockResolvedValue(undefined);
    sessionState.session = { contextId: "a", workMode: "CLINICAL_CARE" };
    const contexts = [
      context({ contextId: "a", label: "Current post" }),
      context({ contextId: "b", label: "Other post", defaultMode: "FACILITY_MANAGEMENT", availableModes: ["FACILITY_MANAGEMENT"], groupHint: "other" }),
    ];
    contractState.contract = { resolvedWorkContexts: contexts, recommendedContextId: "a" };

    render(<ActiveWorkContextBar />);
    fireEvent.click(screen.getByTestId("active-work-context-trigger"));
    fireEvent.click(screen.getByText("Other post"));

    await waitFor(() => expect(switchMock).toHaveBeenCalledWith(
      expect.objectContaining({ contextId: "b" }),
      "FACILITY_MANAGEMENT",
    ));
  });

  it("mints when selecting a recommended context without a session (no fake already-active short-circuit)", async () => {
    switchMock.mockResolvedValue(undefined);
    contractState.contract = { resolvedWorkContexts: [context({})], recommendedContextId: "ctx-1" };

    render(<ActiveWorkContextBar />);
    fireEvent.click(screen.getByTestId("active-work-context-trigger"));
    const matches = screen.getAllByText("Parirenyatwa — Oncology Ward B");
    fireEvent.click(matches[matches.length - 1]);

    await waitFor(() =>
      expect(switchMock).toHaveBeenCalledWith(
        expect.objectContaining({ contextId: "ctx-1" }),
        "CLINICAL_CARE",
      ),
    );
  });

  it("does not re-trigger a switch when selecting the already-minted active context", async () => {
    sessionState.session = { contextId: "ctx-1", workMode: "CLINICAL_CARE" };
    contractState.contract = { resolvedWorkContexts: [context({})], recommendedContextId: "ctx-1" };

    render(<ActiveWorkContextBar />);
    fireEvent.click(screen.getByTestId("active-work-context-trigger"));
    const matches = screen.getAllByText(/Parirenyatwa — Oncology Ward B/);
    fireEvent.click(matches[matches.length - 1]);

    expect(switchMock).not.toHaveBeenCalled();
  });
});
