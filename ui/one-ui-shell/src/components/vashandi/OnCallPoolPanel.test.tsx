import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { OnCallPoolPanel } from "./OnCallPoolPanel";

const { listVirtualPools, getPoolOnDuty, getPoolShifts } = vi.hoisted(() => ({
  listVirtualPools: vi.fn(),
  getPoolOnDuty: vi.fn(),
  getPoolShifts: vi.fn(),
}));
const { listRosters, createRoster, createShift, updateShift } = vi.hoisted(() => ({
  listRosters: vi.fn(),
  createRoster: vi.fn(),
  createShift: vi.fn(),
  updateShift: vi.fn(),
}));

vi.mock("@/lib/vashandi/api/virtualPools", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  listVirtualPools,
  getPoolOnDuty,
  getPoolShifts,
}));
vi.mock("@/lib/vashandi/api/rosters", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  listRosters,
  createRoster,
  createShift,
  updateShift,
}));

function wrap(node: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

describe("OnCallPoolPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listVirtualPools.mockResolvedValue({
      success: true,
      data: [{ poolId: "trauma-oncall:fac-1", shiftCount: 1, onDutyCount: 0 }],
    });
    getPoolOnDuty.mockResolvedValue({ success: true, data: { poolId: "trauma-oncall:fac-1", onDutyCount: 0, onDuty: [] } });
    getPoolShifts.mockResolvedValue({ success: true, data: { poolId: "trauma-oncall:fac-1", shiftCount: 0, shifts: [] } });
    listRosters.mockResolvedValue({ success: true, data: { items: [] } });
  });

  it("lists existing pools and opens one on pick", async () => {
    wrap(<OnCallPoolPanel />);
    const pick = await screen.findByTestId("pool-pick-trauma-oncall:fac-1");
    await userEvent.click(pick);
    expect(await screen.findByTestId("pool-detail")).toHaveTextContent("trauma-oncall:fac-1");
  });

  it("defines a pool from a facility id using the trauma-oncall convention", async () => {
    wrap(<OnCallPoolPanel />);
    await userEvent.type(screen.getByTestId("define-pool-facility"), "fac-1");
    await userEvent.click(screen.getByTestId("define-pool-submit"));
    expect(await screen.findByTestId("pool-detail")).toHaveTextContent("trauma-oncall:fac-1");
    await waitFor(() => expect(getPoolShifts).toHaveBeenCalledWith("trauma-oncall:fac-1"));
  });

  it("adds a member: ensures an on-call roster then schedules a pool shift", async () => {
    createRoster.mockResolvedValue({ success: true, data: { id: "r-oncall" } });
    createShift.mockResolvedValue({ success: true, data: { id: "s-1" } });
    wrap(<OnCallPoolPanel />);

    await userEvent.type(screen.getByTestId("define-pool-facility"), "fac-1");
    await userEvent.click(screen.getByTestId("define-pool-submit"));
    await screen.findByTestId("add-member-form");

    await userEvent.type(screen.getByTestId("member-profile"), "wp-7");
    await userEvent.type(screen.getByTestId("member-start"), "2026-08-01T08:00");
    await userEvent.type(screen.getByTestId("member-end"), "2026-08-01T20:00");
    await userEvent.click(screen.getByTestId("member-add-submit"));

    await waitFor(() =>
      expect(createRoster).toHaveBeenCalledWith(
        expect.objectContaining({ rosterType: "trauma_oncall", facilityId: "fac-1" }),
      ),
    );
    await waitFor(() =>
      expect(createShift).toHaveBeenCalledWith(
        expect.objectContaining({
          rosterId: "r-oncall",
          workforceProfileId: "wp-7",
          virtualPoolId: "trauma-oncall:fac-1",
        }),
      ),
    );
  });

  it("confirming a scheduled member sets shift status to on-duty", async () => {
    getPoolShifts.mockResolvedValue({
      success: true,
      data: {
        poolId: "trauma-oncall:fac-1",
        shiftCount: 1,
        shifts: [{ id: "s-9", rosterId: "r-oncall", workforceProfileId: "wp-7", status: "scheduled" }],
      },
    });
    updateShift.mockResolvedValue({ success: true, data: { id: "s-9", status: "confirmed" } });
    wrap(<OnCallPoolPanel />);

    await userEvent.click(await screen.findByTestId("pool-pick-trauma-oncall:fac-1"));
    const row = await screen.findByTestId("pool-shift-s-9");
    await userEvent.click(within(row).getByTestId("confirm-s-9"));

    await waitFor(() => expect(updateShift).toHaveBeenCalledWith("s-9", { status: "confirmed" }));
  });
});
