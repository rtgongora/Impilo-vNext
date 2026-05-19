import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ReconciliationPage from "./page";

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
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
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
      <ReconciliationPage />
    </QueryClientProvider>,
  );
}

describe("FinanceReconciliationPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation((url: string) => {
      if (url.startsWith("/internal/v1/finance/reconciliation/unmatched")) {
        return Promise.resolve({ content: [], totalElements: 0 });
      }
      if (url.startsWith("/internal/v1/finance/reconciliation/triple-match")) {
        return Promise.resolve({ data: [{ invoice_id: "INV-1", mushex_intent_id: "PI-1" }] });
      }
      return Promise.resolve({});
    });
    post.mockResolvedValue({ ok: true });
  });

  it("documents role boundary and calls import + unmatched + match BFF paths", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByRole("heading", { level: 1, name: /Reconciliation/i })).toBeInTheDocument();
    expect(screen.getByText(/SYSTEM_ADMIN/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^Import$/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/finance/reconciliation/import-statement", expect.any(Array));
    });

    await user.click(screen.getByRole("button", { name: /Fetch unmatched/i }));

    await waitFor(() => {
      expect(get).toHaveBeenCalled();
    });
    expect(get.mock.calls.some(([u]) => String(u).includes("/internal/v1/finance/reconciliation/unmatched"))).toBe(
      true,
    );

    await user.type(screen.getByLabelText("Reconciliation entry id"), "R-1");
    await user.type(screen.getByLabelText("Payment intent id for match"), "I-1");
    await user.click(screen.getByRole("button", { name: /Post match/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/finance/reconciliation/match?reconId=R-1",
        expect.objectContaining({ intentId: "I-1" }),
      );
    });

    await user.type(screen.getByLabelText("Triple match encounter id"), "ENC-1");
    await user.click(screen.getByRole("button", { name: /Fetch triple match/i }));

    await waitFor(() => {
      expect(get.mock.calls.some(([u]) => String(u).includes("/internal/v1/finance/reconciliation/triple-match"))).toBe(
        true,
      );
    });
  });
});
