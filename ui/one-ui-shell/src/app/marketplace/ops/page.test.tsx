import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MarketplaceOpsPage from "./page";

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
      <MarketplaceOpsPage />
    </QueryClientProvider>,
  );
}

describe("MarketplaceOpsPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation((url: string) => {
      if (url.startsWith("/internal/v1/commerce/ops/reviews")) return Promise.resolve({ content: [] });
      if (url === "/internal/v1/commerce/ops/stuck-orders") return Promise.resolve([{ orderId: "O-2" }]);
      if (url.startsWith("/internal/v1/commerce/ops/audit")) return Promise.resolve({ content: [] });
      if (url.startsWith("/internal/v1/commerce/ops/vendors?page")) return Promise.resolve({ content: [] });
      if (url === "/internal/v1/commerce/ops/vendors/V-1") return Promise.resolve({ vendorId: "V-1" });
      return Promise.resolve({});
    });
    post.mockResolvedValue({ ok: true });
  });

  it("loads ops reviews and posts approve actions through the BFF", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByRole("heading", { level: 1, name: /Marketplace ops/i })).toBeInTheDocument();
    expect(screen.getByText(/Ops reviews/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Fetch reviews/i }));

    await waitFor(() => {
      expect(get).toHaveBeenCalled();
    });
    expect(get.mock.calls.some(([url]) => String(url).includes("/internal/v1/commerce/ops/reviews"))).toBe(true);

    await user.type(screen.getByLabelText("Review id"), "REV-1");
    await user.click(screen.getByRole("button", { name: /Approve/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/commerce/ops/reviews/REV-1/approve", {});
    });
  });
});

