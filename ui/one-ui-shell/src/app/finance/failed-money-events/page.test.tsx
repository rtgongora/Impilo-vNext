import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FailedMoneyEventsPage from "./page";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }));
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <FailedMoneyEventsPage />
    </QueryClientProvider>,
  );
}

const DEAD_EVENT = {
  id: 7,
  originalTopic: "costa.settlement.v1",
  payload: "{}",
  errorMessage: "connection reset",
  status: "DEAD",
  createdAt: "2026-07-15T10:00:00Z",
  replayedAt: null,
  replayedBy: null,
};

describe("FailedMoneyEventsPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("lists dead-lettered events and replays one", async () => {
    get.mockResolvedValue({ data: [DEAD_EVENT] });
    post.mockResolvedValue({ data: { id: 7, status: "REPLAYED" } });

    renderPage();

    expect(await screen.findByText("costa.settlement.v1")).toBeInTheDocument();
    // "DEAD" appears both as a filter chip and the row status badge.
    expect(screen.getAllByText("DEAD").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText(/connection reset/i)).toBeInTheDocument();

    // exact "Replay" — avoids the "REPLAYED" filter chip
    await userEvent.click(screen.getByRole("button", { name: "Replay" }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/finance/failed-money-events/7/replay"),
    );
  });

  it("shows an honest empty state when the rails are clear", async () => {
    get.mockResolvedValue({ data: [] });
    renderPage();
    expect(await screen.findByText(/money rails are clear/i)).toBeInTheDocument();
  });
});
