import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FinanceLedgerPage from "./page";

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

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <FinanceLedgerPage />
    </QueryClientProvider>,
  );
}

describe("FinanceLedgerPage", () => {
  beforeEach(() => {
    get.mockReset();
    get.mockResolvedValue({ intentId: "pi-77", entries: [] });
  });

  it("loads ledger detail through the Experience finance route", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText("Ledger intent id"), "pi-77");
    await user.click(screen.getByRole("button", { name: /Load ledger/i }));

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith("/internal/v1/finance/ledger?intentId=pi-77");
    });
  });
});
