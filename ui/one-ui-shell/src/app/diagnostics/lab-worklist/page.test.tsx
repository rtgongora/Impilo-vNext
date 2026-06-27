import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import LabWorklistPage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));

const transitionMutate = vi.fn();
const collectMutate = vi.fn();
const specimenActionMutate = vi.fn();

vi.mock("@/hooks/queries/useDiagnosticsOrders", () => ({
  useFulfilmentWorklist: () => ({
    data: {
      data: [
        {
          orderId: "ORD-LAB-1",
          orderType: "LAB",
          status: "PLACED",
          patientCpid: "CPID-1",
          accessionNumber: "ACC-1",
          workflowState: "RECEIVED",
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
  useWorkflowTransition: () => ({ mutate: transitionMutate, isPending: false, variables: undefined, isError: false }),
  useCollectSpecimen: () => ({ mutate: collectMutate, isPending: false }),
  useSpecimenAction: () => ({ mutate: specimenActionMutate, isPending: false }),
  useOrderSpecimens: () => ({
    data: { data: [{ specimenId: "SPC-1", orderId: "ORD-LAB-1", specimenType: "blood", sampleId: "SPC-1", status: "COLLECTED" }] },
    isLoading: false,
  }),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <LabWorklistPage />
    </QueryClientProvider>,
  );
}

describe("LabWorklistPage", () => {
  it("lists lab orders, accepts via workflow transition, and exposes specimen actions", () => {
    renderPage();
    expect(screen.getByText("ORD-LAB-1")).toBeInTheDocument();
    expect(screen.getByText("RECEIVED")).toBeInTheDocument();

    // Accept drives the LAB workflow transition.
    fireEvent.click(screen.getByText("Accept"));
    expect(transitionMutate).toHaveBeenCalledWith({ orderId: "ORD-LAB-1", target: "ACCEPTED" });

    // Expanding shows the specimen panel; a COLLECTED specimen can be dispatched.
    fireEvent.click(screen.getByText("Specimens"));
    expect(screen.getByTestId("specimen-panel")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Dispatch"));
    expect(specimenActionMutate).toHaveBeenCalledWith(
      expect.objectContaining({ specimenId: "SPC-1", action: "dispatch" }),
    );
  });
});
