import type { ReactNode } from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import FundoStudioAnalyticsPage from "../page";

vi.mock("@/components/learning/FundoStudioWorkspace", () => ({
  FundoStudioWorkspace: ({ title, children }: { title: string; children: ReactNode }) => (
    <section>
      <h1>{title}</h1>
      {children}
    </section>
  ),
}));

vi.mock("@/hooks/queries/useFundoLms", () => ({
  useFundoReportsOverview: () => ({ data: { data: { courses: 10, enrolments: 20, completed: 8, certificates: 4 } } }),
  useFundoReportFiltered: () => ({ data: { data: { items: [{ status: "COMPLETED", passed: true }] } } }),
}));

describe("FundoStudioAnalyticsPage", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders and switches role views", () => {
    render(<FundoStudioAnalyticsPage />);
    expect(screen.getByRole("heading", { name: "Studio Analytics" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "TRAINER" }));
    expect(screen.getByText("Pass trend")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "LEADERSHIP" }));
    expect(screen.getByText("Completion health")).toBeInTheDocument();
  });
});
