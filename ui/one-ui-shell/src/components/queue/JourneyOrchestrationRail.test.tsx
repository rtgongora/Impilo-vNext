import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { JourneyOrchestrationRail } from "./JourneyOrchestrationRail";

const { useCoreTransactionDetail, useApplyCoreTransactionAction } = vi.hoisted(() => ({
  useCoreTransactionDetail: vi.fn(),
  useApplyCoreTransactionAction: vi.fn(),
}));

vi.mock("@/hooks/queries/useCoreTransactionExperience", () => ({
  useCoreTransactionDetail,
  useApplyCoreTransactionAction,
}));

vi.mock("@/features/core-transaction/components", () => ({
  JourneyNextActionPanel: () => <div>Journey next actions</div>,
  TransactionStateBadge: ({ state }: { state: string }) => <span>{state}</span>,
  TransactionTypeBadge: ({ type }: { type: string }) => <span>{type}</span>,
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

describe("JourneyOrchestrationRail", () => {
  beforeEach(() => {
    useApplyCoreTransactionAction.mockReturnValue({ mutate: vi.fn(), isPending: false });
  });

  it("shows loading state while journey transaction resolves", () => {
    useCoreTransactionDetail.mockReturnValue({ data: null, isLoading: true, isError: false });
    render(<JourneyOrchestrationRail transactionId="journey-42" patientId="patient-1" journeyId="42" />);
    expect(screen.getByText(/Loading walk-in journey transaction/)).toBeInTheDocument();
  });

  it("renders linked journey transaction with next actions", () => {
    useCoreTransactionDetail.mockReturnValue({
      data: {
        transaction: { id: "journey-42", type: "FACILITY_WALK_IN", currentState: "QUEUED" },
        journeys: {
          person: { currentStage: "QUEUED" },
          provider: { currentStage: "READY_FOR_PROVIDER" },
        },
        nextActions: [{ code: "START_ENCOUNTER", label: "Start encounter" }],
        auditSummary: { correlationId: "42" },
        permissions: [],
        clinicalContext: {},
      },
      isLoading: false,
      isError: false,
    });

    render(<JourneyOrchestrationRail transactionId="journey-42" patientId="patient-1" journeyId="42" />);
    expect(screen.getByText("Walk-in queue journey")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start encounter" })).toBeInTheDocument();
  });

  it("shows unlink guidance when no transaction is returned", () => {
    useCoreTransactionDetail.mockReturnValue({ data: null, isLoading: false, isError: false });
    render(<JourneyOrchestrationRail transactionId="journey-99" patientId="patient-1" journeyId="99" />);
    expect(screen.getByText(/Walk-in journey not linked yet/)).toBeInTheDocument();
  });
});
