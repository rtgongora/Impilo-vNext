import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import LabWorklistPage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({
    children,
    title,
    subtitle,
  }: {
    children: ReactNode;
    title: string;
    subtitle?: string;
  }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/components/FeatureMaturityBadge", () => ({
  FeatureMaturityBadge: ({ detail }: { detail?: string }) => (
    <span data-testid="maturity-badge">{detail}</span>
  ),
}));

const acceptMutate = vi.fn();
const rejectMutate = vi.fn();

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams("type=LAB"),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } | null }) => unknown) =>
    selector({ facility: { id: "fac-lab-1", name: "Central Lab" } }),
}));

vi.mock("@/hooks/queries/useLabWorklist", () => ({
  useLabWorklist: () => ({
    isLoading: false,
    isError: false,
    data: {
      data: {
        items: [
          {
            orderId: "ord-100",
            patientCpid: "CPID-ZW-00042",
            status: "PLACED",
            priority: "STAT",
            placedAt: "2026-06-08T10:00:00Z",
            clinicalNotes: "FBC urgent",
          },
          {
            orderId: "ord-200",
            patientCpid: "CPID-ZW-00099",
            status: "IN_PROGRESS",
            priority: "ROUTINE",
            placedAt: "2026-06-08T09:00:00Z",
            clinicalNotes: "U&E",
          },
        ],
        summary: {
          pending_collection: 1,
          in_progress: 1,
          completed: 0,
          urgent: 1,
          total: 2,
        },
      },
    },
  }),
  useAcceptLabWorklistItem: () => ({
    isPending: false,
    mutateAsync: acceptMutate,
  }),
  useRejectLabWorklistItem: () => ({
    isPending: false,
    mutateAsync: rejectMutate,
  }),
}));

describe("LabWorklistPage", () => {
  beforeEach(() => {
    acceptMutate.mockReset();
    rejectMutate.mockReset();
  });

  it("renders worklist heading and live BFF maturity badge", () => {
    render(<LabWorklistPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Lab Worklist" })).toBeInTheDocument();
    expect(screen.getByTestId("maturity-badge")).toHaveTextContent("/internal/v1/lab-worklists");
  });

  it("shows summary counts and worklist rows from BFF data", () => {
    render(<LabWorklistPage />);

    expect(screen.getByText("Pending Collection")).toBeInTheDocument();
    expect(screen.getByText("ord-100")).toBeInTheDocument();
    expect(screen.getByText("CPID-ZW-00042")).toBeInTheDocument();
    expect(screen.getByText("ord-200")).toBeInTheDocument();
  });

  it("filters rows by search query", async () => {
    const user = userEvent.setup();
    render(<LabWorklistPage />);

    await user.type(
      screen.getByPlaceholderText(/Search by patient CPID/i),
      "00042",
    );

    expect(screen.getByText("ord-100")).toBeInTheDocument();
    expect(screen.queryByText("ord-200")).not.toBeInTheDocument();
  });

  it("exposes accept action for pending orders", async () => {
    const user = userEvent.setup();
    render(<LabWorklistPage />);

    await user.click(screen.getByRole("button", { name: /Accept/i }));

    expect(acceptMutate).toHaveBeenCalledWith({ orderId: "ord-100" });
  });
});
