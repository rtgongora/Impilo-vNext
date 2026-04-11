import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SubstitutionsPage from "./page";

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
      <SubstitutionsPage />
    </QueryClientProvider>,
  );
}

describe("SubstitutionsPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockResolvedValue({ substitutions: [{ order_id: "ORD-1", status: "PENDING", reason: "Stock-out" }] });
    post.mockResolvedValue({ ok: true });
  });

  it("fetches the substitution inbox and approves a selected order", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByRole("heading", { level: 1, name: /Substitutions/i })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Fetch substitutions/i }));

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith("/internal/v1/commerce/substitutions?page=0&size=20");
    });

    await user.click(await screen.findByRole("button", { name: "Use" }));
    await user.click(screen.getByRole("button", { name: /Approve substitution/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/commerce/rx/ORD-1/substitution/approve",
        expect.objectContaining({ reason: "Clinically acceptable substitution" }),
      );
    });
  });
});
