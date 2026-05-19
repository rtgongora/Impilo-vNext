import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ReversalDetailPage from "./page";

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
  useParams: () => ({ reversalId: "rev-abc-123" }),
}));

vi.mock("@/hooks/queries/useMushexPlatformAdmin", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useMushexPlatformAdmin")>(
    "@/hooks/queries/useMushexPlatformAdmin",
  );
  return {
    ...actual,
    useMushexPlatformReversalById: () => ({
      data: {
        data: {
          reversalId: "rev-abc-123",
          reversalCode: "REV-001",
          originalTxnRef: "TXN-77",
          amount: "45.00",
          currency: "USD",
          reversalStatus: "INITIATED",
        },
      },
      isLoading: false,
      isError: false,
    }),
  };
});

describe("ReversalDetailPage", () => {
  it("renders reversal detail fields", () => {
    render(<ReversalDetailPage />);
    expect(screen.getByRole("heading", { name: "Reversal record" })).toBeInTheDocument();
    expect(screen.getByText("REV-001")).toBeInTheDocument();
    expect(screen.getByText("TXN-77")).toBeInTheDocument();
    expect(screen.getByText("INITIATED")).toBeInTheDocument();
  });

  it("documents the BFF per-id reversal route", () => {
    render(<ReversalDetailPage />);
    expect(
      screen.getByText("/internal/v1/finance/mushex-platform/reversals/rev-abc-123"),
    ).toBeInTheDocument();
  });
});

