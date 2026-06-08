import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EnterpriseResourceDashboard } from "./EnterpriseResourceDashboard";

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

const mockUseFacilityStore = vi.fn();
const mockUseAuthStore = vi.fn();

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (s: { facility: { id: string } | null }) => unknown) => mockUseFacilityStore(selector),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => mockUseAuthStore(),
}));

vi.mock("@/hooks/queries/useInventory", () => ({
  useInventoryStockouts: () => ({
    data: { data: [{ id: "1" }, { id: "2" }, { id: "3" }] } as unknown,
    isLoading: false,
    error: null,
  }),
  useInventoryNearExpiry: () => ({ data: { data: [] } as unknown, isLoading: false, error: null }),
  useInventoryReconcilePending: () => ({
    data: { data: [{ id: "r1" }, { id: "r2" }] } as unknown,
    isLoading: false,
    error: null,
  }),
}));

vi.mock("@/hooks/queries/useProcurement", () => ({
  useProcRequisitions: () => ({ data: { data: [{ id: "q1" }] } as unknown, isLoading: false, error: null }),
  useProcPurchaseOrders: () => ({ data: { data: [{ id: "po1" }] } as unknown, isLoading: false, error: null }),
}));

vi.mock("@/hooks/queries/useAssets", () => ({
  useAssets: () => ({ data: { data: [] } as unknown, isLoading: false, error: null }),
}));

vi.mock("@/hooks/queries/useFacilities", () => ({
  useFacilities: () => ({ data: { data: [{ id: "fac-1", attributes: { province: "Harare", district: "Harare" } }] }, isLoading: false, error: null }),
}));

vi.mock("@/hooks/queries/useFinancialReports", () => ({
  useRevenueSummary: () => ({ data: { total_revenue: 12000 }, isLoading: false, error: null }),
}));

vi.mock("@/hooks/queries/useMushexPlatformAdmin", () => ({
  extractCount: () => 2,
  useMushexPlatformRemittanceTransfers: () => ({ data: { items: [{}, {}] }, isLoading: false, error: null }),
}));

describe("EnterpriseResourceDashboard", () => {
  beforeEach(() => {
    mockUseFacilityStore.mockImplementation((selector) => selector({ facility: { id: "fac-1" } }));
    mockUseAuthStore.mockReturnValue({
      hasRole: (r: string) => ["SYSTEM_ADMIN", "FINANCE", "PHARMACIST"].includes(r),
    });
  });

  it("renders API-backed inventory tiles with numeric counts", () => {
    render(<EnterpriseResourceDashboard />);
    expect(screen.getByText("Stockouts (on-hand)")).toBeInTheDocument();
    expect(screen.getAllByText("3").length).toBeGreaterThan(0);
    expect(screen.getByText("Reconciliation queue")).toBeInTheDocument();
    expect(screen.getAllByText("2").length).toBeGreaterThan(0);
    expect(screen.getByText("Procurement requisitions")).toBeInTheDocument();
    expect(screen.getByText("Purchase orders")).toBeInTheDocument();
  });

  it("shows facility context banner when no facility is selected", () => {
    mockUseFacilityStore.mockImplementation((selector) => selector({ facility: null }));
    mockUseAuthStore.mockReturnValue({
      hasRole: () => false,
    });

    render(<EnterpriseResourceDashboard />);
    expect(screen.getByText("Facility context required")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Select facility" })).toHaveAttribute("href", "/facility");
  });

  it("renders revenue and cold-chain tiles for finance roles", () => {
    render(<EnterpriseResourceDashboard />);
    expect(screen.getByText("Revenue (month)")).toBeInTheDocument();
    expect(screen.getByText("Cold-chain risk (30d)")).toBeInTheDocument();
    expect(screen.getByText("MusheX remittances")).toBeInTheDocument();
  });
});
