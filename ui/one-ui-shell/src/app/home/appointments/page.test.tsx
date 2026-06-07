import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MyAppointmentsPage from "./page";

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

vi.mock("@/hooks/queries/useAppointments", () => ({
  useClientAppointments: () => ({
    data: [
      {
        id: "apt-1",
        facilityName: "Harare Central Hospital",
        appointmentType: "CONSULTATION",
        status: "CONFIRMED",
        startTime: "2026-06-12T09:00:00Z",
      },
      {
        id: "apt-2",
        facilityName: "Clinic B",
        appointmentType: "GENERAL",
        status: "REQUESTED",
        startTime: "2026-06-13T09:00:00Z",
      },
    ],
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  }),
  useCancelAppointment: () => ({
    mutate: cancelMutate,
    isPending: false,
  }),
}));

describe("MyAppointmentsPage", () => {
  beforeEach(() => {
    cancelMutate.mockClear();
  });

  it("lists only confirmed appointments and links to My Bookings", () => {
    render(<MyAppointmentsPage />);

    expect(screen.getByRole("heading", { name: "My Appointments" })).toBeInTheDocument();
    expect(screen.getByText("Harare Central Hospital")).toBeInTheDocument();
    expect(screen.getByText("CONFIRMED")).toBeInTheDocument();
    expect(screen.queryByText("REQUESTED")).not.toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "My Bookings" })[0]).toHaveAttribute(
      "href",
      "/home/bookings",
    );
    expect(screen.getByRole("link", { name: "Book a service" })).toHaveAttribute(
      "href",
      "/home/bookings/new",
    );
  });

  it("cancels a confirmed appointment through the appointments BFF", async () => {
    const user = userEvent.setup();

    render(<MyAppointmentsPage />);

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(cancelMutate).toHaveBeenCalledWith({ id: "apt-1" });
  });
});
