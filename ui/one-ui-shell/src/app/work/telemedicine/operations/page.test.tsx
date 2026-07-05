import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TelemedicineOperationsPage from "./page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

function renderWithQuery(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe("/work/telemedicine/operations", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("renders live RTC transport health from the gateway", async () => {
    get.mockImplementation(async (path: string) => {
      if (path.includes("rtc") || path.includes("health")) {
        return {
          data: {
            provider: "livekit",
            livekitEnabled: true,
            livekitConfigured: true,
            productionReady: false,
            activeSessions: 3,
          },
        };
      }
      return { data: null };
    });

    renderWithQuery(<TelemedicineOperationsPage />);

    expect(screen.getByText("RTC transport health")).toBeInTheDocument();
    expect(await screen.findByText("livekit")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("shows honest empty states when no facility context is selected", async () => {
    get.mockResolvedValue({ data: null });
    renderWithQuery(<TelemedicineOperationsPage />);

    expect(
      screen.getByText(/select a facility context to load its backlog/i),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(/no submitted referrals awaiting specialty response/i),
    ).toBeInTheDocument();
  });

  it("declares the per-session failure diagnostics gap instead of faking a table", () => {
    get.mockResolvedValue({ data: null });
    renderWithQuery(<TelemedicineOperationsPage />);

    expect(
      screen.getByText(/per-session failure diagnostics not yet queryable/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/impilo\.rtc\.\*/)).toBeInTheDocument();
    // No fabricated failure rows exist
    expect(screen.queryByText(/ICE failure/i)).not.toBeInTheDocument();
  });
});
