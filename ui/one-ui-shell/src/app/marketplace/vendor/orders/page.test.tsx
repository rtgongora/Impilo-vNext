import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import VendorOrdersPage from "./page";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams("vendorId=V-1"),
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
      <VendorOrdersPage />
    </QueryClientProvider>,
  );
}

describe("MarketplaceVendorOrdersPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/commerce/vendor/V-1/orders") {
        return Promise.resolve([{ orderId: "O-9", status: "NEW" }]);
      }
      return Promise.resolve({});
    });
    post.mockResolvedValue({ ok: true });
  });

  it("loads vendor orders and posts accept", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText(/Vendor:/i)).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "O-9" })).toHaveAttribute("href", "/marketplace/orders/O-9");

    await user.click(screen.getByRole("button", { name: "Accept" }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/commerce/vendor/V-1/orders/O-9/accept");
    });
    expect(get).toHaveBeenCalledWith("/internal/v1/commerce/vendor/V-1/orders");
  });
});
