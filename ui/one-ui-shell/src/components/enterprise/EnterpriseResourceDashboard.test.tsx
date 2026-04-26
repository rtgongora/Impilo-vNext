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
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("Reconciliation queue")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
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

  it("documents that high-level revenue and fleet KPIs are not fabricated", () => {
    render(<EnterpriseResourceDashboard />);
    expect(screen.getByText(/What is intentionally not fabricated/i)).toBeInTheDocument();
    expect(screen.getByText(/reporting-service/i)).toBeInTheDocument();
  });
});
