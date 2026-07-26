import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { EncounterCareChainRail } from "./EncounterCareChainRail";

/**
 * experience-bff empty-200 honesty sweep — UI half.
 *
 * Every number on this rail is a claim: nothing ordered on this encounter, nothing billed,
 * nothing paid. The chain is cascaded, so one failed read used to yield three confident zeros.
 */

const orders = vi.fn(() => ({ data: { data: [] as unknown[] }, isLoading: false, isError: false }));
const costEvents = vi.fn(() => ({ data: [] as unknown[], isLoading: false, isError: false }));
const invoices = vi.fn(() => ({ data: { data: [] as unknown[] }, isLoading: false, isError: false }));

vi.mock("@/hooks/queries/useLabOrders", () => ({ useLabOrders: () => orders() }));
vi.mock("@/hooks/queries/useCostaIntel", () => ({ useCostaIntelCostEvents: () => costEvents() }));
vi.mock("@/hooks/queries/useFinanceBillingWorkspace", () => ({
  useFinanceBillingInvoicesForEncounter: () => invoices(),
}));
vi.mock("@/hooks/queries/useFinancePayerOps", () => ({
  usePayerOpsPaymentIntentsBySource: () => ({ data: { data: [] }, isLoading: false, isError: false }),
}));
vi.mock("@/hooks/queries/useFinanceSettlements", () => ({
  useFinanceSettlementsByIntentIds: () => ({ data: { data: [] }, isLoading: false, isError: false }),
}));

describe("EncounterCareChainRail honesty", () => {
  it("shows a real zero when every source answered", () => {
    render(<EncounterCareChainRail encounterId="enc-1" patientId="pat-1" />);

    expect(screen.getByTestId("encounter-care-chain-rail")).toBeInTheDocument();
    expect(screen.getAllByText("0")).toHaveLength(3);
  });

  it("shows an unknown count rather than zero when OROS could not be read", () => {
    orders.mockReturnValueOnce({ data: undefined as never, isLoading: false, isError: true });

    render(<EncounterCareChainRail encounterId="enc-1" patientId="pat-1" />);

    expect(screen.getByText(/order count unknown, not zero/i)).toBeInTheDocument();
    expect(screen.getAllByText("0")).toHaveLength(2);
  });

  it("does not report zero payments when the invoice read failed one link up the chain", () => {
    invoices.mockReturnValueOnce({ data: undefined as never, isLoading: false, isError: true });

    render(<EncounterCareChainRail encounterId="enc-1" patientId="pat-1" />);

    expect(screen.getByText(/cost events and invoice rows unknown, not zero/i)).toBeInTheDocument();
    expect(screen.getByText(/payment intents and settlements unknown, not zero/i)).toBeInTheDocument();
  });
});
