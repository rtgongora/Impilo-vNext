import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import type { MaternitySummaryProgress } from "@/hooks/queries/useMaternitySummary";
import { PartographProgressPanel } from "../PartographProgressPanel";
import { presentProgress } from "../partograph-progress-presentation";

function progress(over: Partial<MaternitySummaryProgress> = {}): MaternitySummaryProgress {
  return {
    status: "LEFT_OF_ALERT",
    latest_dilation_cm: 5,
    expected_dilation_cm: 5,
    hours_behind_alert_line: null,
    outstanding_observations: [],
    observations: [],
    recommended_action: "Continue routine monitoring.",
    content_version: "engineering-seed-1.0.0",
    content_source: "WHO classic partograph",
    ...over,
  };
}

describe("the partograph verdict is an instruction, not a status name", () => {
  it("names the action line crossing and shows what to do", () => {
    render(
      <PartographProgressPanel
        progress={progress({
          status: "AT_OR_RIGHT_OF_ACTION",
          hours_behind_alert_line: 4.2,
          recommended_action: "Obstructed labour requires a decision now — reassess and escalate.",
        })}
      />,
    );
    const panel = screen.getByTestId("partograph-progress");
    expect(panel.getAttribute("data-tone")).toBe("action");
    expect(panel.textContent).toContain("Action line crossed");
    expect(screen.getByTestId("partograph-progress-action").textContent).toContain("decision now");
    expect(screen.getByTestId("partograph-progress-dilation").textContent).toContain(
      "4.2 hours behind the alert line",
    );
  });

  it("distinguishes the alert line from the action line", () => {
    expect(presentProgress(progress({ status: "BETWEEN_ALERT_AND_ACTION" })).tone).toBe("alert");
    expect(presentProgress(progress({ status: "AT_OR_RIGHT_OF_ACTION" })).tone).toBe("action");
    expect(presentProgress(progress({ status: "LEFT_OF_ALERT" })).tone).toBe("normal");
  });
});

describe("INSUFFICIENT_DATA is never the reassuring case", () => {
  it("renders as an outstanding job, not as a quiet parenthetical", () => {
    render(
      <PartographProgressPanel
        progress={progress({
          status: "INSUFFICIENT_DATA",
          latest_dilation_cm: null,
          expected_dilation_cm: null,
          outstanding_observations: ["CERVICAL_DILATION", "FETAL_HEART_RATE"],
          recommended_action: "Assess and record cervical dilation to establish progress.",
        })}
      />,
    );

    const panel = screen.getByTestId("partograph-progress");
    expect(panel.getAttribute("data-tone")).toBe("insufficient");
    expect(presentProgress(progress({ status: "INSUFFICIENT_DATA" })).urgent).toBe(true);
    expect(screen.getByTestId("partograph-progress-insufficient-note").textContent).toContain(
      "not a finding that it is",
    );
    // What it is waiting on. Without this, "not established" is a dead end rather than a job.
    const outstanding = screen.getByTestId("partograph-progress-outstanding");
    expect(outstanding.textContent).toContain("cervical dilation");
    expect(outstanding.textContent).toContain("fetal heart rate");
  });

  it("treats a status this screen does not recognise as not-established, never as normal", () => {
    const p = presentProgress(progress({ status: "SOMETHING_NEW" }));
    expect(p.tone).toBe("insufficient");
    expect(p.tone).not.toBe("normal");
  });

  it("does not mark a normally progressing labour as urgent", () => {
    expect(presentProgress(progress({ status: "LEFT_OF_ALERT" })).urgent).toBe(false);
  });
});
