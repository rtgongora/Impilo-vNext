import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CrvsUbomiOrchestrationRail } from "./CrvsUbomiOrchestrationRail";

vi.mock("@/hooks/queries/useUbomiRegistry", () => ({
  useUbomiRegistry: () => ({ data: { available: true, message: "ok" }, isLoading: false }),
}));

vi.mock("@/hooks/queries/useUbomi", () => ({
  useUbomiBirths: () => ({ data: [{ id: 1 }], isLoading: false }),
  useUbomiDeaths: () => ({ data: [{ id: 2 }, { id: 3 }], isLoading: false }),
}));

describe("CrvsUbomiOrchestrationRail", () => {
  it("shows UBOMI CRVS orchestration KPIs", () => {
    render(<CrvsUbomiOrchestrationRail />);
    expect(screen.getByTestId("crvs-ubomi-orchestration-rail")).toBeInTheDocument();
    expect(screen.getByTestId("ubomi-crvs-kpi-strip")).toHaveTextContent("1 birth notification(s)");
    expect(screen.getByRole("link", { name: /CRVS console/i })).toHaveAttribute("href", "/ubomi");
  });
});
