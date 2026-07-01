import { type ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

import CitizenMyCarePage from "./page";

function renderPage(node: ReactNode = <CitizenMyCarePage />) {
  const client = new QueryClient({
    // Swallow query errors at the cache boundary so a rejected queryFn is
    // definitively handled (avoids jsdom unhandled-rejection noise); the UI
    // still observes isError and renders the retry affordance.
    queryCache: new QueryCache({ onError: () => {} }),
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

describe("CitizenMyCarePage", () => {
  beforeEach(() => get.mockReset());

  it("renders real at-a-glance counts from the citizen health summary", async () => {
    get.mockResolvedValue({
      data: {
        conditions: [{ name: "Hypertension" }, { display: "Type 2 diabetes" }],
        allergies: [{ name: "Penicillin" }],
        results: [{ name: "FBC" }],
      },
    });

    renderPage();

    await waitFor(() => expect(screen.getByText("Your health at a glance")).toBeInTheDocument());
    expect(get).toHaveBeenCalledWith("/internal/v1/citizen/health-summary");
    expect(screen.getByText("Penicillin")).toBeInTheDocument();
    expect(screen.getByText("Hypertension")).toBeInTheDocument();
    // Handoffs into real, registered citizen routes
    expect(screen.getByRole("link", { name: /Care timeline/i })).toHaveAttribute("href", "/citizen/wallet/timeline");
  });

  it("shows an honest empty state when the summary has nothing yet", async () => {
    get.mockResolvedValue({ data: { conditions: [], allergies: [], results: [] } });

    renderPage();

    await waitFor(() => expect(screen.getByText(/Nothing to show yet/i)).toBeInTheDocument());
  });

  it("always offers real handoffs into the citizen care journey", async () => {
    get.mockResolvedValue({ data: { conditions: [], allergies: [], results: [] } });

    renderPage();

    await waitFor(() => expect(screen.getByText("Continue your care")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: /Health records/i })).toHaveAttribute("href", "/citizen/wallet/records");
    expect(screen.getByRole("link", { name: /Payments & bills/i })).toHaveAttribute("href", "/citizen/wallet/payments");
    expect(screen.getByRole("link", { name: /Share my record/i })).toHaveAttribute("href", "/citizen/record-sharing");
  });
});
