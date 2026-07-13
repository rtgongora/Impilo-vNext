import type { ReactNode } from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import SortingDeskPage from "./page";

const { sortMutate } = vi.hoisted(() => ({ sortMutate: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div>,
}));
vi.mock("@/hooks/queries/useSortingDesk", () => ({
  useVisitTypes: () => ({
    data: {
      OUTPATIENT: [{ code: "OPD_NEW", label: "New outpatient consultation", fastTrackDefault: false }],
      CASUALTY: [{ code: "ED_TRAUMA", label: "Emergency — trauma", fastTrackDefault: true }],
    },
    isLoading: false,
  }),
  useLatestSort: () => ({ data: null, isLoading: false }),
  useSortJourney: () => ({ mutate: sortMutate, isPending: false, isSuccess: false, isError: false }),
}));

describe("SortingDeskPage", () => {
  beforeEach(() => sortMutate.mockReset());

  it("sorts a journey with the chosen context + visit type", async () => {
    render(<SortingDeskPage />);
    fireEvent.change(screen.getByPlaceholderText(/Journey/), { target: { value: "J-1" } });
    fireEvent.click(screen.getByRole("button", { name: /^Load$/ }));

    // Context + visit-type selects appear once a journey is loaded.
    const selects = await screen.findAllByRole("combobox");
    fireEvent.change(selects[0], { target: { value: "CASUALTY" } });
    const selectsAfter = screen.getAllByRole("combobox");
    fireEvent.change(selectsAfter[1], { target: { value: "ED_TRAUMA" } });

    fireEvent.click(screen.getByRole("button", { name: /Sort journey/ }));
    await waitFor(() => expect(sortMutate).toHaveBeenCalledTimes(1));
    expect(sortMutate.mock.calls[0][0]).toMatchObject({ journeyId: "J-1", context: "CASUALTY", visitType: "ED_TRAUMA" });
  });
});
