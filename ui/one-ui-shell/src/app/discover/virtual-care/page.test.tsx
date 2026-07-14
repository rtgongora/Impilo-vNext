import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import DiscoverVirtualCarePage from "./page";

const { directoryState } = vi.hoisted(() => ({
  directoryState: {
    data: undefined as unknown,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  },
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/hooks/queries/useVirtualCare", () => ({
  useVirtualHospitals: () => directoryState,
}));

describe("DiscoverVirtualCarePage", () => {
  it("shows a request CTA only for REQUESTABLE hospitals; COMING_SOON gets an honest badge with no CTA", () => {
    directoryState.isLoading = false;
    directoryState.isError = false;
    directoryState.data = {
      data: {
        virtualHospitals: [
          {
            id: "vh-national-telemedicine",
            name: "National Telemedicine Hospital",
            availability: "REQUESTABLE",
            poolId: "GENERAL_TELEMEDICINE",
            purpose: "Countrywide telemedicine coordination.",
          },
          {
            id: "vh-mental-health",
            name: "Virtual Mental Health Unit",
            availability: "COMING_SOON",
            purpose: "Counselling and psychiatric review.",
          },
        ],
      },
    };

    render(<DiscoverVirtualCarePage />);

    // Requestable: CTA deep-links into the request flow with the VH preselected.
    const cta = screen.getByRole("link", { name: "Request a teleconsult" });
    expect(cta).toHaveAttribute(
      "href",
      "/citizen/virtual-care/request?vh=vh-national-telemedicine",
    );

    // Coming soon: badge + section heading, honest copy, and exactly one CTA on the whole page.
    expect(screen.getAllByText("Coming soon").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByRole("link", { name: "Request a teleconsult" })).toHaveLength(1);
    expect(
      screen.getByText(/Not yet taking requests — this service opens once its care team is connected./),
    ).toBeInTheDocument();
  });

  it("shows an honest error state with retry when the directory fails to load", () => {
    directoryState.isLoading = false;
    directoryState.isError = true;
    directoryState.data = undefined;

    render(<DiscoverVirtualCarePage />);

    expect(screen.getByText(/couldn't load the virtual care directory/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Request a teleconsult" })).not.toBeInTheDocument();
  });
});
