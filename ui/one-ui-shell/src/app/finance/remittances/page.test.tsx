import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import FinanceRemittancesPage from "./page";

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

vi.mock("@/hooks/queries/useCoverage", () => ({
  useCoverageRemittances: () => ({
    data: [
      {
        id: "rem-1",
        remittanceNumber: "REM-100",
        coverageId: "cov-1",
        amount: 25,
        currency: "USD",
        status: "SETTLED",
        remittedAt: "2026-04-12T09:00:00Z",
      },
    ],
    isLoading: false,
    isError: false,
  }),
}));

describe("FinanceRemittancesPage", () => {
  it("renders remittance rows from coverage remittances feed", () => {
    render(<FinanceRemittancesPage />);
    expect(screen.getByRole("heading", { level: 1, name: "Remittances" })).toBeInTheDocument();
    expect(screen.getByText("REM-100")).toBeInTheDocument();
    expect(screen.getByText("cov-1")).toBeInTheDocument();
    expect(screen.getByText("USD 25.00")).toBeInTheDocument();
  });
});
