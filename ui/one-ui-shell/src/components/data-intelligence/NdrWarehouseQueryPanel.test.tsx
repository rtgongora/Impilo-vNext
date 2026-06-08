import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { NdrWarehouseQueryPanel } from "./NdrWarehouseQueryPanel";

const buildMutate = vi.fn();

vi.mock("@/hooks/queries/useNdr", () => ({
  useNdrBronzeQuery: () => ({
    data: { items: [{ event_id: "evt-1", event_type: "impilo.test.v1" }], hasMore: false, cursor: null },
    isLoading: false,
    isError: false,
  }),
  useNdrGoldEncountersQuery: () => ({
    data: { items: [], hasMore: false, cursor: null },
    isLoading: false,
    isError: false,
  }),
  useNdrBuildGoldEncounters: () => ({ mutate: buildMutate, isPending: false, isSuccess: false }),
  bronzeEventLabel: (row: { event_type?: string }) => row.event_type ?? "event",
  goldEncounterLabel: () => "encounter",
}));

describe("NdrWarehouseQueryPanel", () => {
  it("renders bronze and gold query panels with honest empty gold state", () => {
    render(<NdrWarehouseQueryPanel />);
    expect(screen.getByTestId("ndr-warehouse-query-panel")).toBeInTheDocument();
    expect(screen.getByTestId("ndr-bronze-status")).toHaveTextContent(/1 bronze event/i);
    expect(screen.getByTestId("ndr-gold-status")).toHaveTextContent(/reachable but no encounters/i);
  });

  it("triggers gold build mutation", async () => {
    const user = userEvent.setup();
    render(<NdrWarehouseQueryPanel />);
    await user.click(screen.getByRole("button", { name: /Build gold encounters/i }));
    expect(buildMutate).toHaveBeenCalled();
  });
});
