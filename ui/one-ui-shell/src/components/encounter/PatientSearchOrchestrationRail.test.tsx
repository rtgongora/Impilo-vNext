import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { PatientSearchOrchestrationRail } from "./PatientSearchOrchestrationRail";

const mockUseCoreTransactionDetail = vi.fn();
const mockUseApplyCoreTransactionAction = vi.fn();

vi.mock("@/hooks/queries/useCoreTransactionExperience", () => ({
  useCoreTransactionDetail: (...args: unknown[]) => mockUseCoreTransactionDetail(...args),
  useApplyCoreTransactionAction: () => mockUseApplyCoreTransactionAction(),
}));

function renderRail(props: Partial<Parameters<typeof PatientSearchOrchestrationRail>[0]> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <PatientSearchOrchestrationRail
        transactionId="tx-search-1"
        journeyId="journey-42"
        {...props}
      />
    </QueryClientProvider>,
  );
}

describe("PatientSearchOrchestrationRail", () => {
  beforeEach(() => {
    mockUseApplyCoreTransactionAction.mockReturnValue({ mutate: vi.fn(), isPending: false });
    mockUseCoreTransactionDetail.mockReturnValue({ data: null, isLoading: false, isError: false });
  });

  it("shows static guidance when no transaction id is provided", () => {
    renderRail({ transactionId: null });
    expect(screen.getByText(/Patient search orchestration/)).toBeInTheDocument();
    expect(screen.getByText(/Search matches route into chart entry/)).toBeInTheDocument();
  });

  it("shows loading state for correlated search", () => {
    mockUseCoreTransactionDetail.mockReturnValue({ data: null, isLoading: true, isError: false });
    renderRail();
    expect(screen.getByText(/Loading patient-selection transaction context/)).toBeInTheDocument();
  });

  it("shows transaction context and selected patient resolve action", () => {
    mockUseCoreTransactionDetail.mockReturnValue({
      data: {
        transaction: { id: "tx-1", type: "FACILITY_WALK_IN", currentState: "IDENTITY_PENDING" },
        journeys: {
          provider: { currentStage: "OPEN_CLIENT_CONTEXT" },
          person: { currentStage: "IDENTIFY_ME" },
        },
        auditSummary: { correlationId: "corr-search" },
        nextActions: [{ code: "RESOLVE_IDENTITY", label: "Resolve client identity" }],
        permissions: [],
      },
      isLoading: false,
      isError: false,
    });
    renderRail({ selectedPatientId: "pat-1", selectedPatientLabel: "Tariro Moyo" });
    expect(screen.getByText(/Patient search transaction journey/)).toBeInTheDocument();
    expect(screen.getByText(/Selected patient:/)).toBeInTheDocument();
    expect(screen.getByText("Tariro Moyo")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Resolve identity on transaction/i })).toBeInTheDocument();
  });
});
