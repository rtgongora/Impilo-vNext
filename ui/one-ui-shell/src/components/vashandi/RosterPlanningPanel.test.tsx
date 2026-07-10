import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RosterCreateForm, RosterPlanningCard } from "./RosterPlanningPanel";

const { createRoster, approveRoster, createShift, listRosterShifts } = vi.hoisted(() => ({
  createRoster: vi.fn(),
  approveRoster: vi.fn(),
  createShift: vi.fn(),
  listRosterShifts: vi.fn(),
}));

vi.mock("@/lib/vashandi/api/rosters", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  createRoster,
  approveRoster,
  createShift,
  listRosterShifts,
}));

function wrap(node: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

describe("Roster planning", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listRosterShifts.mockResolvedValue({ success: true, data: { items: [] } });
  });

  it("creates a draft roster with the period", async () => {
    createRoster.mockResolvedValue({ success: true, data: { id: "r-1", status: "draft" } });
    wrap(<RosterCreateForm />);

    await userEvent.type(screen.getByTestId("roster-start"), "2026-08-01");
    await userEvent.type(screen.getByTestId("roster-end"), "2026-08-31");
    await userEvent.click(screen.getByTestId("roster-create-submit"));

    await waitFor(() =>
      expect(createRoster).toHaveBeenCalledWith(
        expect.objectContaining({ periodStart: "2026-08-01", periodEnd: "2026-08-31" }),
      ),
    );
  });

  it("requires both dates before creating", async () => {
    wrap(<RosterCreateForm />);
    await userEvent.click(screen.getByTestId("roster-create-submit"));
    expect(await screen.findByText(/Both period dates are required/)).toBeInTheDocument();
    expect(createRoster).not.toHaveBeenCalled();
  });

  it("draft rosters offer approval; approved ones do not", async () => {
    approveRoster.mockResolvedValue({ success: true });
    wrap(<RosterPlanningCard roster={{ id: "r-1", status: "draft", rosterType: "facility_monthly" }} />);
    await userEvent.click(screen.getByTestId("roster-approve-r-1"));
    await waitFor(() => expect(approveRoster).toHaveBeenCalledWith("r-1"));

    wrap(<RosterPlanningCard roster={{ id: "r-2", status: "approved" }} />);
    expect(screen.queryByTestId("roster-approve-r-2")).not.toBeInTheDocument();
  });

  it("expanding a roster loads its shifts and Add shift posts one", async () => {
    listRosterShifts.mockResolvedValue({
      success: true,
      data: { items: [{ id: "s-1", rosterId: "r-1", shiftType: "night", status: "scheduled" }] },
    });
    createShift.mockResolvedValue({ success: true, data: { id: "s-2" } });
    wrap(<RosterPlanningCard roster={{ id: "r-1", status: "draft" }} />);

    await userEvent.click(screen.getByTestId("roster-toggle-r-1"));
    expect(await screen.findByTestId("roster-shifts-r-1")).toHaveTextContent(/night/);

    await userEvent.click(screen.getByTestId("roster-add-shift-r-1"));
    await userEvent.type(screen.getByTestId("shift-profile"), "wp-9");
    await userEvent.type(screen.getByTestId("shift-start"), "2026-08-01T08:00");
    await userEvent.type(screen.getByTestId("shift-end"), "2026-08-01T17:00");
    await userEvent.click(screen.getByTestId("shift-add-submit"));

    await waitFor(() =>
      expect(createShift).toHaveBeenCalledWith(
        expect.objectContaining({ rosterId: "r-1", workforceProfileId: "wp-9", shiftType: "day" }),
      ),
    );
  });
});
