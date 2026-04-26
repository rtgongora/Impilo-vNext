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

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
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
    get.mockReset();
    post.mockReset();
    get.mockImplementation((url: string) => {
      if (url === `/internal/v1/commerce/orders/${ORDER_ID}`) return Promise.resolve({ orderId: ORDER_ID, status: "PENDING" });
      if (url === `/internal/v1/commerce/orders/${ORDER_ID}/tracking`) return Promise.resolve({ status: "PENDING" });
      if (url === `/internal/v1/marketplace/orders/${ORDER_ID}`) return Promise.reject({ status: 404 });
      return Promise.resolve({});
    });
    post.mockResolvedValue({ ok: true });
  });

  it("loads commerce order and invokes validate action", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText(/Commerce order/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "VALIDATE" }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(`/internal/v1/commerce/orders/${ORDER_ID}/validate`);
    });
  });
});

