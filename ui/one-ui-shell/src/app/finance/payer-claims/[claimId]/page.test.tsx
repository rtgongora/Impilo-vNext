import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FinancePayerClaimDetailPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ claimId: "CLM-1" }),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
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

vi.mock("@/components/FinanceAccessNotice", () => ({
  FinancePayerOpsReconciliationNotice: () => <div>PAYER_OPS notice</div>,
}));

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <FinancePayerClaimDetailPage />
    </QueryClientProvider>,
  );
}

describe("FinancePayerClaimDetailPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockResolvedValue({ claimId: "CLM-1", status: "DRAFT" });
    post.mockResolvedValue({ ok: true });
  });

  it("loads claim detail and posts submit and dispute actions", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByRole("link", { name: /Back to payer claims/i })).toHaveAttribute(
      "href",
      "/finance/payer-claims",
    );

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith("/internal/v1/finance/payer-claims/CLM-1");
    });

    const submitButton = screen.getByRole("button", { name: /Submit claim/i });
    expect(submitButton).toBeDisabled();
    await user.click(screen.getByLabelText(/I confirm this submit action is intentional/i));
    await user.click(submitButton);

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/finance/payer-claims/CLM-1/submit");
    });

    fireEvent.change(screen.getByLabelText("Payer claim dispute JSON"), {
      target: { value: '{"reason":"missing invoice","notes":"follow up"}' },
    });
    const disputeButton = screen.getByRole("button", { name: /Dispute claim/i });
    expect(disputeButton).toBeDisabled();
    await user.click(screen.getByLabelText(/I confirm this dispute action is intentional/i));
    await user.click(disputeButton);

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/finance/payer-claims/CLM-1/dispute", {
        reason: "missing invoice",
        notes: "follow up",
      });
    });
  });
});
