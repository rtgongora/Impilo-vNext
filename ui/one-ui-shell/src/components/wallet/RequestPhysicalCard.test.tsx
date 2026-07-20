import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RequestPhysicalCard } from "./RequestPhysicalCard";

const post = vi.fn();
const get = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiClient: { post: (...a: unknown[]) => post(...a), get: (...a: unknown[]) => get(...a) },
}));

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

describe("RequestPhysicalCard", () => {
  beforeEach(() => { post.mockReset(); get.mockReset(); });

  it("submits a fresh-card request and shows the PROOFING state", async () => {
    post.mockResolvedValue({ data: { id: 42, state: "PROOFING" } });
    get.mockResolvedValue({ data: { id: 42, state: "PROOFING" } });
    wrap(<RequestPhysicalCard />);
    fireEvent.click(screen.getByTestId("request-physical-btn"));
    const status = await screen.findByTestId("issuance-status");
    expect(status.textContent).toContain("PROOFING");
    expect(post).toHaveBeenCalledWith("/internal/v1/wallet/cards/request-physical", {});
  });

  it("shows a clean error on failure", async () => {
    post.mockRejectedValueOnce({ status: 503 });
    wrap(<RequestPhysicalCard />);
    fireEvent.click(screen.getByTestId("request-physical-btn"));
    expect(await screen.findByTestId("request-physical-error")).toBeInTheDocument();
  });
});
