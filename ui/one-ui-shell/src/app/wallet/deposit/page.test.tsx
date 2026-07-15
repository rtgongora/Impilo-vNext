import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DepositPage from "./page";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (sel: (s: { user: { id: string } }) => unknown) => sel({ user: { id: "CPID-1" } }),
}));

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <DepositPage />
    </QueryClientProvider>,
  );
}

// Route by URL: wallet lookup, funding sources, deposits list.
function wireGet(deposits: unknown[]) {
  get.mockImplementation((url: string) => {
    if (url.includes("/wallet/me/funding-sources")) return Promise.resolve({ data: [] });
    if (url.includes("/wallet/me")) return Promise.resolve({ data: { walletId: "WLT-1" } });
    if (url.endsWith("/deposits")) return Promise.resolve({ data: deposits });
    return Promise.resolve({ data: {} });
  });
}

describe("DepositPage — deposit history + cancel", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("lists the citizen's deposits with reference code and status", async () => {
    wireGet([
      { depositId: "D1", referenceCode: "IMP-ABCD2345", channel: "BANK_ZIPIT", amount: 50, currency: "USD", status: "PENDING" },
      { depositId: "D2", referenceCode: "IMP-EFGH6789", channel: "CASH", amount: 20, currency: "USD", status: "CONFIRMED" },
    ]);

    renderPage();

    expect(await screen.findByText("IMP-ABCD2345")).toBeInTheDocument();
    expect(screen.getByText("IMP-EFGH6789")).toBeInTheDocument();
    expect(screen.getByText("PENDING")).toBeInTheDocument();
    expect(screen.getByText("CONFIRMED")).toBeInTheDocument();
  });

  it("cancels a pending deposit through the self-wallet route", async () => {
    wireGet([
      { depositId: "D1", referenceCode: "IMP-ABCD2345", channel: "BANK_ZIPIT", amount: 50, currency: "USD", status: "PENDING" },
    ]);
    post.mockResolvedValue({ data: { status: "CANCELLED" } });

    renderPage();
    const cancelBtn = await screen.findByRole("button", { name: /cancel/i });
    await userEvent.click(cancelBtn);

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/funding/WLT-1/deposits/D1/cancel",
        {},
      ),
    );
  });

  it("shows an empty state when there are no deposits", async () => {
    wireGet([]);
    renderPage();
    expect(await screen.findByText("No deposits yet.")).toBeInTheDocument();
  });
});
