import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import CitizenVirtualCarePage from "./page";

const { requestsState } = vi.hoisted(() => ({
  requestsState: {
    data: undefined as unknown,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  },
}));

vi.mock("@/hooks/queries/useVirtualCare", () => ({
  useMyVirtualCareRequests: () => requestsState,
}));

describe("CitizenVirtualCarePage", () => {
  it("renders the caller's requests with honest pool-status copy", () => {
    requestsState.isLoading = false;
    requestsState.isError = false;
    requestsState.data = {
      data: {
        requests: [
          {
            id: "ref-1",
            status: "SUBMITTED",
            reason: "Cough for two weeks",
            modality: "message",
            virtualHospital: { id: "vh-national-telemedicine", name: "National Telemedicine Hospital" },
          },
          {
            id: "ref-2",
            status: "ACCEPTED",
            reason: "Skin rash",
            modality: "video",
            virtualHospital: { id: "vh-specialist", name: "Virtual Specialist Hospital" },
          },
        ],
      },
    };

    render(<CitizenVirtualCarePage />);

    expect(screen.getByText("National Telemedicine Hospital")).toBeInTheDocument();
    expect(
      screen.getByText("Waiting in the virtual hospital's pool — a provider will accept it."),
    ).toBeInTheDocument();
    expect(screen.getByText("A provider has accepted your request.")).toBeInTheDocument();
  });

  it("shows an empty state pointing at discovery when there are no requests", () => {
    requestsState.isLoading = false;
    requestsState.isError = false;
    requestsState.data = { data: { requests: [] } };

    render(<CitizenVirtualCarePage />);

    expect(screen.getByText(/no virtual care requests yet/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Find a virtual hospital" })).toHaveAttribute(
      "href",
      "/discover/virtual-care",
    );
  });
});
