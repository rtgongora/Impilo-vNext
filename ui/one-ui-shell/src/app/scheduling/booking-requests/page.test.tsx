import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import BookingRequestsPage from "./page";

const { approveMutate, convertMutate } = vi.hoisted(() => ({
  approveMutate: vi.fn(),
  convertMutate: vi.fn(),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/components/experience/FacilityWorkClusterRibbon", () => ({
  FacilityWorkClusterRibbon: () => null,
}));

vi.mock("@/components/experience/OrganizationPlaneContextBar", () => ({
  OrganizationPlaneContextBar: () => null,
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/queries/useBookings", () => ({
  useFacilityBookings: () => ({
    data: [
      {
        id: "bk-inbox-1",
        bookingType: "CONSULTATION",
        bookingStatus: "PENDING_APPROVAL",
        preferredStartTime: "2026-06-12T10:00:00Z",
        reasonForBooking: "Chest pain review",
      },
    ],
    isLoading: false,
  }),
  useApproveBooking: () => ({ mutate: approveMutate, isPending: false }),
  useRejectBooking: () => ({ mutate: vi.fn(), isPending: false }),
  useTriageBooking: () => ({ mutate: vi.fn(), isPending: false }),
  useRequestBookingMvumo: () => ({ mutate: vi.fn(), isPending: false }),
  useConvertBooking: () => ({ mutate: convertMutate, isPending: false }),
}));

describe("BookingRequestsPage", () => {
  beforeEach(() => {
    approveMutate.mockClear();
    convertMutate.mockClear();
  });

  it("shows provider inbox with triage actions", () => {
    render(<BookingRequestsPage />);

    expect(screen.getByRole("heading", { name: "Booking requests" })).toBeInTheDocument();
    expect(screen.getByTestId("booking-request-bk-inbox-1")).toBeInTheDocument();
    expect(screen.getByText("PENDING APPROVAL")).toBeInTheDocument();
  });

  it("approves and converts bookings from the inbox", async () => {
    const user = userEvent.setup();
    render(<BookingRequestsPage />);

    await user.click(screen.getByRole("button", { name: "Approve" }));
    expect(approveMutate).toHaveBeenCalledWith("bk-inbox-1");

    await user.click(screen.getByRole("button", { name: "Convert" }));
    expect(convertMutate).toHaveBeenCalledWith("bk-inbox-1");
  });
});
