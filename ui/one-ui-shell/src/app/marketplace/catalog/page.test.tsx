import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import CatalogPage from "./page";

const push = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
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

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } | null }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <CatalogPage />
    </QueryClientProvider>,
  );
}

describe("CatalogPage (MSIKA registry mode)", () => {
  beforeEach(() => {
    push.mockReset();
    get.mockReset();
    post.mockReset();
    get.mockImplementation((url: string) => {
      if (url.startsWith("/internal/v1/product-registry/search")) {
        return Promise.resolve({
          items: [{ itemId: "I-1", displayName: "Cold chain box", msikaCoreCode: "MSK-001" }],
        });
      }
      if (url.startsWith("/internal/v1/marketplace/catalog")) {
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({ data: [] });
    });
    post.mockImplementation((url: string) => {
      if (url === "/internal/v1/commerce/orders") {
        return Promise.resolve({ orderId: "ORD-1" });
      }
      if (url === "/internal/v1/commerce/cart/validate") {
        return Promise.resolve({ valid: true });
      }
      return Promise.resolve({});
    });
  });

  it("searches product registry and creates a commerce order from selected items", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: /Product registry \(MSIKA\)/i }));
    await user.type(screen.getByPlaceholderText(/Search MSIKA registry/i), "cold");

    expect(await screen.findByText(/Cold chain box/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Add for facility/i }));

    await user.click(screen.getByRole("button", { name: /Validate cart/i }));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/commerce/cart/validate", expect.any(Object)));

    await user.click(screen.getByRole("button", { name: /Order for facility/i }));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/commerce/orders", expect.any(Object)));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/marketplace/orders/ORD-1"));
  });
});

