import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import InvestigationsPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "CPID-1" }),
}));
vi.mock("@/components/EHRLayout", () => ({
  EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));

vi.mock("@/hooks/queries/useDiagnosticsOrders", () => ({
  useDiagnosticsOrders: () => ({
    data: {
      data: [
        {
          orderId: "ORD-LAB-1",
          orderType: "LAB",
          status: "RESULT_AVAILABLE",
          accessionNumber: "ACC-2026-1",
          workflowState: "FINAL_RESULT",
          imagingState: null,
          studyUid: null,
          studyViewerUrl: null,
          placedAt: "2026-06-25T09:00:00.000Z",
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
  useOrderResults: () => ({
    data: { data: [{ resultId: "RES-1", orderId: "ORD-LAB-1", kind: "LAB", reportStatus: "FINAL", isCritical: true, version: 1 }] },
    isLoading: false,
  }),
  useResultObservations: () => ({
    data: {
      data: [
        {
          observationId: "OBS-1",
          resultId: "RES-1",
          orderId: "ORD-LAB-1",
          sequence: 0,
          analyteName: "Potassium",
          valueNumeric: 7.1,
          unit: "mmol/L",
          refRangeText: "3.5-5.1",
          abnormalFlag: "HH",
          criticalFlag: true,
        },
      ],
    },
    isLoading: false,
  }),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <InvestigationsPage />
    </QueryClientProvider>,
  );
}

describe("InvestigationsPage", () => {
  it("surfaces the generalised workflow state and expands to structured lab observations", () => {
    renderPage();

    // The generalised fine-grained lab state is shown (not just the coarse status).
    expect(screen.getByText("FINAL_RESULT")).toBeInTheDocument();
    expect(screen.getByText("LAB")).toBeInTheDocument();

    // Expanding the order reveals the structured observation table with the critical analyte.
    fireEvent.click(screen.getByText("ORD-LAB-1"));
    expect(screen.getByTestId("observation-table")).toBeInTheDocument();
    expect(screen.getByText("Potassium")).toBeInTheDocument();
    expect(screen.getByText("7.1")).toBeInTheDocument();
  });
});
