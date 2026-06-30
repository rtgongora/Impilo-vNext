import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import PersonHealthWalletPage from "./page";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn() },
}));

// The identity/trust banner is a separately-tested component with its own assurance query; stub it
// so this test focuses on the wallet overview composition rather than the banner's internals.
vi.mock("@/components/citizen/IdentityAssuranceBanner", () => ({
  IdentityAssuranceBanner: () => null,
}));

// The "acting for" banner reads sessionStorage via the delegation store; default is null.
const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <PersonHealthWalletPage />
    </QueryClientProvider>,
  );
}

const OVERVIEW = {
  data: {
    identity: { healthId: "CPID-1", assurance: { assuranceState: "FULLY_VERIFIED" } },
    profileCompletion: { present: 6, total: 6, percent: 100, missing: [] },
    records: { _source: "BUTANO · PCT", summary: { conditions: [] } },
    careTimeline: { _source: "PCT", eventCount: 3, timeline: [] },
    consent: { _source: "Tshepo consent", activeCount: 2 },
    dependants: { _source: "Mvumo · VITO", iCanActForCount: 1 },
    payments: { _source: "Costa · MusheX", outstandingCount: 0 },
    commsPreferences: { _source: "notification-service", preferences: {} },
    nextActions: [],
    viewingAs: "SELF",
  },
  meta: {},
};

describe("PersonHealthWalletPage", () => {
  it("renders real wallet cards with live counts and source labels", async () => {
    get.mockResolvedValue(OVERVIEW);
    renderPage();

    expect(await screen.findByText("Mushe Person Health Wallet")).toBeInTheDocument();
    // Cards link to real surfaces and show real counts from the BFF overview.
    expect(await screen.findByText("My health records")).toBeInTheDocument();
    expect(screen.getByText("Care timeline")).toBeInTheDocument();
    expect(screen.getByText(/3 events/)).toBeInTheDocument();
    expect(screen.getByText(/2 active consents/)).toBeInTheDocument();
    // Profile completion meter reflects the real percent.
    expect(screen.getByText("100%")).toBeInTheDocument();
  });

  it("shows a truthful unavailable state for a card whose owner is down", async () => {
    get.mockResolvedValue({
      data: { ...OVERVIEW.data, records: { _source: "BUTANO · PCT", unavailable: true } },
      meta: {},
    });
    renderPage();

    await screen.findByText("My health records");
    await waitFor(() =>
      expect(screen.getByText(/temporarily unavailable/i)).toBeInTheDocument(),
    );
  });

  it("renders an error state with retry when the overview fails to load", async () => {
    get.mockRejectedValue(new Error("boom"));
    renderPage();

    expect(await screen.findByText(/couldn.t load your wallet/i)).toBeInTheDocument();
    expect(screen.getByText(/try again/i)).toBeInTheDocument();
  });
});
