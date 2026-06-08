import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ReportingDashboardOrchestrationPanel } from "./ReportingDashboardOrchestrationPanel";

vi.mock("@/hooks/queries/useReports", () => ({
  useGenerateReport: () => ({ mutate: vi.fn(), isPending: false, data: null }),
  useReportJob: () => ({ data: null, isLoading: false }),
}));

vi.mock("@/hooks/queries/useNdila", () => ({
  useNdilaTileConfig: () => ({
    data: { provider: "ndila-service", tileUrlTemplate: "https://tiles.example/{z}/{x}/{y}" },
    isLoading: false,
    isError: false,
  }),
}));

describe("ReportingDashboardOrchestrationPanel", () => {
  it("renders report orchestration controls and Ndila/NDR depth links", () => {
    render(<ReportingDashboardOrchestrationPanel />);
    expect(screen.getByTestId("reporting-dashboard-orchestration-panel")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Run clinical summary/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Custom reports/i })).toHaveAttribute("href", "/reports/custom");
    expect(screen.getByTestId("reporting-ndila-probe")).toHaveTextContent(/Ndila tiles: live/i);
    expect(screen.getByTestId("reporting-ndr-probe")).toHaveTextContent(/NDR warehouse/i);
    expect(screen.getByRole("link", { name: /Ndila map/i })).toHaveAttribute("href", "/ndila");
    expect(screen.getByRole("link", { name: /NDR pipelines/i })).toHaveAttribute(
      "href",
      "/data-intelligence/pipelines",
    );
  });
});
