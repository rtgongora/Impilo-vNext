import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import WalletPaymentsPage from "./page";

const { walletState } = vi.hoisted(() => ({
  walletState: {
    data: undefined as { data: { payments: Record<string, unknown> } } | undefined,
    isLoading: false,
    isError: false,
  },
}));

vi.mock("@/hooks/queries/useWallet", () => ({
  useWalletOverview: () => walletState,
}));

describe("WalletPaymentsPage", () => {
  beforeEach(() => {
    walletState.data = undefined;
    walletState.isLoading = false;
    walletState.isError = false;
  });

  it("shows honest unavailable state when billing card degrades", () => {
    walletState.data = { data: { payments: { unavailable: true, outstanding: null } } };
    render(<WalletPaymentsPage />);
    expect(screen.getByText(/Billing is temporarily unavailable/i)).toBeInTheDocument();
  });

  it("shows the coverage split — coverage pays vs your share", () => {
    walletState.data = {
      data: {
        payments: {
          _source: "costa (billing) + mushex (payments)",
          outstanding: {
            items: [
              {
                billId: "BILL-9",
                billType: "ENCOUNTER",
                status: "FINAL",
                currency: "USD",
                totalPayable: 120,
                insurerPayable: 80,
                patientPayable: 40,
                coverageStatus: "APPLIED",
              },
            ],
          },
        },
      },
    };
    render(<WalletPaymentsPage />);
    expect(screen.getByText(/USD 120.00/)).toBeInTheDocument();
    expect(screen.getByText("Coverage pays")).toBeInTheDocument();
    expect(screen.getByText("USD 80.00")).toBeInTheDocument();
    expect(screen.getByText("You pay")).toBeInTheDocument();
    expect(screen.getByText("USD 40.00")).toBeInTheDocument();
    expect(screen.getByText("Ready to settle")).toBeInTheDocument();
    // The Pay action must route to the real finance surface, never fake success here.
    expect(screen.getByRole("link", { name: "Pay" })).toHaveAttribute("href", "/finance");
  });

  it("shows an empty state when there are no outstanding bills", () => {
    walletState.data = { data: { payments: { outstanding: { items: [] } } } };
    render(<WalletPaymentsPage />);
    expect(screen.getByText(/You have no outstanding bills/i)).toBeInTheDocument();
  });
});
