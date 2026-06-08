import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { SurveillanceOutbreakOrchestrationPanel } from "./SurveillanceOutbreakOrchestrationPanel";

vi.mock("@/hooks/queries/useSurveillance", () => ({
  useSignals: () => ({ data: [{ id: "s1" }], isLoading: false }),
  useCases: () => ({ data: [{ id: "c1" }, { id: "c2" }], isLoading: false }),
  useAlerts: () => ({ data: [{ id: "a1" }], isLoading: false }),
}));

vi.mock("@/hooks/queries/useNdila", () => ({
  useNdilaTileConfig: () => ({
    data: { provider: "PREVIEW_SOVEREIGN" },
    isLoading: false,
    isError: false,
  }),
}));

describe("SurveillanceOutbreakOrchestrationPanel", () => {
  it("shows surveillance KPIs and Ndila probe", () => {
    render(<SurveillanceOutbreakOrchestrationPanel />);
    expect(screen.getByTestId("surveillance-outbreak-orchestration-panel")).toBeInTheDocument();
    expect(screen.getByTestId("surveillance-kpi-strip")).toHaveTextContent("1 signal(s)");
    expect(screen.getByTestId("surveillance-ndila-probe")).toHaveTextContent("PREVIEW_SOVEREIGN");
    expect(screen.getByRole("link", { name: /Outbreaks/i })).toHaveAttribute("href", "/public-health?tab=outbreaks");
  });
});
