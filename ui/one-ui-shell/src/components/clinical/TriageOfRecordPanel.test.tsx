import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { TriageOfRecordPanel } from "./TriageOfRecordPanel";

describe("TriageOfRecordPanel", () => {
  it("shows the empty state when no triage has been recorded", () => {
    render(<TriageOfRecordPanel triage={null} />);
    expect(screen.getByTestId("triage-of-record-empty")).toBeInTheDocument();
  });

  it("renders the IITT priority and acuity as the authoritative decision", () => {
    render(
      <TriageOfRecordPanel
        triage={{ acuity: 1, triage_tool: "WHO_IITT", iitt_priority: "RED", requires_clinician_review: false }}
      />,
    );
    expect(screen.getByTestId("iitt-priority")).toHaveTextContent("IITT RED");
    expect(screen.getByTestId("acuity-of-record")).toHaveTextContent("1");
    expect(screen.getByTestId("triage-tool")).toHaveTextContent(/authoritative/i);
    expect(screen.queryByTestId("clinician-review-banner")).not.toBeInTheDocument();
  });

  it("raises the clinician-review banner with its reason when flagged", () => {
    render(
      <TriageOfRecordPanel
        triage={{
          acuity: 3,
          triage_tool: "WHO_IITT",
          iitt_priority: "GREEN",
          requires_clinician_review: true,
          review_reason: "Advisory ESI acuity 1 is materially more urgent than IITT GREEN",
        }}
      />,
    );
    const banner = screen.getByTestId("clinician-review-banner");
    expect(banner).toHaveTextContent(/clinician review required/i);
    expect(banner).toHaveTextContent(/materially more urgent/i);
  });

  it("labels a manual entry as manual, not authoritative, and shows no IITT tier", () => {
    render(
      <TriageOfRecordPanel
        triage={{ acuity: 2, triage_tool: "ESI", iitt_priority: null, requires_clinician_review: false }}
      />,
    );
    expect(screen.getByTestId("triage-tool")).toHaveTextContent(/manual entry/i);
    expect(screen.queryByTestId("iitt-priority")).not.toBeInTheDocument();
  });
});
