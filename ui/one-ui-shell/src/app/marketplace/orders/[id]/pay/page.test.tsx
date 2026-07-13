import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OrderPayPage from "./page";
import { findIntentId } from "@/lib/marketplace/find-intent-id";

const ORDER_ID = "ORD-PAY-1";

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: ORDER_ID }),
  useSearchParams: () => new URLSearchParams("method=MUSHE_WALLET"),
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

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (state: { user: { id: string } }) => unknown) =>
    selector({ user: { id: "actor-1" } }),
}));

const { payMutate, confirmMutate, orderState } = vi.hoisted(() => ({
  payMutate: vi.fn(),
  confirmMutate: vi.fn(),
  orderState: { status: "PRICED" },
}));

vi.mock("@/hooks/queries/useCommerceFlow", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/hooks/queries/useCommerceFlow")>();
  return {
    ...actual,
    useCommerceOrder: () => ({
      data: { success: true, data: { orderId: ORDER_ID, status: orderState.status, amountTotal: 25.5, currency: "USD" } },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    }),
    useCommerceOrderAction: () => ({
      isPending: false,
      mutate: (
        args: { orderId: string; action: string },
        opts?: { onSuccess?: (res: unknown) => void },
      ) => {
        payMutate(args);
        opts?.onSuccess?.({ success: true, data: { orderId: ORDER_ID, mushexPaymentIntentId: "PI-99" } });
      },
    }),
    useCommercePaymentConfirm: () => ({
      isPending: false,
      mutate: (args: unknown, opts?: { onSuccess?: (res: unknown) => void }) => {
        confirmMutate(args);
        opts?.onSuccess?.({ ok: true });
      },
    }),
  };
});

vi.mock("@/hooks/queries/useMusheWallet", () => ({
  useWalletByOwner: () => ({ data: undefined }),
  useBalance: () => ({ data: undefined }),
  useWalletPay: () => ({ mutateAsync: vi.fn() }),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <OrderPayPage />
    </QueryClientProvider>,
  );
}

describe("OrderPayPage", () => {
  beforeEach(() => {
    payMutate.mockReset();
    confirmMutate.mockReset();
    orderState.status = "PRICED";
  });

  it("finds a MusheX intent id in tolerant shapes", () => {
    expect(findIntentId({ data: { mushexPaymentIntentId: "PI-1" } })).toBe("PI-1");
    expect(findIntentId({ data: { settlement: { paymentIntentId: "PI-2" } } })).toBe("PI-2");
    expect(findIntentId({ nothing: true })).toBeUndefined();
  });

  it("runs pay → intent → wallet confirm with the intent as reference", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByText("USD 25.50")).toBeTruthy();

    await user.click(screen.getByRole("button", { name: /start payment/i }));
    await waitFor(() => {
      expect(payMutate).toHaveBeenCalledWith({ orderId: ORDER_ID, action: "pay" });
      expect(screen.getByText("PI-99")).toBeTruthy();
    });

    await user.click(screen.getByRole("button", { name: /pay .*now/i }));
    await waitFor(() => {
      expect(confirmMutate).toHaveBeenCalledWith(
        expect.objectContaining({ orderId: ORDER_ID, intentId: "PI-99", method: "MUSHE_WALLET", amount: 25.5, currency: "USD" }),
      );
    });
  });

  it("shows the success panel with wallet + tracking links once PAID", () => {
    orderState.status = "PAID";
    renderPage();
    expect(screen.getByText("Payment received")).toBeTruthy();
    expect(screen.getByRole("link", { name: /wallet transactions/i }).getAttribute("href")).toBe("/wallet/transactions");
    expect(screen.getByRole("link", { name: /track order/i }).getAttribute("href")).toBe(`/marketplace/orders/${ORDER_ID}`);
  });
});
