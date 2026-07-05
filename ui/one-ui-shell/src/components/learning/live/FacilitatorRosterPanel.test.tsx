import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { FacilitatorRosterPanel } from "./FacilitatorRosterPanel";

const attendanceState: { items: Array<Record<string, unknown>> } = { items: [] };
const refetch = vi.fn();
const mutateAsync = vi.fn();

vi.mock("@/hooks/queries/useFundoDelivery", () => ({
  useSessionAttendance: () => ({
    data: { data: { items: attendanceState.items } },
    refetch,
    isFetching: false,
  }),
  useCreateCheckinToken: () => ({ mutateAsync, isPending: false }),
}));

describe("FacilitatorRosterPanel", () => {
  beforeEach(() => {
    attendanceState.items = [];
    mutateAsync.mockReset();
    refetch.mockReset();
  });

  it("renders the honest empty register", () => {
    render(<FacilitatorRosterPanel sessionId="sess-1" />);
    expect(screen.getByTestId("roster-empty")).toBeInTheDocument();
    expect(screen.getByText("Attendance (0)")).toBeInTheDocument();
  });

  it("renders checked-in learners with status and method", () => {
    attendanceState.items = [
      { id: "att-1", subjectId: "PROV-2", status: "PRESENT", checkInMethod: "CODE" },
      { id: "att-2", subjectId: "USER-9" },
    ];
    render(<FacilitatorRosterPanel sessionId="sess-1" />);
    const entries = screen.getAllByTestId("roster-entry");
    expect(entries).toHaveLength(2);
    expect(entries[0]).toHaveTextContent("PROV-2");
    expect(entries[0]).toHaveTextContent("PRESENT · CODE");
    expect(entries[1]).toHaveTextContent("USER-9");
    expect(screen.getByText("Attendance (2)")).toBeInTheDocument();
  });

  it("creates a check-in token and displays the code", async () => {
    mutateAsync.mockResolvedValue({ data: { token: { code: "482913" } } });
    render(<FacilitatorRosterPanel sessionId="sess-1" />);
    fireEvent.click(screen.getByTestId("create-checkin-token"));
    await waitFor(() => expect(screen.getByTestId("checkin-token-code")).toHaveTextContent("482913"));
    expect(mutateAsync).toHaveBeenCalledWith({});
  });

  it("surfaces an honest error when the token payload has no code", async () => {
    mutateAsync.mockResolvedValue({ data: {} });
    render(<FacilitatorRosterPanel sessionId="sess-1" />);
    fireEvent.click(screen.getByTestId("create-checkin-token"));
    await waitFor(() =>
      expect(screen.getByText(/no code was returned/i)).toBeInTheDocument()
    );
  });
});
