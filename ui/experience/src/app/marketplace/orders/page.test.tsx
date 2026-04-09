import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OrdersPage from "./page";

const { get, post } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => <a href={href} {...props}>{children}</a>,
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div>,
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central Hospital" } }),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (state: { user: { displayName: string } }) => unknown) =>
    selector({ user: { displayName: "Tariro Moyo" } }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><OrdersPage /></QueryClientProvider>);
}

describe("OrdersPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("creates real marketplace orders with the normalized backend contract", async () => {
    get.mockResolvedValue({ data: [] });
    post.mockResolvedValue({ data: { id: "order-2", type: "MarketplaceOrder", attributes: { facility_id: "facility-1", order_number: "PO-NEW", status: "PENDING", total_amount: 120, currency: "USD", items: "[]", ordered_by: "Tariro Moyo", created_at: "2026-04-09T10:00:00.000Z" } } });

    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText(/Catalog or service ID/i), "95000000-0000-0000-0000-000000000001");
    await user.type(screen.getByLabelText(/Description/i), "Cold-chain boxes");
    await user.clear(screen.getByLabelText(/Quantity/i));
    await user.type(screen.getByLabelText(/Quantity/i), "2");
    await user.clear(screen.getByLabelText(/Unit price/i));
    await user.type(screen.getByLabelText(/Unit price/i), "60");
    await user.click(screen.getByRole("button", { name: /Create order/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/marketplace/orders", expect.objectContaining({
        facility_id: "facility-1",
        ordered_by: "Tariro Moyo",
        total_amount: "120.00",
      }));
    });

    const payload = post.mock.calls[0][1] as { items: string };
    expect(JSON.parse(payload.items)).toEqual([
      expect.objectContaining({ productId: "95000000-0000-0000-0000-000000000001", description: "Cold-chain boxes", quantity: 2, unitPrice: 60 }),
    ]);
  }, 15000);
});
