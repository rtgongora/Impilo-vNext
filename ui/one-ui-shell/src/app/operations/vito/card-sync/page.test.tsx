import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider, MutationCache } from "@tanstack/react-query";
import CardSyncOpsWorkspace from "./page";

const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { post: (...a: unknown[]) => post(...a) } }));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }));

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    // Errors are handled by the component's onError; swallow at the cache so the
    // test harness doesn't flag them as unhandled rejections.
    mutationCache: new MutationCache({ onError: () => {} }),
  });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

describe("Card-sync ops page", () => {
  beforeEach(() => post.mockReset());

  it("drains the queue and shows per-pass counts", async () => {
    post.mockResolvedValue({ data: { processed: 3, synced: 2, failed: 0, retried: 1 } });
    wrap(<CardSyncOpsWorkspace />);
    fireEvent.click(screen.getByTestId("drain-btn"));
    await waitFor(() => expect(screen.getByTestId("drain-result")).toBeInTheDocument());
    expect(screen.getByTestId("drain-result").textContent).toContain("2");
    expect(post).toHaveBeenCalledWith("/internal/v1/admin/card-sync/drain", {});
  });

  it("shows an unavailable message on failure", async () => {
    post.mockRejectedValueOnce({ status: 503 });
    wrap(<CardSyncOpsWorkspace />);
    fireEvent.click(screen.getByTestId("drain-btn"));
    expect(await screen.findByTestId("drain-error")).toBeInTheDocument();
  });
});
