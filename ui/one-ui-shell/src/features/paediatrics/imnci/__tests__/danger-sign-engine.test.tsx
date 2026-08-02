import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import type { DangerSignAssessment } from "@/hooks/queries/usePaediatricDecisionSupport";
import { DangerSignEngineView } from "../DangerSignEnginePanel";

function assessment(over: Partial<DangerSignAssessment> = {}): DangerSignAssessment {
  return {
    incomplete: false,
    unassessedSigns: [],
    alerts: [],
    referralRequired: false,
    contentVersion: "engineering-seed-1.0.0",
    approvalStatus: "ENGINEERING_SEED",
    note: null,
    ...over,
  };
}

describe("a danger-sign screen is only reassurance if the questions were asked", () => {
  it("never shows an all-clear over an incomplete screen", () => {
    render(
      <DangerSignEngineView
        assessment={assessment({
          incomplete: true,
          unassessedSigns: ["convulsions", "lethargicOrUnconscious"],
        })}
      />,
    );

    const incomplete = screen.getByTestId("danger-signs-incomplete");
    expect(incomplete.textContent).toContain("2 signs were not assessed");
    expect(incomplete.textContent).toContain("convulsions");
    expect(incomplete.textContent).toContain("not an all-clear");
    // The reassuring branch must not have been reached.
    expect(screen.queryByTestId("danger-signs-clear")).toBeNull();
  });

  it("reports the incompleteness even when alerts have already fired", () => {
    // An alert plus unassessed signs is not a finished screen: completing them may raise more.
    render(
      <DangerSignEngineView
        assessment={assessment({
          incomplete: true,
          unassessedSigns: ["convulsions"],
          referralRequired: true,
          alerts: [
            {
              id: "PSBI",
              name: "Possible serious bacterial infection",
              severity: "CRITICAL",
              action: "Give the first dose of antibiotic and refer urgently.",
              referralRequired: true,
              overridable: false,
              triggeringFindings: ["poorFeeding"],
              source: "IMNCI young infant chart",
              contentVersion: "engineering-seed-1.0.0",
              approvalStatus: "ENGINEERING_SEED",
            },
          ],
        })}
      />,
    );

    expect(screen.getByTestId("danger-alert-PSBI").textContent).toContain("refer urgently");
    expect(screen.getByTestId("danger-alert-PSBI").textContent).toContain("cannot be overridden");
    const incomplete = screen.getByTestId("danger-signs-incomplete");
    expect(incomplete.textContent).toContain("may raise further alerts");
    expect(screen.queryByTestId("danger-signs-clear")).toBeNull();
  });

  it("gives the all-clear only when every sign in the rule set was assessed", () => {
    render(<DangerSignEngineView assessment={assessment()} />);
    const clear = screen.getByTestId("danger-signs-clear");
    expect(clear.getAttribute("data-tone")).toBe("well");
    expect(clear.textContent).toContain("Every danger sign in the rule set was assessed");
  });

  it("shows the referral instruction when the rules require one", () => {
    render(<DangerSignEngineView assessment={assessment({ referralRequired: true })} />);
    expect(screen.getByTestId("danger-signs-referral").textContent).toContain("Referral required");
  });
});
