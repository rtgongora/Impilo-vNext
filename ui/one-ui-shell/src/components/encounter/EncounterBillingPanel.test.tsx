import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { EncounterBillingPanel } from "./EncounterBillingPanel";
import type { EncounterBillingData } from "@/hooks/queries/useEncounterBilling";

const { billingState } = vi.hoisted(() => ({
  billingState: {
    data: undefined as { data: EncounterBillingData } | undefined,
    isLoading: false,
    isError: false,
  },
}));

vi.mock("@/hooks/queries/useEncounterBilling", () => ({
  useEncounterBilling: () => billingState,
}));

function bill(overrides: Record<string, unknown> = {}) {
  return {
    bill_id: "BILL-1",
    status: "FINAL",
    bill_type: "ENCOUNTER",
    currency: "USD",
    total_charge: 120,
    total_payable: 120,
    insurer_payable: 80,
    patient_payable: 40,
    subsidy_payable: 0,
    write_off: 0,
    coverage_status: "APPLIED",
    coverage_plan_code: "PLAN-A",
    created_at: "2026-07-01T10:00:00Z",
    finalized_at: "2026-07-01T11:00:00Z",
    payments: [],
    state: "SHORTFALL_DUE",
    ...overrides,
  };
}

describe("EncounterBillingPanel", () => {
  beforeEach(() => {
    billingState.data = undefined;
    billingState.isLoading = false;
    billingState.isError = false;
  });

  it("shows an honest empty state when no bill exists", () => {
    billingState.data = {
      data: { encounter_id: "enc-1", billing_state: "NOT_BILLED", bills: [] },
    };
    render(<EncounterBillingPanel patientId="patient-1" encounterId="enc-1" />);
    expect(screen.getByText(/No bill exists for this encounter yet/i)).toBeInTheDocument();
    expect(screen.getByText("Not billed")).toBeInTheDocument();
  });

  it("shows shortfall due with coverage split and finance link", () => {
    billingState.data = {
      data: {
        encounter_id: "enc-1",
        billing_state: "SHORTFALL_DUE",
        bills: [bill()],
      },
    };
    render(<EncounterBillingPanel patientId="patient-1" encounterId="enc-1" />);
    // Overall badge + per-bill badge both say shortfall due.
    expect(screen.getAllByText("Shortfall due").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("USD 40.00")).toBeInTheDocument();
    expect(screen.getByText("USD 80.00")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: /open bill/i });
    expect(link).toHaveAttribute(
      "href",
      "/finance/billing/BILL-1?patientId=patient-1&encounterId=enc-1",
    );
  });

  it("never claims payment success without paid data (pending stays pending)", () => {
    billingState.data = {
      data: {
        encounter_id: "enc-1",
        billing_state: "PAYMENT_PENDING",
        bills: [
          bill({
            state: "PAYMENT_PENDING",
            payments: [
              {
                id: "1",
                payment_type: "REMAINDER",
                amount: 40,
                paid_amount: 0,
                status: "PENDING",
                mushex_intent_id: "intent-1",
                paid_at: null,
              },
            ],
          }),
        ],
      },
    };
    render(<EncounterBillingPanel patientId="patient-1" encounterId="enc-1" />);
    expect(screen.getAllByText("Payment pending").length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText(/^Paid$/)).not.toBeInTheDocument();
    expect(screen.getByText(/REMAINDER PENDING/)).toBeInTheDocument();
  });

  it("fails closed with an unavailable message on error", () => {
    billingState.isError = true;
    render(<EncounterBillingPanel patientId="patient-1" encounterId="enc-1" />);
    expect(
      screen.getByText(/Billing status is currently unavailable/i),
    ).toBeInTheDocument();
  });
});
