import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

import { CriticalResultsPanel } from "./CriticalResultsPanel";

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <CriticalResultsPanel />
    </QueryClientProvider>,
  );
}

describe("CriticalResultsPanel", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    post.mockResolvedValue({});
  });

  it("shows the all-clear when nothing is unacknowledged", async () => {
    get.mockResolvedValue({ data: [] });
    renderPanel();
    await waitFor(() => expect(screen.getByText(/No unacknowledged critical results/i)).toBeInTheDocument());
  });

  it("lists a critical result and acknowledges it via the safety endpoint", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    get.mockResolvedValue({ data: [{ resultId: "res-1", orderId: "ord-1", criticalReason: "Serum K+ 7.2 mmol/L", critical: true }] });
    renderPanel();
    await waitFor(() => expect(screen.getByText(/Serum K\+ 7.2 mmol\/L/i)).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /Acknowledge/i }));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/diagnostics/results/res-1/critical/ack", expect.anything()));
  });
});
