import { type ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({ apiClient: { get: vi.fn() } }));
const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

import CommsOpsPage from "./page";

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>);
}

describe("CommsOpsPage", () => {
  beforeEach(() => get.mockReset());

  it("renders the escalation queue, adapter status and on-call roster from live endpoints", async () => {
    get.mockImplementation((path?: string) => {
      const p = String(path ?? "");
      if (p.includes("/escalations")) return Promise.resolve({ data: [{ id: "e1", priority: "URGENT", status: "OPEN", tier: 1, resolutionBreached: true }] });
      if (p.includes("/delivery/adapters")) return Promise.resolve({ data: [{ channel: "SMS", status: "NOT_CONFIGURED" }] });
      if (p.includes("/on-call")) return Promise.resolve({ data: [{ actorId: "worker-1", actorType: "PROVIDER", dutyStatus: "ON_CALL" }] });
      return Promise.resolve({ data: [] });
    });

    wrap(<CommsOpsPage />);

    await waitFor(() => expect(screen.getByText("URGENT")).toBeInTheDocument());
    expect(screen.getByText("SLA breached")).toBeInTheDocument();
    // honest adapter status: SMS shows as not configured, not a fake "ready".
    expect(screen.getByText("not configured")).toBeInTheDocument();
    expect(screen.getByText("worker-1")).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/khuluma/escalations");
  });
});
