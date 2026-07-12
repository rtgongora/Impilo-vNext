import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OrderDetailPage from "./page";

const ORDER_ID = "ORD-1";

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: ORDER_ID }),
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

const { post } = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock("@/hooks/queries/useCommerceFlow", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/hooks/queries/useCommerceFlow")>();
  return {
    ...actual,
    useCommerceOrder: () => ({
      data: { orderId: ORDER_ID, status: "PENDING" },
      isLoading: false,
      isError: false,
    }),
    useCommerceOrderTracking: () => ({
      data: { status: "PENDING" },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    }),
    useCommerceOrderAction: () => ({
      isPending: false,
      mutate: (_args: { orderId: string; action: string }, opts?: { onSuccess?: (res: unknown) => void }) => {
        void post(`/internal/v1/commerce/orders/${ORDER_ID}/validate`).then((res: unknown) => opts?.onSuccess?.(res));
      },
    }),
  };
});

vi.mock("@/hooks/queries/useCommercePickup", () => ({
  useCommerceIssuePickup: () => ({ isPending: false, data: null, mutate: vi.fn() }),
}));

vi.mock("@/hooks/queries/useMarketplace", () => ({
  useMarketplaceOrder: () => ({
    data: undefined,
    isLoading: false,
    isError: true,
  }),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <OrderDetailPage />
    </QueryClientProvider>,
  );
}

describe("OrderDetailPage (commerce)", () => {
  beforeEach(() => {
    post.mockReset();
    post.mockResolvedValue({ ok: true });
  });

  it("loads commerce order and invokes validate action", async () => {
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getAllByText("Commerce order").length).toBeGreaterThan(0);
    });
    await user.click(screen.getByRole("button", { name: "VALIDATE" }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(`/internal/v1/commerce/orders/${ORDER_ID}/validate`);
    });
  });
});
