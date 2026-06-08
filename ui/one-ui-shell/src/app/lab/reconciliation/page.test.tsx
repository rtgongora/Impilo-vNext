import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import LabReconciliationPage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/components/FeatureMaturityBadge", () => ({
  FeatureMaturityBadge: ({ status, detail }: { status: string; detail?: string }) => (
    <span data-testid="maturity-badge">
      {status}:{detail}
    </span>
  ),
}));

vi.mock("@/hooks/queries/useLabReconciliation", () => ({
  useLabReconciliation: () => ({
    data: {
      data: {
        summary: { matched: 1, pending: 2, resolved: 0 },
        items: [
          {
            recId: "rec-1",
            externalKey: "LIS-99",
            status: "PENDING",
            patientCpid: "CPID-1",
          },
        ],
      },
    },
    isLoading: false,
    isError: false,
  }),
  useMatchLabReconciliation: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useResolveLabReconciliation: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

describe("LabReconciliationPage", () => {
  it("uses live lab-reconciliation BFF data", () => {
    render(<LabReconciliationPage />);
    expect(screen.getByText("Lab Reconciliation")).toBeInTheDocument();
    expect(screen.getByTestId("maturity-badge")).toHaveTextContent(
      "live:OROS reconcile queue via /internal/v1/lab-reconciliation",
    );
    expect(screen.getByText("LIS-99")).toBeInTheDocument();
  });
});
