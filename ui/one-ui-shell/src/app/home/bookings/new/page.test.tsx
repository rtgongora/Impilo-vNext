import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import BookServiceWizardPage from "./page";

const push = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
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

vi.mock("@/hooks/queries/useFacilities", () => ({
  useFacilities: () => ({
    data: {
      data: [
        {
          id: "facility-1",
          attributes: { name: "Harare Central Hospital" },
        },
      ],
    },
    isLoading: false,
  }),
}));

vi.mock("@/hooks/queries/useBookings", () => ({
  useBookingRoutingProviders: () => ({ data: [] }),
  useBookingRoutingWorkspaces: () => ({ data: [] }),
  useBookingRoutingResources: () => ({ data: [] }),
  useBookingAvailability: () => ({ data: [], isLoading: false }),
  useCreateBooking: () => ({
    mutateAsync: vi.fn().mockResolvedValue({ id: "bk-new" }),
    isPending: false,
    isError: false,
  }),
  useRequestBookingMvumo: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
}));

vi.mock("@/components/common/RecognisedProviderChip", () => ({
  RecognisedProviderChip: () => null,
}));

describe("BookServiceWizardPage", () => {
  it("renders target type picker like telemedicine routing", async () => {
    const user = userEvent.setup();
    render(<BookServiceWizardPage />);

    expect(screen.getByRole("heading", { name: "Book a service" })).toBeInTheDocument();
    expect(screen.getByTestId("target-type-PRACTITIONER")).toBeInTheDocument();
    expect(screen.getByTestId("target-type-WORKSPACE")).toBeInTheDocument();
    expect(screen.getByTestId("target-type-FACILITY_SERVICE")).toBeInTheDocument();

    await user.click(screen.getByTestId("target-type-PRACTITIONER"));
    expect(screen.getByPlaceholderText("Search clinics and hospitals…")).toBeInTheDocument();
  });
});
