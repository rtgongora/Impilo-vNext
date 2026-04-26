import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RefundsPage from "./page";

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
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RefundsPage />
    </QueryClientProvider>,
  );
}

describe("FinanceRefundsPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/finance/refunds/payment-intents/int-1") {
        return Promise.resolve({ intentId: "int-1", status: "PAID" });
      }
      return Promise.resolve({});
    });
    post.mockResolvedValue({ refundId: "r1" });
  });

  it("loads intent and posts refund", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByText(/FACILITY_ADMIN/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText("Payment intent id"), "int-1");
    await user.click(screen.getByRole("button", { name: /Load intent/i }));

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith("/internal/v1/finance/refunds/payment-intents/int-1");
    });

    await user.type(screen.getByLabelText("Amount"), "10");
    await user.type(screen.getByLabelText("Reason"), "duplicate");
    await user.click(screen.getByRole("button", { name: /Submit refund/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/finance/refunds/payment-intents/int-1/refund", {
        amount: 10,
        reason: "duplicate",
      });
    });
  });
});
