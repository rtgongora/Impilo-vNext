import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AssignmentLifecycleActions } from "./AssignmentLifecycleActions";
import type { WorkforceAssignment } from "@/lib/vashandi/types";

const { precheckAssignment, approveAssignment, activateAssignment, suspendAssignment, endAssignment } =
  vi.hoisted(() => ({
    precheckAssignment: vi.fn(),
    approveAssignment: vi.fn(),
    activateAssignment: vi.fn(),
    suspendAssignment: vi.fn(),
    endAssignment: vi.fn(),
  }));
const { runVashandiPrecheck } = vi.hoisted(() => ({ runVashandiPrecheck: vi.fn() }));

vi.mock("@/lib/vashandi/api/assignments", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  precheckAssignment,
  approveAssignment,
  activateAssignment,
  suspendAssignment,
  endAssignment,
}));
vi.mock("@/lib/vashandi/api/profiles", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  runVashandiPrecheck,
}));

function renderWith(assignment: WorkforceAssignment) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AssignmentLifecycleActions assignment={assignment} />
    </QueryClientProvider>,
  );
}

const base: WorkforceAssignment = { id: "a-1", workforceProfileId: "p-1", status: "requested" };

describe("AssignmentLifecycleActions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    runVashandiPrecheck.mockResolvedValue({
      success: true,
      policyDecision: { decisionId: "opa-123" },
    });
  });

  it("shows precheck+approve for requested, and precheck carries the OPA decision id", async () => {
    precheckAssignment.mockResolvedValue({
      success: true,
      data: {
        actionStatus: "allowed",
        eligibility: {
          overallStatus: "allowed",
          checks: [{ dependency: "varapi", status: "live" }],
        },
      },
    });
    renderWith(base);

    expect(screen.getByTestId("assignment-precheck")).toBeInTheDocument();
    expect(screen.getByTestId("assignment-approve")).toBeInTheDocument();
    expect(screen.queryByTestId("assignment-suspend")).not.toBeInTheDocument();

    await userEvent.click(screen.getByTestId("assignment-precheck"));

    await waitFor(() => {
      expect(precheckAssignment).toHaveBeenCalledWith("a-1", { opaDecisionId: "opa-123" });
    });
    expect(await screen.findByTestId("eligibility-verdict")).toHaveTextContent(/Eligible — all dependency checks passed/);
    expect(screen.getByTestId("eligibility-verdict")).toHaveTextContent(/varapi/);
  });

  it("surfaces a conditional-training verdict honestly on activate", async () => {
    activateAssignment.mockResolvedValue({
      success: true,
      data: {
        actionStatus: "conditional_training",
        eligibility: { overallStatus: "conditional_training", message: "INFECT-CTL not completed" },
      },
    });
    renderWith({ ...base, status: "approved" });

    await userEvent.click(screen.getByTestId("assignment-activate"));

    expect(await screen.findByTestId("eligibility-verdict")).toHaveTextContent(/Conditional — a SOFT training requirement is unmet/);
    expect(screen.getByTestId("eligibility-verdict")).toHaveTextContent(/INFECT-CTL/);
  });

  it("active assignments offer suspend and end, which call the plain actions", async () => {
    suspendAssignment.mockResolvedValue({ success: true, data: { actionStatus: "completed" } });
    renderWith({ ...base, status: "active" });

    expect(screen.getByTestId("assignment-suspend")).toBeInTheDocument();
    expect(screen.getByTestId("assignment-end")).toBeInTheDocument();
    expect(screen.queryByTestId("assignment-precheck")).not.toBeInTheDocument();

    await userEvent.click(screen.getByTestId("assignment-suspend"));
    await waitFor(() => expect(suspendAssignment).toHaveBeenCalledWith("a-1", undefined));
  });

  it("shows the friendly message when the BFF denies the action", async () => {
    approveAssignment.mockResolvedValue({
      success: false,
      friendlyMessage: "This Vashandi action is blocked by policy.",
    });
    renderWith(base);

    await userEvent.click(screen.getByTestId("assignment-approve"));

    expect(await screen.findByTestId("assignment-action-error")).toHaveTextContent(/blocked by policy/);
  });
});
