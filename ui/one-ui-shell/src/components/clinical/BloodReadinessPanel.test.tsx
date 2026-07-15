import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post: vi.fn() } }));

import { BloodReadinessPanel } from "./BloodReadinessPanel";

function renderPanel(orderId?: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <BloodReadinessPanel orderId={orderId} />
    </QueryClientProvider>,
  );
}

describe("BloodReadinessPanel", () => {
  beforeEach(() => get.mockReset());

  it("prompts for an order when none is provided", () => {
    renderPanel();
    expect(screen.getByPlaceholderText(/MADI blood order reference/i)).toBeInTheDocument();
  });

  it("renders BLOCKED with the honest blocker reason", async () => {
    get.mockResolvedValue({ data: { status: "BLOCKED", madi_status: "DRAFT", blocker: "needs RESERVED + COMPATIBLE crossmatch" } });
    renderPanel("ord-1");
    await waitFor(() => expect(screen.getByText("BLOCKED")).toBeInTheDocument());
    expect(screen.getByText(/needs RESERVED \+ COMPATIBLE crossmatch/i)).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith(expect.stringContaining("/internal/v1/ed/blood-readiness?orderId=ord-1"));
  });

  it("renders READY when reserved + compatible", async () => {
    get.mockResolvedValue({ data: { status: "READY", reservation_status: "RESERVED", crossmatch_status: "COMPATIBLE", madi_status: "RESERVED" } });
    renderPanel("ord-2");
    await waitFor(() => expect(screen.getByText("READY")).toBeInTheDocument());
  });
});
