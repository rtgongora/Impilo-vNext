import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FinancePayerClaimsPage from "./page";

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
  PageShell: ({
    children,
    title,
    subtitle,
  }: {
    children: ReactNode;
    title: string;
    subtitle?: string;
  }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/components/FinanceAccessNotice", () => ({
  FinancePayerOpsReconciliationNotice: () => <div>PAYER_OPS notice</div>,
}));

const listMock = vi.fn();

vi.mock("@/hooks/queries/usePayerClaims", () => ({
  usePayerClaims: (...args: unknown[]) => listMock(...args),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <FinancePayerClaimsPage />
    </QueryClientProvider>,
  );
}

describe("FinancePayerClaimsPage", () => {
  beforeEach(() => {
    listMock.mockReset();
    listMock.mockReturnValue({
      data: {
        data: {
          items: [
            {
              claimId: "CLM-1",
              billId: "BILL-1",
              insurerId: "INS-001",
              status: "SUBMITTED",
              submittedAt: "2026-04-11T08:00:00Z",
            },
          ],
        },
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
  });

  it("renders the queue and links into claim detail", () => {
    renderPage();

    expect(screen.getByRole("heading", { level: 1, name: /Payer claims queue/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Payer ops/i })).toHaveAttribute("href", "/finance/payer-ops");
    expect(screen.getByRole("link", { name: /Commerce & payer stack/i })).toHaveAttribute(
      "href",
      "/finance/commerce-integrations",
    );
    expect(screen.getByText("CLM-1")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open claim/i })).toHaveAttribute("href", "/finance/payer-claims/CLM-1");
  });

  it("refetches with updated filters when fetch claims is pressed", async () => {
    const user = userEvent.setup();
    const refetch = vi.fn();
    listMock.mockReturnValue({
      data: { data: { items: [] } },
      isLoading: false,
      isError: false,
      refetch,
    });

    renderPage();

    fireEvent.change(screen.getByLabelText(/Status/i), { target: { value: "REJECTED" } });
    fireEvent.change(screen.getByLabelText(/Insurer ID/i), { target: { value: "INS-777" } });
    fireEvent.change(screen.getByLabelText(/^Page$/i), { target: { value: "2" } });
    fireEvent.change(screen.getByLabelText(/^Size$/i), { target: { value: "50" } });

    await user.click(screen.getByRole("button", { name: /Fetch claims/i }));

    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(
        { status: "REJECTED", insurerId: "INS-777", page: 2, size: 50 },
        true,
      );
    });
    expect(refetch).toHaveBeenCalled();
  });
});
