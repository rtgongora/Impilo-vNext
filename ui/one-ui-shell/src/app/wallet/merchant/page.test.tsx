import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MerchantDashboardPage from "./page";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (sel: (s: { user: { id: string; providerId: string } }) => unknown) =>
    sel({ user: { id: "CPID-1", providerId: "PROV-1" } }),
}));

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MerchantDashboardPage />
    </QueryClientProvider>,
  );
}

describe("MerchantDashboardPage — earnings / payouts received", () => {
  beforeEach(() => get.mockReset());

  it("lists settlement payouts as earnings from the merchant wallet", async () => {
    get.mockImplementation((rawUrl?: string) => {
      const url = String(rawUrl ?? "");
      if (url.includes("/merchants/by-provider")) {
        return Promise.resolve({ data: { merchantId: "M1", walletId: "MWLT-1", status: "ACTIVE" } });
      }
      if (url.includes("/wallets/MWLT-1/transactions")) {
        return Promise.resolve({
          data: {
            items: [
              { txnId: "T1", transactionType: "SETTLEMENT", direction: "CREDIT", amount: 42.5, currency: "USD", reference: "payout:B1:IT1", createdAt: "2026-07-13T10:00:00Z" },
              { txnId: "T2", transactionType: "PAYMENT", direction: "CREDIT", amount: 5, currency: "USD", reference: "order-x", createdAt: "2026-07-13T09:00:00Z" },
            ],
          },
        });
      }
      if (url.includes("/wallets/MWLT-1")) return Promise.resolve({ data: { currency: "USD" } });
      if (url.includes("/balance")) return Promise.resolve({ data: { availableBalance: 47.5 } });
      return Promise.resolve({ data: {} });
    });

    renderPage();

    // The SETTLEMENT payout reference shows under earnings.
    const refs = await screen.findAllByText("payout:B1:IT1");
    expect(refs.length).toBeGreaterThan(0);
    expect(screen.getByText("Earnings — payouts received")).toBeInTheDocument();
  });

  it("shows an empty earnings state when no payouts have been received", async () => {
    get.mockImplementation((rawUrl?: string) => {
      const url = String(rawUrl ?? "");
      if (url.includes("/merchants/by-provider")) {
        return Promise.resolve({ data: { merchantId: "M1", walletId: "MWLT-1", status: "ACTIVE" } });
      }
      if (url.includes("/wallets/MWLT-1/transactions")) return Promise.resolve({ data: { items: [] } });
      if (url.includes("/wallets/MWLT-1")) return Promise.resolve({ data: { currency: "USD" } });
      if (url.includes("/balance")) return Promise.resolve({ data: { availableBalance: 0 } });
      return Promise.resolve({ data: {} });
    });

    renderPage();
    expect(await screen.findByText(/No payouts received yet/i)).toBeInTheDocument();
  });
});
