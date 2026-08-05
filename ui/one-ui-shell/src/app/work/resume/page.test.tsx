import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import WorkResumePage from "./page";
import type { SessionExperienceContract } from "@/lib/trust";

/**
 * `/work/resume` is where 148 facility-guarded routes now send someone without a duty session.
 * Three properties matter, and each replaces something the old `/facility` registry chooser
 * got wrong:
 *   1. one tap mints and returns them to the journey the guard interrupted;
 *   2. minting is still a deliberate act — never automatic on arrival;
 *   3. signing in is not a commitment to start a shift, so leaving is always offered.
 */

const switchWorkContext = vi.fn();
vi.mock("@/hooks/queries/useSwitchWorkContext", () => ({
  useSwitchWorkContext: () => switchWorkContext,
}));

const contractState: { contract: Partial<SessionExperienceContract> | null; isLoading: boolean } = {
  contract: null,
  isLoading: false,
};
vi.mock("@/hooks/useSessionExperienceContract", () => ({
  useSessionExperienceContract: () => contractState,
}));

const refresh = vi.fn();
let search = new URLSearchParams();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh, push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => search,
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

const CONTEXT = {
  contextId: "ctx-1",
  contextKind: "facility" as const,
  sourceSystem: "VASHANDI" as const,
  facilityId: "fac-1",
  availableModes: ["CLINICAL_CARE"],
  defaultMode: "CLINICAL_CARE",
  restrictions: [],
  label: "Parirenyatwa — OPD",
  groupHint: "today" as const,
};

beforeEach(() => {
  switchWorkContext.mockReset().mockResolvedValue(undefined);
  refresh.mockReset();
  search = new URLSearchParams();
  contractState.contract = null;
  contractState.isLoading = false;
});

describe("/work/resume", () => {
  it("resumes to the route the guard interrupted, not to a landing page", async () => {
    search = new URLSearchParams("returnTo=%2Fclinical%2Fqueue");
    contractState.contract = { resolvedWorkContexts: [CONTEXT], recommendedContextId: "ctx-1" };
    render(<WorkResumePage />);

    // Arrival alone must not mint — a duty token is a deliberate assertion of authority.
    expect(switchWorkContext).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId("resume-button"));

    await waitFor(() =>
      expect(switchWorkContext).toHaveBeenCalledWith(
        expect.objectContaining({ contextId: "ctx-1" }),
        "CLINICAL_CARE",
        "/clinical/queue",
      ),
    );
  });

  it("falls back to Work Home when there is no returnTo", async () => {
    contractState.contract = { resolvedWorkContexts: [CONTEXT], recommendedContextId: "ctx-1" };
    render(<WorkResumePage />);
    await userEvent.click(screen.getByTestId("resume-button"));
    await waitFor(() =>
      expect(switchWorkContext).toHaveBeenCalledWith(expect.anything(), "CLINICAL_CARE", "/work"),
    );
  });

  it("refuses an unsafe returnTo rather than resuming into it", async () => {
    search = new URLSearchParams("returnTo=%2F%2Fattacker.example");
    contractState.contract = { resolvedWorkContexts: [CONTEXT], recommendedContextId: "ctx-1" };
    render(<WorkResumePage />);
    await userEvent.click(screen.getByTestId("resume-button"));
    await waitFor(() =>
      expect(switchWorkContext).toHaveBeenCalledWith(expect.anything(), expect.anything(), "/work"),
    );
  });

  it("offers no resume button when the context grants several modes", () => {
    contractState.contract = {
      resolvedWorkContexts: [{ ...CONTEXT, availableModes: ["CLINICAL_CARE", "DEPARTMENT_MANAGEMENT"], defaultMode: null }],
      recommendedContextId: "ctx-1",
    };
    render(<WorkResumePage />);
    expect(screen.queryByTestId("resume-button")).toBeNull();
    expect(screen.getByText(/more than one kind of work/i)).toBeInTheDocument();
  });

  it("always offers a way out — signing in is not a commitment to start a shift", () => {
    contractState.contract = { resolvedWorkContexts: [CONTEXT], recommendedContextId: "ctx-1" };
    render(<WorkResumePage />);

    const otherWays = screen.getByTestId("resume-other-ways");
    expect(otherWays).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /My Impilo/i })).toHaveAttribute("href", "/home");
    expect(screen.getByRole("link", { name: /Impilo Fundo/i })).toHaveAttribute("href", "/learning");
  });

  it("says a source is unreachable instead of claiming the person has no workplaces", () => {
    contractState.contract = {
      resolvedWorkContexts: [],
      recommendedContextId: null,
      workContextSourceStatuses: [{ system: "VASHANDI", state: "DEGRADED" }],
    };
    render(<WorkResumePage />);

    expect(screen.getByTestId("resume-degraded")).toBeInTheDocument();
    expect(screen.getByText(/couldn't reach your assignment records/i)).toBeInTheDocument();
    // The manual registry chooser must remain reachable — it is the honest fallback, not dead.
    expect(screen.getByRole("link", { name: /Choose a facility manually/i })).toHaveAttribute("href", "/facility");
    expect(screen.queryByTestId("resume-empty")).toBeNull();
  });

  it("states plainly when there genuinely is no assignment", () => {
    contractState.contract = {
      resolvedWorkContexts: [],
      recommendedContextId: null,
      workContextSourceStatuses: [{ system: "VASHANDI", state: "EMPTY" }],
    };
    render(<WorkResumePage />);
    expect(screen.getByTestId("resume-empty")).toBeInTheDocument();
    expect(screen.queryByTestId("resume-degraded")).toBeNull();
  });

  it("shows loading rather than a verdict while the contract is in flight", () => {
    contractState.isLoading = true;
    render(<WorkResumePage />);
    expect(screen.getByText(/Finding your workplaces/i)).toBeInTheDocument();
    expect(screen.queryByTestId("resume-empty")).toBeNull();
    expect(screen.queryByTestId("resume-degraded")).toBeNull();
  });

  it("keeps the person on the page with a recoverable error when the mint fails", async () => {
    switchWorkContext.mockRejectedValue(new Error("mint failed"));
    contractState.contract = { resolvedWorkContexts: [CONTEXT], recommendedContextId: "ctx-1" };
    render(<WorkResumePage />);

    await userEvent.click(screen.getByTestId("resume-button"));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/Could not start your work session/i));
    expect(screen.getByTestId("resume-button")).toBeEnabled();
  });
});
