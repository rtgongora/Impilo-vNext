import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MarketplacePickupPage from "./page";

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

vi.mock("@/lib/api-client", () => ({
  apiClient: { post },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MarketplacePickupPage />
    </QueryClientProvider>,
  );
}

describe("MarketplacePickupPage", () => {
  beforeEach(() => {
    post.mockReset();
    post.mockResolvedValue({ ok: true });
  });

  it("issues a pickup token and posts pickup claim payloads", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText("Pickup order id"), "ORD-7");
    await user.click(screen.getByRole("button", { name: /Issue token/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/commerce/orders/ORD-7/pickup/issue");
    });

    fireEvent.change(screen.getByLabelText("Pickup claim JSON"), {
      target: { value: '{"pickupToken":"abc","claimedBy":"nurse-1","claimantId":"id-1"}' },
    });
    await user.click(screen.getByRole("button", { name: /Claim pickup/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/commerce/pickup/claim", {
        pickupToken: "abc",
        claimedBy: "nurse-1",
        claimantId: "id-1",
      });
    });
  });
});
