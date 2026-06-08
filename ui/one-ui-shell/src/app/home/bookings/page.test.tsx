import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MyBookingsPage from "./page";

const { cancelMutate } = vi.hoisted(() => ({
  cancelMutate: vi.fn(),
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

vi.mock("@/hooks/queries/useBookings", () => ({
  useClientBookings: () => ({
    data: [
      {
        id: "bk-1",
        bookingType: "CONSULTATION",
        bookingStatus: "PENDING_APPROVAL",
        facilityName: "Harare Central Hospital",
        preferredStartTime: "2026-06-12T09:00:00Z",
        reasonForBooking: "Follow-up",
      },
    ],
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  }),
  useCancelBooking: () => ({
    mutate: cancelMutate,
    isPending: false,
  }),
}));

describe("MyBookingsPage", () => {
  beforeEach(() => {
    cancelMutate.mockClear();
  });

  it("lists active booking transactions with status badges", () => {
    render(<MyBookingsPage />);

    expect(screen.getByRole("heading", { name: "My Bookings" })).toBeInTheDocument();
    expect(screen.getByText("Harare Central Hospital")).toBeInTheDocument();
    expect(screen.getByText("PENDING APPROVAL")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Book a service" })).toHaveAttribute(
      "href",
      "/home/bookings/new",
    );
  });

  it("cancels a booking through the bookings BFF", async () => {
    const user = userEvent.setup();
    render(<MyBookingsPage />);

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(cancelMutate).toHaveBeenCalledWith({ id: "bk-1" });
  });
});
