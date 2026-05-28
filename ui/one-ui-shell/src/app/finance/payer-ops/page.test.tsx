import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import PayerOpsPage from "./page";

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
      <PayerOpsPage />
    </QueryClientProvider>,
  );
}

describe("FinancePayerOpsPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation((url: string) => {
      if (url.includes("/payment-intents/pi-1") && !url.includes("/receipts")) {
        return Promise.resolve({ intentId: "pi-1", status: "PAID" });
      }
      if (url.includes("/payment-intents/pi-1/receipts")) {
        return Promise.resolve([]);
      }
      if (url.includes("/internal/v1/finance/settlements")) {
        return Promise.resolve({ data: [{ id: "set-1" }] });
      }
      if (url.includes("/adapters")) return Promise.resolve([{ id: "a1" }]);
      if (url.includes("/fraud-flags")) return Promise.resolve({ content: [] });
      if (url.includes("/ops-reviews")) return Promise.resolve({ content: [] });
      return Promise.resolve({});
    });
    post.mockResolvedValue({ ok: true });
  });

  it("loads intent and cancels via BFF paths", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByText(/DEVELOPER/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText("Payment intent id"), "pi-1");
    await user.click(screen.getByRole("button", { name: /Load intent/i }));

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith("/internal/v1/finance/payer-ops/payment-intents/pi-1");
    });

    await user.click(screen.getByRole("button", { name: /Cancel intent/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/finance/payer-ops/payment-intents/pi-1/cancel");
    });
  });

  it("composes payer journey state and linked settlement lookup", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText("Payment intent id"), "pi-1");
    await user.click(screen.getByRole("button", { name: /Load intent/i }));

    await waitFor(() => {
      expect(screen.getByText(/Current payer state:/i)).toBeInTheDocument();
      expect(screen.getAllByText("Remittance claim").length).toBeGreaterThan(0);
    });
    expect(get.mock.calls.some((call) => String(call[0]).includes("/internal/v1/finance/settlements?intentIds=pi-1"))).toBe(
      true,
    );
  });
});
