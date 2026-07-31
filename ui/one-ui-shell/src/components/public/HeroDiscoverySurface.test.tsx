import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/lib/api-client";
import { HeroDiscoverySurface } from "./HeroDiscoverySurface";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

// The map is a dynamic MapLibre import; it has its own coverage and would only add
// WebGL noise here. These tests are about what the surface claims, not how it draws.
vi.mock("./find-care/FindCareMap", () => ({
  FindCareMap: () => <div data-testid="find-care-map" />,
}));

vi.mock("./ResumeJourneyCard", () => ({
  ResumeJourneyCard: () => null,
}));

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

function response(over: Partial<Record<string, unknown>> = {}) {
  return {
    query: null,
    category: "all",
    results: [],
    categoryCounts: {},
    notes: [],
    ...over,
  };
}

const facility = {
  category: "care",
  type: "Clinic",
  id: "17",
  title: "Seke North Clinic",
  subtitle: "Harare · Clinic",
  meta: ["In directory"],
  distanceMeters: 2400,
  etaMinutes: null,
  latitude: -17.8,
  longitude: 31.0,
  availability: null,
  href: "/welcome/find-care/17",
  actionLabel: "View services",
};

describe("HeroDiscoverySurface", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("opens on the map view, as the brief requires", async () => {
    get.mockResolvedValue(response());
    render(<HeroDiscoverySurface />);

    await waitFor(() => expect(get).toHaveBeenCalled());
    expect(screen.getByRole("button", { name: /^Map$/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /^List$/ })).toHaveAttribute("aria-pressed", "false");
  });

  it("shows every note the orchestrator returned, not just the first", async () => {
    get.mockResolvedValue(
      response({
        notes: [
          "Search a service or share your location to see facilities and care near you.",
          "Medicines are temporarily unavailable.",
        ],
      }),
    );
    render(<HeroDiscoverySurface />);

    // Both lanes failed independently; reporting one would describe a tidier outage
    // than the one that actually happened.
    expect(
      await screen.findByText(/share your location to see facilities/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/medicines are temporarily unavailable/i)).toBeInTheDocument();
  });

  it("counts the blend but never labels a filtered lane's siblings as empty", async () => {
    get.mockResolvedValue(
      response({ results: [facility], categoryCounts: { care: 1, marketplace: 4 } }),
    );
    const { rerender } = render(<HeroDiscoverySurface />);

    // Unfiltered: the counts describe the whole blend, so they are meaningful.
    // waitFor covers the render that follows the resolved fetch, not just the call itself.
    await waitFor(() =>
      expect(screen.getByRole("tab", { name: /^Medicines/ })).toHaveTextContent("4"),
    );

    // Filtered: the response holds one lane only, so a "0" on every other chip would
    // claim those lanes are empty when they were simply never asked.
    get.mockResolvedValue(
      response({ category: "care", results: [facility], categoryCounts: { care: 1 } }),
    );
    rerender(<HeroDiscoverySurface />);
    fireEvent.click(screen.getByRole("tab", { name: /^Care/ }));

    // The accessible name carries the badge, so an exact "Medicines" match IS the
    // assertion that no count is rendered for a lane the response never covered.
    await waitFor(() => {
      expect(screen.getByRole("tab", { name: "Medicines" })).toBeInTheDocument();
    });
    expect(screen.queryByRole("tab", { name: /^Medicines\s+\d/ })).not.toBeInTheDocument();
  });

  it("renders no availability or travel time when the source gave none", async () => {
    get.mockResolvedValue(response({ results: [facility], categoryCounts: { care: 1 } }));
    render(<HeroDiscoverySurface />);

    // Switch to the list, where the full result card renders.
    fireEvent.click(await screen.findByRole("button", { name: /^List$/ }));

    expect(await screen.findByText("Seke North Clinic")).toBeInTheDocument();
    expect(screen.getByText("2.4 km")).toBeInTheDocument();
    expect(screen.queryByText(/min away/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/open now/i)).not.toBeInTheDocument();
  });

  it("keeps the provider directory honest instead of faking a listing", async () => {
    get.mockResolvedValue(response());
    render(<HeroDiscoverySurface />);

    fireEvent.click(await screen.findByRole("tab", { name: /^Providers$/ }));

    expect(await screen.findByText(/public provider directory isn.t open/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /verify a registered provider/i })).toHaveAttribute(
      "href",
      "/verify/practitioner",
    );
  });
});
