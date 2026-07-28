import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MentalHealthQueuePage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

const REFERRAL = {
  id: "r-1",
  episode_id: "e-1234567890",
  handover_id: "h-1",
  subject_cpid: null,
  status: "PENDING" as const,
  request_reason: "acute psychosis",
  requested_by: "nurse-A",
  requested_at: "2026-07-28T10:00:00Z",
};

function renderWithQuery(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe("/work/mental-health referral queue", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("distinguishes a failed read from an empty queue — a read that failed is not zero pending", async () => {
    get.mockRejectedValue(new Error("network down"));
    renderWithQuery(<MentalHealthQueuePage />);
    expect(await screen.findByText(/could not be read/i)).toBeInTheDocument();
    expect(screen.queryByText(/no referrals pending acceptance/i)).not.toBeInTheDocument();
  });

  it("shows an empty state when there are no pending referrals", async () => {
    get.mockResolvedValue({ data: [] });
    renderWithQuery(<MentalHealthQueuePage />);
    expect(await screen.findByText(/no referrals pending acceptance/i)).toBeInTheDocument();
  });

  it("lists a pending referral with identity-unresolved fallback text", async () => {
    get.mockResolvedValue({ data: [REFERRAL] });
    renderWithQuery(<MentalHealthQueuePage />);
    expect(await screen.findByText(/identity unresolved/i)).toBeInTheDocument();
    expect(screen.getByText(/acute psychosis/i)).toBeInTheDocument();
  });

  it("accepting calls the accept endpoint for that referral", async () => {
    get.mockResolvedValue({ data: [REFERRAL] });
    post.mockResolvedValue({ data: { ...REFERRAL, status: "ACCEPTED" } });
    const user = userEvent.setup();
    renderWithQuery(<MentalHealthQueuePage />);

    await screen.findByText(/acute psychosis/i);
    await user.click(screen.getByRole("button", { name: /accept/i }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/mental-health/referrals/r-1/accept", {}),
    );
  });

  it("declining requires a reason before the confirm button is enabled, then calls decline", async () => {
    get.mockResolvedValue({ data: [REFERRAL] });
    post.mockResolvedValue({ data: { ...REFERRAL, status: "DECLINED" } });
    const user = userEvent.setup();
    renderWithQuery(<MentalHealthQueuePage />);

    await screen.findByText(/acute psychosis/i);
    await user.click(screen.getByRole("button", { name: /decline/i }));

    const confirmButton = screen.getByRole("button", { name: /confirm decline/i });
    expect(confirmButton).toBeDisabled();

    await user.type(screen.getByPlaceholderText(/decline reason/i), "not a psychiatric emergency");
    expect(confirmButton).toBeEnabled();

    await user.click(confirmButton);
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/mental-health/referrals/r-1/decline", {
        reason: "not a psychiatric emergency",
      }),
    );
  });
});
