import { type ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useParams: () => ({ transactionId: "TX-1" }),
}));

const get = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiClient: { get: (...args: unknown[]) => get(...args) },
}));

import CitizenVisitStatusPage from "./page";

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>);
}

describe("CitizenVisitStatusPage", () => {
  beforeEach(() => get.mockReset());

  it("renders the composed patient-facing status", async () => {
    get.mockResolvedValue({
      data: {
        transactionId: "TX-1",
        available: true,
        state: "QUEUED",
        statusLabel: "In the queue",
        message: "You're in the queue. Please stay nearby — we'll call you soon.",
      },
    });

    wrap(<CitizenVisitStatusPage />);

    await waitFor(() => expect(screen.getByText("In the queue")).toBeInTheDocument());
    expect(screen.getByText(/we'll call you soon/i)).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/mobile/citizen/visit/TX-1/status");
  });

  it("shows an honest unavailable state when the lane can't resolve the visit", async () => {
    get.mockResolvedValue({
      data: { transactionId: "TX-1", available: false, statusLabel: "Status unavailable", message: "We can't show this visit's status right now." },
    });

    wrap(<CitizenVisitStatusPage />);

    await waitFor(() => expect(screen.getByText("Unavailable")).toBeInTheDocument());
  });
});
