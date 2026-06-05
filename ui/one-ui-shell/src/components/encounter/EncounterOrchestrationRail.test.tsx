import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { EncounterOrchestrationRail } from "./EncounterOrchestrationRail";

const mockUseEncounterCoreTransaction = vi.fn();
const mockUseApplyCoreTransactionAction = vi.fn();

vi.mock("@/hooks/queries/useCoreTransactionExperience", () => ({
  useEncounterCoreTransaction: (...args: unknown[]) => mockUseEncounterCoreTransaction(...args),
  useApplyCoreTransactionAction: () => mockUseApplyCoreTransactionAction(),
}));

function renderRail() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <EncounterOrchestrationRail encounterId="enc-1" patientId="pat-1" />
    </QueryClientProvider>,
  );
}

describe("EncounterOrchestrationRail", () => {
  beforeEach(() => {
    mockUseApplyCoreTransactionAction.mockReturnValue({ mutate: vi.fn(), isPending: false });
  });

  it("shows loading state", () => {
    mockUseEncounterCoreTransaction.mockReturnValue({
      transaction: null,
      isLoading: true,
      isError: false,
    });
    renderRail();
    expect(screen.getByText(/Loading encounter transaction context/)).toBeInTheDocument();
  });

  it("shows empty state when no transaction is linked", () => {
    mockUseEncounterCoreTransaction.mockReturnValue({
      transaction: null,
      isLoading: false,
      isError: false,
    });
    renderRail();
    expect(screen.getByText(/Encounter transaction not linked yet/)).toBeInTheDocument();
  });

  it("shows transaction context when linked", () => {
    mockUseEncounterCoreTransaction.mockReturnValue({
      transaction: {
        transaction: { id: "tx-1", type: "FACILITY_WALK_IN", currentState: "IN_ENCOUNTER" },
        journeys: {
          provider: { currentStage: "DELIVER_CARE" },
          person: { currentStage: "RECEIVE_CARE" },
        },
        auditSummary: { correlationId: "corr-abc" },
        nextActions: [{ code: "COMPLETE_ENCOUNTER", label: "Complete encounter" }],
        permissions: [],
        clinicalContext: { ordersPending: 2 },
      },
      isLoading: false,
      isError: false,
    });
    renderRail();
    expect(screen.getByText(/Encounter transaction journey/)).toBeInTheDocument();
    expect(screen.getByText("Complete encounter")).toBeInTheDocument();
    expect(screen.getByText(/corr-abc/)).toBeInTheDocument();
  });
});
