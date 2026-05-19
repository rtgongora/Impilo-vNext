import type { ReactNode } from "react";
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import CostaEncounterTimelinePage from "./page";

/**
 * Phase 5 — read-only COSTA encounter timeline tests.
 *
 * Covers:
 *   1) header + identity card render with the encounter id;
 *   2) decisions, cost events, invoices, intents, settlements merge into one chronologically-sorted table;
 *   3) rows without a timestamp sink to the bottom (no fabricated timestamps);
 *   4) empty payload renders an honest "no rows" message;
 *   5) both endpoints in error state render the documented "could not load" copy;
 *   6) no write-action buttons exist;
 *   7) source-list fan-in intent rows are surfaced;
 *   8) settlement-filtered linkage rows are surfaced.
 */

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
  useParams: () => ({ encounterId: "enc-001" }),
  useSearchParams: () => ({
    get: (key: string) => (key === "patientId" ? "pat-123" : null),
  }),
}));

interface QueryShape<T> {
  data: T;
  isLoading?: boolean;
  isFetching?: boolean;
  isError?: boolean;
  error?: unknown;
}

vi.mock("@/hooks/queries/useServiceAccessDecisions", () => ({
  useServiceAccessDecisionsList: (): QueryShape<unknown[]> => ({
    data: [
      {
        service_access_decision_id: "decision-1",
        access_status: "GRANTED",
        billing_timing_mode: "POST_SERVICE",
        encounter_id: "enc-001",
        decision_timestamp: "2026-05-10T09:00:00Z",
        estimated_cost: 150.0,
        requested_service_name: "Consultation",
      },
      {
        service_access_decision_id: "decision-2",
        access_status: "DEFERRED",
        encounter_id: "enc-001",
        decision_timestamp: null,
        requested_service_name: "MRI scan",
      },
    ],
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/hooks/queries/useCostaIntel", () => ({
  useCostaIntelCostEvents: (): QueryShape<unknown[]> => ({
    data: [
      {
        id: "cost-event-1",
        service_name: "Consultation fee",
        captured_at: "2026-05-10T09:30:00Z",
        total_amount: "150.00",
      },
    ],
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/hooks/queries/useFinanceBillingWorkspace", () => ({
  useFinanceBillingInvoicesForEncounter: (): QueryShape<{ data: unknown[] }> => ({
    data: {
      data: [
        {
          invoice_id: "inv-1",
          mushex_intent_id: "intent-1",
          payment_status: "PENDING",
          invoice: {
            invoiceId: "inv-1",
            invoiceNumber: "INV-001",
            invoiceStatus: "ISSUED",
            issuedAt: "2026-05-10T10:00:00Z",
            total: "150.00",
          },
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
  useFinanceBillingSubsidiesForEncounter: (): QueryShape<{ data: unknown[] }> => ({
    data: {
      data: [
        {
          bill_id: "BILL-1",
          subsidy_amount: "25.00",
          write_off_amount: "5.00",
          bill_status: "FINALIZED",
          finalized_at: "2026-05-10T10:08:00Z",
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/hooks/queries/useFinancePayerOps", () => ({
  usePayerOpsPaymentIntentsBySource: (): QueryShape<{ data: unknown[] }> => ({
    data: {
      data: [
        {
          intentId: "intent-1",
          sourceType: "COSTA_BILL",
          sourceId: "BILL-1",
          status: "PENDING",
          amountTotal: "150.00",
          createdAt: "2026-05-10T10:05:00Z",
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/hooks/queries/useFinanceSettlements", () => ({
  useFinanceSettlementsByIntentIds: (): QueryShape<{ data: unknown[] }> => ({
    data: {
      data: [
        {
          settlementId: "SET-1",
          status: "COMPUTED",
          createdAt: "2026-05-10T10:10:00Z",
          totals: "{\"intentCount\":1,\"totalPaid\":\"150.00\"}",
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
}));

describe("CostaEncounterTimelinePage", () => {
  it("renders the page title and quotes the encounter id in the subtitle and identity card", () => {
    render(<CostaEncounterTimelinePage />);
    expect(screen.getByRole("heading", { name: "COSTA encounter timeline" })).toBeInTheDocument();
    expect(screen.getByTestId("subtitle").textContent).toContain("enc-001");
    const identitySection = screen.getByText("Encounter context").closest("section");
    expect(identitySection).not.toBeNull();
    expect(within(identitySection as HTMLElement).getByText("enc-001")).toBeInTheDocument();
    expect(within(identitySection as HTMLElement).getByText("pat-123")).toBeInTheDocument();
  });

  it("merges decisions, cost events, invoices, intents, settlements, and subsidy rows into one chronological table, sinking null-timestamp rows", () => {
    render(<CostaEncounterTimelinePage />);

    const section = screen
      .getByRole("heading", { name: /Service-access decisions & charge sheet events/i })
      .closest("section");
    expect(section).not.toBeNull();
    const rows = within(section as HTMLElement).getAllByRole("row");
    // 1 header + 7 body rows (2 decisions + 1 cost event + 1 invoice + 1 intent + 1 subsidy + 1 settlement)
    expect(rows.length).toBe(8);

    // Body rows in chronological order: decision-1 -> cost-event -> invoice -> intent -> subsidy -> settlement -> null timestamp decision.
    const bodyTexts = rows.slice(1).map((r) => r.textContent ?? "");
    expect(bodyTexts[0]).toMatch(/09:00[\s\S]*Decision[\s\S]*Consultation[\s\S]*150[\s\S]*GRANTED/);
    expect(bodyTexts[1]).toMatch(/09:30[\s\S]*Cost event[\s\S]*Consultation fee[\s\S]*150\.00[\s\S]*CAPTURED/);
    expect(bodyTexts[2]).toMatch(/10:00[\s\S]*Invoice[\s\S]*INV-001[\s\S]*150\.00[\s\S]*ISSUED/);
    expect(bodyTexts[3]).toMatch(/10:05[\s\S]*Intent[\s\S]*intent-1[\s\S]*150\.00[\s\S]*PENDING/);
    expect(bodyTexts[4]).toMatch(/10:08[\s\S]*Subsidy[\s\S]*BILL-1[\s\S]*25\.00[\s\S]*SUBSIDY/);
    expect(bodyTexts[5]).toMatch(/10:10[\s\S]*Settlement[\s\S]*SET-1[\s\S]*COMPUTED/);
    expect(bodyTexts[6]).toMatch(/—[\s\S]*Decision[\s\S]*MRI scan[\s\S]*DEFERRED/);
  });

  it("renders the BFF routes used by both timeline sources (no hidden endpoints)", () => {
    render(<CostaEncounterTimelinePage />);
    const section = screen
      .getByRole("heading", { name: /Service-access decisions & charge sheet events/i })
      .closest("section");
    expect(section).not.toBeNull();
    const scope = within(section as HTMLElement);
    expect(
      scope.getByText("/internal/v1/finance/service-access-decisions?encounter_id=…"),
    ).toBeInTheDocument();
    expect(
      scope.getByText("/internal/v1/finance/costa-intel/cost-events?encounter_id=…"),
    ).toBeInTheDocument();
    expect(
      scope.getByText("/internal/v1/finance/billing-workspace/lifecycle/invoices?encounterId=…"),
    ).toBeInTheDocument();
    expect(
      scope.getByText("/internal/v1/finance/payer-ops/payment-intents?sourceType=COSTA_BILL&sourceIds=…"),
    ).toBeInTheDocument();
    expect(scope.getByText("/internal/v1/finance/settlements?intentIds=…")).toBeInTheDocument();
    expect(
      scope.getByText("/internal/v1/finance/billing-workspace/lifecycle/subsidies?encounterId=…"),
    ).toBeInTheDocument();
  });

  it("renders the gap card naming settlement detail granularity as the remaining timeline gap", () => {
    render(<CostaEncounterTimelinePage />);
    expect(screen.getByText(/Invoices, payments, and settlement/)).toBeInTheDocument();
    expect(screen.getByText(/Invoice and intent linkage/)).toBeInTheDocument();
    expect(screen.getByText(/Settlement detail granularity/)).toBeInTheDocument();
  });

  it("renders no write-action buttons on this page", () => {
    render(<CostaEncounterTimelinePage />);
    const buttons = screen.queryAllByRole("button");
    for (const button of buttons) {
      const name = (button.textContent || "").toLowerCase();
      expect(name).not.toMatch(/create|credit|debit|issue|reverse|refund|approve|block|cancel|submit/);
    }
  });
});

describe("CostaEncounterTimelinePage — empty + error states", () => {
  it("renders an honest 'no rows' state when both endpoints return empty", async () => {
    vi.resetModules();
    vi.doMock("next/navigation", () => ({
      useParams: () => ({ encounterId: "enc-empty" }),
      useSearchParams: () => ({ get: () => null }),
    }));
    vi.doMock("@/hooks/queries/useServiceAccessDecisions", () => ({
      useServiceAccessDecisionsList: () => ({ data: [], isLoading: false, isError: false }),
    }));
    vi.doMock("@/hooks/queries/useCostaIntel", () => ({
      useCostaIntelCostEvents: () => ({ data: [], isLoading: false, isError: false }),
    }));
    vi.doMock("@/hooks/queries/useFinanceBillingWorkspace", () => ({
      useFinanceBillingInvoicesForEncounter: () => ({ data: [], isLoading: false, isError: false }),
      useFinanceBillingSubsidiesForEncounter: () => ({ data: [], isLoading: false, isError: false }),
    }));
    vi.doMock("@/hooks/queries/useFinancePayerOps", () => ({
      usePayerOpsPaymentIntentsBySource: () => ({ data: [], isLoading: false, isError: false }),
    }));
    vi.doMock("@/hooks/queries/useFinanceSettlements", () => ({
      useFinanceSettlementsByIntentIds: () => ({ data: [], isLoading: false, isError: false }),
    }));
    const { default: Page } = await import("./page");
    render(<Page />);
    expect(
      screen.getByText(
        "No service-access decisions, cost events, invoices, intents, settlements, or subsidy rows returned for this encounter.",
      ),
    ).toBeInTheDocument();
  });

  it("renders an honest error state when either endpoint errors", async () => {
    vi.resetModules();
    vi.doMock("next/navigation", () => ({
      useParams: () => ({ encounterId: "enc-error" }),
      useSearchParams: () => ({ get: () => null }),
    }));
    vi.doMock("@/hooks/queries/useServiceAccessDecisions", () => ({
      useServiceAccessDecisionsList: () => ({
        data: undefined,
        isLoading: false,
        isError: true,
        error: new Error("503"),
      }),
    }));
    vi.doMock("@/hooks/queries/useCostaIntel", () => ({
      useCostaIntelCostEvents: () => ({ data: [], isLoading: false, isError: false }),
    }));
    vi.doMock("@/hooks/queries/useFinanceBillingWorkspace", () => ({
      useFinanceBillingInvoicesForEncounter: () => ({ data: [], isLoading: false, isError: false }),
      useFinanceBillingSubsidiesForEncounter: () => ({ data: [], isLoading: false, isError: false }),
    }));
    vi.doMock("@/hooks/queries/useFinancePayerOps", () => ({
      usePayerOpsPaymentIntentsBySource: () => ({ data: [], isLoading: false, isError: false }),
    }));
    vi.doMock("@/hooks/queries/useFinanceSettlements", () => ({
      useFinanceSettlementsByIntentIds: () => ({ data: [], isLoading: false, isError: false }),
    }));
    const { default: Page } = await import("./page");
    render(<Page />);
    expect(
      screen.getByText(/Could not load one or both timeline sources from COSTA/),
    ).toBeInTheDocument();
  });
});
