import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ProcedureWorklistPage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));

const transitionMutate = vi.fn();

vi.mock("@/hooks/queries/useDiagnosticsOrders", () => ({
  useFulfilmentWorklist: () => ({
    data: {
      data: [
        { orderId: "ORD-ECG-1", orderType: "PROCEDURE", status: "PLACED", patientCpid: "CPID-1", workflowState: "ACCEPTED", scheduledAt: null },
      ],
    },
    isLoading: false,
    isError: false,
  }),
  useWorkflowTransition: () => ({ mutate: transitionMutate, isPending: false, variables: undefined, isError: false }),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ProcedureWorklistPage />
    </QueryClientProvider>,
  );
}

describe("ProcedureWorklistPage", () => {
  it("lists procedure orders and drives the workflow (schedule/start)", () => {
    renderPage();
    expect(screen.getByText("ORD-ECG-1")).toBeInTheDocument();
    expect(screen.getByText("ACCEPTED")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Start"));
    expect(transitionMutate).toHaveBeenCalledWith({ orderId: "ORD-ECG-1", target: "IN_PROGRESS" });
  });
});
