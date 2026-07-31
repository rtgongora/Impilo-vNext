// @vitest-environment jsdom
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ActiveWorkContextBar } from "./ActiveWorkContextBar";
import type { ResolvedWorkContextView } from "@/lib/trust";

afterEach(() => cleanup());

const { pathnameState, contractState, switchMock } = vi.hoisted(() => ({
  pathnameState: { value: "/work" },
  contractState: { contract: undefined as unknown },
  switchMock: vi.fn(),
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
  useWorkSessionStore: (sel: (s: { session: null }) => unknown) => sel({ session: null }),
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

  it("shows the active context's label inside the work zone", () => {
    contractState.contract = { resolvedWorkContexts: [context({})], recommendedContextId: "ctx-1" };

    render(<ActiveWorkContextBar />);

    expect(screen.getByText("Parirenyatwa — Oncology Ward B")).toBeTruthy();
  });

  it("opens the switcher grouped by groupHint on click", () => {
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

  it("does not re-trigger a switch when selecting the already-active context", async () => {
    contractState.contract = { resolvedWorkContexts: [context({})], recommendedContextId: "ctx-1" };

    render(<ActiveWorkContextBar />);
    fireEvent.click(screen.getByTestId("active-work-context-trigger"));
    const matches = screen.getAllByText("Parirenyatwa — Oncology Ward B");
    fireEvent.click(matches[matches.length - 1]);

    expect(switchMock).not.toHaveBeenCalled();
  });
});
