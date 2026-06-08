import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PublicHealthOutreachOrchestrationPanel } from "./PublicHealthOutreachOrchestrationPanel";

vi.mock("@/hooks/queries/usePublicHealth", () => ({
  usePublicHealthFieldTasks: () => ({
    data: [
      { id: "t1", status: "PLANNED" },
      { id: "t2", status: "COMPLETED" },
    ],
    isLoading: false,
  }),
}));

describe("PublicHealthOutreachOrchestrationPanel", () => {
  it("shows field outreach task orchestration", () => {
    render(<PublicHealthOutreachOrchestrationPanel />);
    expect(screen.getByTestId("public-health-outreach-orchestration-panel")).toBeInTheDocument();
    expect(screen.getByTestId("outreach-field-kpi-strip")).toHaveTextContent("2 field task(s)");
    expect(screen.getByRole("link", { name: /Field operations/i })).toHaveAttribute("href", "/public-health?tab=field");
  });
});
