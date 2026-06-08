import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import LabPage from "./page";

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
        summary: {
          pending_collection: 3,
          in_progress: 2,
          completed: 5,
          urgent: 1,
          total: 10,
        },
        items: [],
      },
    },
  }),
}));

describe("LabPage", () => {
  it("renders order hub with type filters and summary", () => {
    render(<LabPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Order Hub" })).toBeInTheDocument();
    expect(screen.getByText(/multi-type order orchestration/i)).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByTestId("maturity-badge")).toHaveTextContent("lab-worklists");
  });

  it("links to all four lab sub-pages with type query on worklist", async () => {
    const user = userEvent.setup();
    render(<LabPage />);

    expect(screen.getByText("Worklist")).toBeInTheDocument();
    expect(screen.getByText("Results Review")).toBeInTheDocument();
    expect(screen.getByText("Test Catalog")).toBeInTheDocument();
    expect(screen.getByText("Reconciliation")).toBeInTheDocument();

    expect(screen.getByRole("link", { name: /Worklist/i })).toHaveAttribute(
      "href",
      "/lab/worklist?type=LAB",
    );
    expect(screen.getByRole("link", { name: /Results Review/i })).toHaveAttribute(
      "href",
      "/lab/results",
    );

    await user.click(screen.getByRole("button", { name: "Imaging" }));
    expect(screen.getByRole("link", { name: /Worklist/i })).toHaveAttribute(
      "href",
      "/lab/worklist?type=IMAGING",
    );
  });
});
