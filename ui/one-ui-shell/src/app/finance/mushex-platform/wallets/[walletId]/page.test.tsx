import type { ReactNode } from "react";
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import CustodialWalletDetailPage from "./page";

/**
 * Phase 4 — read-only custodial wallet detail page tests.
 *
 * The page is a thin consumer of two already-existing BFF passthroughs
 * (per-wallet transactions, wallet-filtered card profiles). The tests cover:
 *
 *   1) header + identity card render and quote the wallet ID;
 *   2) transactions table renders rows from a top-level array;
 *   3) card-profiles table renders rows from a `{ data: [] }` envelope;
 *   4) empty payload renders an honest "no rows" message, not fabricated data;
 *   5) upstream error renders the documented "could not load" message;
 *   6) no write-action buttons are surfaced on this page.
 */

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div data-testid="app-layout">{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p data-testid="subtitle">{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ walletId: "wallet-abc-123" }),
}));

interface QueryShape<T> {
  data: T;
  isLoading?: boolean;
  isFetching?: boolean;
  isError?: boolean;
  error?: unknown;
}

vi.mock("@/hooks/queries/useMushexPlatformAdmin", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useMushexPlatformAdmin")>(
    "@/hooks/queries/useMushexPlatformAdmin",
  );
  return {
    ...actual,
    useMushexPlatformWalletTransactions: (): QueryShape<unknown[]> => ({
      data: [
        {
          id: "txn-1",
          transactionType: "CREDIT",
          amount: "100.00",
          referenceCode: "REF-001",
          createdAt: "2026-05-12T10:00:00Z",
        },
        {
          id: "txn-2",
          transactionType: "DEBIT",
          amount: "25.00",
          referenceCode: "REF-002",
          createdAt: "2026-05-12T11:30:00Z",
        },
      ],
      isLoading: false,
      isError: false,
    }),
    useMushexPlatformCardProfilesForWallet: (): QueryShape<{ data: unknown[] }> => ({
      data: {
        data: [
          {
            id: "card-1",
            brand: "VISA",
            maskedPan: "**** 1234",
            status: "ACTIVE",
            createdAt: "2026-04-01T08:00:00Z",
          },
        ],
      },
      isLoading: false,
      isError: false,
    }),
  };
});

describe("CustodialWalletDetailPage", () => {
  it("renders the page title and the wallet ID in the identity card", () => {
    render(<CustodialWalletDetailPage />);

    expect(screen.getByRole("heading", { name: "Custodial wallet" })).toBeInTheDocument();
    expect(screen.getByTestId("subtitle").textContent).toContain("wallet-abc-123");
    expect(screen.getByText("Wallet identity")).toBeInTheDocument();
  });

  it("renders the transaction history table from a top-level array envelope", () => {
    render(<CustodialWalletDetailPage />);

    const section = screen.getByRole("heading", { name: "Transaction history" }).closest("section");
    expect(section).not.toBeNull();
    const scope = within(section as HTMLElement);

    expect(scope.getByText("txn-1")).toBeInTheDocument();
    expect(scope.getByText("txn-2")).toBeInTheDocument();
    expect(scope.getByText("CREDIT")).toBeInTheDocument();
    expect(scope.getByText("DEBIT")).toBeInTheDocument();
    expect(scope.getByText("REF-001")).toBeInTheDocument();
    expect(scope.getByText("REF-002")).toBeInTheDocument();
  });

  it("renders the linked card profiles table from a `{ data: [] }` envelope", () => {
    render(<CustodialWalletDetailPage />);

    const section = screen.getByRole("heading", { name: "Linked card profiles" }).closest("section");
    expect(section).not.toBeNull();
    const scope = within(section as HTMLElement);

    expect(scope.getByText("card-1")).toBeInTheDocument();
    expect(scope.getByText("VISA")).toBeInTheDocument();
    expect(scope.getByText("**** 1234")).toBeInTheDocument();
    expect(scope.getByText("ACTIVE")).toBeInTheDocument();
  });

  it("documents the BFF routes used by both tables (no hidden endpoints)", () => {
    render(<CustodialWalletDetailPage />);

    expect(
      screen.getByText(
        "/internal/v1/finance/mushex-platform/wallets/wallet-abc-123/transactions",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "/internal/v1/finance/mushex-platform/card-profiles?walletId=wallet-abc-123",
      ),
    ).toBeInTheDocument();
  });

  it("renders no write-action buttons on this page", () => {
    render(<CustodialWalletDetailPage />);

    const buttons = screen.queryAllByRole("button");
    for (const button of buttons) {
      const name = (button.textContent || "").toLowerCase();
      expect(name).not.toMatch(/create|credit|debit|issue|reverse|refund|approve|block|cancel/);
    }
  });
});

describe("CustodialWalletDetailPage — empty + error states", () => {
  it("does not fabricate transactions or card rows when payloads are empty", async () => {
    vi.resetModules();
    vi.doMock("next/navigation", () => ({
      useParams: () => ({ walletId: "wallet-empty-1" }),
    }));
    vi.doMock("@/hooks/queries/useMushexPlatformAdmin", async () => {
      const actual = await vi.importActual<
        typeof import("@/hooks/queries/useMushexPlatformAdmin")
      >("@/hooks/queries/useMushexPlatformAdmin");
      return {
        ...actual,
        useMushexPlatformWalletTransactions: () => ({
          data: [],
          isLoading: false,
          isError: false,
        }),
        useMushexPlatformCardProfilesForWallet: () => ({
          data: [],
          isLoading: false,
          isError: false,
        }),
      };
    });
    const { default: Page } = await import("./page");
    render(<Page />);

    expect(screen.getByText("No transactions returned for this wallet.")).toBeInTheDocument();
    expect(screen.getByText("No card profiles linked to this wallet.")).toBeInTheDocument();
  });

  it("renders honest 'could not load' messages when both endpoints error", async () => {
    vi.resetModules();
    vi.doMock("next/navigation", () => ({
      useParams: () => ({ walletId: "wallet-err-1" }),
    }));
    vi.doMock("@/hooks/queries/useMushexPlatformAdmin", async () => {
      const actual = await vi.importActual<
        typeof import("@/hooks/queries/useMushexPlatformAdmin")
      >("@/hooks/queries/useMushexPlatformAdmin");
      return {
        ...actual,
        useMushexPlatformWalletTransactions: () => ({
          data: undefined,
          isLoading: false,
          isError: true,
          error: new Error("503 Service Unavailable"),
        }),
        useMushexPlatformCardProfilesForWallet: () => ({
          data: undefined,
          isLoading: false,
          isError: true,
          error: new Error("503 Service Unavailable"),
        }),
      };
    });
    const { default: Page } = await import("./page");
    render(<Page />);

    expect(
      screen.getByText(/Could not load transactions for this wallet from MusheX/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Could not load card profiles for this wallet from MusheX/),
    ).toBeInTheDocument();
  });
});
