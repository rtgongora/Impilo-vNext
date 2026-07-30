import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import RestraintReviewPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

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

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (state: unknown) => unknown) =>
    selector({ user: { providerId: "PROV-9" } }),
}));

const EVENT = {
  id: "re-1",
  referral_id: "ref-1",
  restraint_type: "SECLUSION",
  reason: "imminent harm to others",
  de_escalation_attempted: "verbal de-escalation, offered PRN",
  initiated_by: "nurse-B",
  initiated_at: "2026-07-29T22:00:00Z",
  ended_at: "2026-07-29T22:40:00Z",
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RestraintReviewPage />
    </QueryClientProvider>,
  );
}

describe("/work/mental-health/restraint-review", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  /**
   * The failure this page exists to prevent: an unreadable backlog rendering as "nothing to
   * review", which would let unreviewed restraint sit indefinitely with nobody aware of it.
   */
  it("does not render a failed read as an empty backlog", async () => {
    get.mockRejectedValue(new Error("no route to host"));
    renderPage();

    expect(await screen.findByTestId("restraint-backlog-unreadable")).toHaveTextContent(
      /not an empty one/i,
    );
    expect(screen.queryByTestId("restraint-backlog-empty")).toBeNull();
  });

  it("says the backlog is genuinely clear when the read succeeded with no rows", async () => {
    get.mockResolvedValue({ data: [] });
    renderPage();

    expect(await screen.findByTestId("restraint-backlog-empty")).toHaveTextContent(
      /every recorded episode has been reviewed/i,
    );
  });

  it("shows what de-escalation was attempted, which is what makes review possible", async () => {
    get.mockResolvedValue({ data: [EVENT] });
    renderPage();

    expect(await screen.findByTestId("restraint-backlog-row")).toHaveTextContent(
      "verbal de-escalation, offered PRN",
    );
    expect(screen.getByTestId("restraint-backlog-count")).toHaveTextContent("1 episode");
  });

  it("will not submit a review with no outcome, because a reviewless review is not a review", async () => {
    get.mockResolvedValue({ data: [EVENT] });
    const user = userEvent.setup();
    renderPage();

    await screen.findByTestId("restraint-backlog-row");
    expect(screen.getByTestId("restraint-backlog-review")).toBeDisabled();

    await user.type(screen.getByTestId("restraint-backlog-outcome"), "proportionate");
    expect(screen.getByTestId("restraint-backlog-review")).toBeEnabled();
  });

  it("records the review against the event, attributed to the signed-in provider", async () => {
    get.mockResolvedValue({ data: [EVENT] });
    post.mockResolvedValue({ data: { ...EVENT, review_outcome: "proportionate" } });
    const user = userEvent.setup();
    renderPage();

    await screen.findByTestId("restraint-backlog-row");
    await user.type(screen.getByTestId("restraint-backlog-outcome"), "proportionate");
    await user.click(screen.getByTestId("restraint-backlog-review"));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/mental-health/restraint-events/re-1/review", {
        reviewOutcome: "proportionate",
        reviewedBy: "PROV-9",
      }),
    );
  });

  it("surfaces the service's refusal rather than silently dropping the review", async () => {
    get.mockResolvedValue({ data: [EVENT] });
    post.mockRejectedValue({ error: "restraint_review_refused", message: "review outcome is required" });
    const user = userEvent.setup();
    renderPage();

    await screen.findByTestId("restraint-backlog-row");
    await user.type(screen.getByTestId("restraint-backlog-outcome"), "x");
    await user.click(screen.getByTestId("restraint-backlog-review"));

    expect(await screen.findByTestId("restraint-backlog-error")).toHaveTextContent(
      "review outcome is required",
    );
  });
});
