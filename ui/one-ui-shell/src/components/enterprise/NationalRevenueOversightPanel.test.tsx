import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { NationalRevenueOversightPanel } from "./NationalRevenueOversightPanel";

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

const mockUseRevenueSummary = vi.fn();
const mockUseClaimsExpenditure = vi.fn();
const mockUseDebtAging = vi.fn();
const mockUseMushexPlatformRemittanceTransfers = vi.fn();

vi.mock("@/hooks/queries/useFinancialReports", () => ({
  useRevenueSummary: (...args: unknown[]) => mockUseRevenueSummary(...args),
  useClaimsExpenditure: (...args: unknown[]) => mockUseClaimsExpenditure(...args),
  useDebtAging: (...args: unknown[]) => mockUseDebtAging(...args),
}));

vi.mock("@/hooks/queries/useMushexPlatformAdmin", () => ({
  extractCount: (payload: unknown) => {
    if (payload && typeof payload === "object" && Array.isArray((payload as { items?: unknown[] }).items)) {
      return (payload as { items: unknown[] }).items.length;
    }
    return null;
  },
  useMushexPlatformRemittanceTransfers: () => mockUseMushexPlatformRemittanceTransfers(),
}));

describe("NationalRevenueOversightPanel", () => {
  beforeEach(() => {
    mockUseRevenueSummary.mockReturnValue({
      data: {
        totalRevenue: 250000,
        encounterCount: 42,
        byPayerType: [{ bucket: "INSURANCE", total: 180000 }],
      },
      isLoading: false,
      isError: false,
      error: null,
    });
    mockUseClaimsExpenditure.mockReturnValue({
      data: { claimsExposure: 95000, lossRatio: 0.12 },
      isLoading: false,
      isError: false,
      error: null,
    });
    mockUseMushexPlatformRemittanceTransfers.mockReturnValue({
      data: { items: [{}, {}] },
      isLoading: false,
      isError: false,
      error: null,
    });
    mockUseDebtAging.mockReturnValue({
      data: { writeOffTotal: 1500, agingBuckets: [{ bucket: "0-30", outstanding: 5000 }] },
      isLoading: false,
      isError: false,
      error: null,
    });
  });

  it("calls national hooks without facility or payer filters", () => {
    render(<NationalRevenueOversightPanel />);
    expect(mockUseRevenueSummary).toHaveBeenCalledWith(undefined, expect.any(Number), expect.any(Number));
    expect(mockUseClaimsExpenditure).toHaveBeenCalledWith(undefined, expect.any(Number), expect.any(Number));
    expect(mockUseDebtAging).toHaveBeenCalledWith(undefined);
  });

  it("renders API-backed revenue and claims KPIs", () => {
    render(<NationalRevenueOversightPanel />);
    expect(screen.getByTestId("national-revenue-oversight-panel")).toBeInTheDocument();
    expect(screen.getByText("National revenue oversight")).toBeInTheDocument();
    expect(screen.getByText("42 encounters")).toBeInTheDocument();
    expect(screen.getByText("1 payer type buckets")).toBeInTheDocument();
    expect(screen.getByText("INSURANCE")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("shows honest error states without synthetic KPIs", () => {
    mockUseRevenueSummary.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: { error: { message: "COSTA unavailable" } },
    });
    mockUseClaimsExpenditure.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: { error: { message: "Claims report failed" } },
    });

    render(<NationalRevenueOversightPanel />);
    expect(screen.getByText("COSTA unavailable")).toBeInTheDocument();
    expect(screen.getByText("Claims report failed")).toBeInTheDocument();
    expect(screen.queryByText("42 encounters")).not.toBeInTheDocument();
  });
});
