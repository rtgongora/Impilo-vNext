import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { NompiloGuidanceOrchestrationRail } from "./NompiloGuidanceOrchestrationRail";

vi.mock("next/navigation", () => ({
  usePathname: () => "/guidance",
}));

vi.mock("@/hooks/queries/useGuidance", () => ({
  useReminders: () => ({
    data: { data: [{ id: "r1", dismissed: false }, { id: "r2", dismissed: true }] },
    isLoading: false,
  }),
}));

describe("NompiloGuidanceOrchestrationRail", () => {
  it("renders route context and guidance links", () => {
    render(<NompiloGuidanceOrchestrationRail />);
    expect(screen.getByTestId("nompilo-guidance-orchestration-rail")).toBeInTheDocument();
    expect(screen.getByTestId("nompilo-route-context")).toHaveTextContent("/guidance");
    expect(screen.getByRole("link", { name: /Ask with context/i })).toHaveAttribute("href", "/ask?from=%2Fguidance");
    expect(screen.getByRole("link", { name: /Reminders/i })).toHaveAttribute("href", "/guidance/reminders");
  });
});
