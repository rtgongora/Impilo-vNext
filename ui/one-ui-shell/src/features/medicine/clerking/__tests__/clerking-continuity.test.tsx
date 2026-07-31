import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { ClerkingContinuityShell } from "../ClerkingContinuityShell";

vi.mock("../useClerkingContinuityContext", () => ({
  useClerkingContinuityContext: vi.fn(),
}));

import { useClerkingContinuityContext } from "../useClerkingContinuityContext";

const mocked = vi.mocked(useClerkingContinuityContext);

function baseCtx(overrides: Partial<ReturnType<typeof useClerkingContinuityContext>> = {}) {
  return {
    patientId: "patient-1",
    activeProblems: [],
    resolvedProblems: [],
    episodes: [],
    medications: [],
    adherenceFindings: [],
    allergies: [],
    immunizations: [],
    procedures: [],
    functional: [],
    family: [],
    carePlans: [],
    goals: [],
    directives: [],
    admissions: [],
    maternity: null,
    unavailable: [],
    isLoading: false,
    ...overrides,
  };
}

describe("ClerkingContinuityShell honesty", () => {
  it("names unavailable blocks instead of claiming empty history", () => {
    mocked.mockReturnValue(
      baseCtx({
        unavailable: ["problem list", "medical episodes", "allergies"],
      })
    );

    render(<ClerkingContinuityShell patientId="patient-1" />);

    expect(screen.getByTestId("clerking-unavailable-banner")).toHaveTextContent(
      "Could not load: problem list, medical episodes, allergies"
    );
    expect(screen.getAllByTestId("clerking-unavailable").length).toBeGreaterThanOrEqual(3);
    expect(screen.queryByText("No active problems on the list")).toBeNull();
  });

  it("shows empty labels only when the read succeeded", () => {
    mocked.mockReturnValue(baseCtx());
    render(<ClerkingContinuityShell patientId="patient-1" />);
    expect(screen.getByText("No active problems on the list")).toBeInTheDocument();
    expect(screen.queryByTestId("clerking-unavailable-banner")).toBeNull();
  });
});
