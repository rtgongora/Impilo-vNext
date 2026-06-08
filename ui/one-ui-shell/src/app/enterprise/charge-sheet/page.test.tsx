import type { ReactNode } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import EnterpriseChargeSheetPage from "./page";

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

vi.mock("@/hooks/queries/useCostaIntel", () => ({
  useCostaIntelTariffLists: () => ({
    data: [
      {
        id: 1,
        name: "National tariff 2026",
        externalCode: "NAT-2026",
        tariffFamily: "NATIONAL",
        tariffType: "SERVICE",
        priceBasis: "FLAT",
        officialStatus: "APPROVED",
        validationStatus: "VALID",
        approvedForBilling: true,
        referenceOnly: false,
        currency: "USD",
      },
    ],
    isLoading: false,
    isError: false,
  }),
}));

const mockUseFinanceBillingCharges = vi.fn();

vi.mock("@/hooks/queries/useFinanceBillingWorkspace", () => ({
  useFinanceBillingCharges: (...args: unknown[]) => mockUseFinanceBillingCharges(...args),
}));

vi.mock("@/hooks/queries/useInventory", () => ({
  useInventoryOnHand: () => ({ data: [], isLoading: false, isError: false }),
}));

describe("EnterpriseChargeSheetPage", () => {
  it("renders tariff rows from COSTA intel hook", () => {
    mockUseFinanceBillingCharges.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
    });
    render(<EnterpriseChargeSheetPage />);
    expect(screen.getByText("Charge sheet")).toBeInTheDocument();
    expect(screen.getByText("National tariff 2026")).toBeInTheDocument();
    expect(screen.getByText("NAT-2026")).toBeInTheDocument();
  });

  it("loads billing charges when bill id is submitted", () => {
    mockUseFinanceBillingCharges.mockReturnValue({
      data: { data: [{ id: "c1", description: "Consultation", amount: "50.00", status: "POSTED" }] },
      isLoading: false,
      isError: false,
    });
    render(<EnterpriseChargeSheetPage />);
    fireEvent.change(screen.getByPlaceholderText("bill-uuid or bill reference"), {
      target: { value: "bill-123" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Load charges" }));
    expect(mockUseFinanceBillingCharges).toHaveBeenCalledWith("bill-123", true);
    expect(screen.getByText("Consultation")).toBeInTheDocument();
  });
});
