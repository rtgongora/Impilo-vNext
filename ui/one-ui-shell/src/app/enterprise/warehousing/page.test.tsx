import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import EnterpriseWarehousingPage from "./page";

vi.mock("@/components/enterprise/EnterpriseWorkspaceShell", () => ({
  EnterpriseWorkspaceShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/components/FeatureMaturityBadge", () => ({
  FeatureMaturityBadge: () => <span>partial</span>,
}));

const mockUseFacilityStore = vi.fn();

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (s: { facility: { id: string; name: string } | null }) => unknown) =>
    mockUseFacilityStore(selector),
}));

vi.mock("@/hooks/queries/useInventory", () => ({
  useInventoryOnHand: () => ({
    data: { data: [{ id: "1" }, { id: "2" }] },
    isLoading: false,
    error: null,
  }),
  useInventoryStockouts: () => ({
    data: { data: [{ id: "s1" }] },
    isLoading: false,
    error: null,
  }),
  useInventoryNearExpiry: () => ({
    data: { data: [] },
    isLoading: false,
    error: null,
  }),
  useInventoryMovements: () => ({
    data: [
      {
        id: "m1",
        movedAt: "2026-06-01T10:00:00Z",
        itemName: "Gloves",
        fromLocation: "Store A",
        toLocation: "Ward B",
        quantity: 10,
        reason: "Transfer",
        performedBy: "user-1",
      },
    ],
    isLoading: false,
    isError: false,
  }),
}));

describe("EnterpriseWarehousingPage", () => {
  beforeEach(() => {
    mockUseFacilityStore.mockImplementation((selector) =>
      selector({ facility: { id: "fac-1", name: "Central" } }),
    );
  });

  it("renders inventory-backed warehouse metrics and movement rows", () => {
    render(<EnterpriseWarehousingPage />);
    expect(screen.getByText("Warehousing & distribution")).toBeInTheDocument();
    expect(screen.getByText("On-hand SKUs")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("Gloves")).toBeInTheDocument();
    expect(screen.getByText(/What is intentionally not fabricated/i)).toBeInTheDocument();
  });

  it("shows facility context banner when no facility is selected", () => {
    mockUseFacilityStore.mockImplementation((selector) => selector({ facility: null }));
    render(<EnterpriseWarehousingPage />);
    expect(screen.getByText("Facility context required")).toBeInTheDocument();
  });
});
