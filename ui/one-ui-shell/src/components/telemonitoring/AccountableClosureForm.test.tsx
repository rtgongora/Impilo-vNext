/**
 * OF-B26/OF-B28 — the closure form holds the §14.6 line: submit REFUSED until
 * all four fields are present; the emergency assumption-of-care recorder only
 * appears for EMERGENCY episodes.
 */

import React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AccountableClosureForm } from "./AccountableClosureForm";
import type { AlertEpisodeView } from "@/lib/telemonitoring/types";

function episode(overrides: Partial<AlertEpisodeView> = {}): AlertEpisodeView {
  return {
    episodeId: "E-1",
    planId: "P-1",
    patientCpid: "CPID-1",
    deviceRef: "DEV-1",
    triggerClass: "THRESHOLD_BREACH",
    metricCode: "SBP",
    initialSeverity: "RED",
    severity: "RED",
    status: "ACKNOWLEDGED",
    severityCeilingApplied: false,
    contributingReadings: null,
    ladderHistory: null,
    currentRung: null,
    breachCount: 1,
    responseDeadlineAt: null,
    acknowledgedBy: "nurse-1",
    acknowledgedAt: "2026-07-23T08:05:00Z",
    autoEscalatedAt: null,
    chwTaskDispatched: null,
    reviewReferralRef: null,
    emsIncidentRef: null,
    emsDispatched: null,
    assumptionOfCareNote: null,
    assumptionOfCareBy: null,
    resolvedBy: null,
    assessment: null,
    actionTaken: null,
    outcome: null,
    resolvedAt: null,
    createdAt: "2026-07-23T08:00:00Z",
    ...overrides,
  };
}

function fill(id: string, value: string) {
  fireEvent.change(screen.getByLabelText(new RegExp(id, "i")), { target: { value } });
}

describe("AccountableClosureForm", () => {
  it("refuses submit until all four closure fields are present", () => {
    const onResolve = vi.fn();
    render(<AccountableClosureForm episode={episode()} onResolve={onResolve} />);

    const submit = screen.getByRole("button", { name: /close episode/i });
    expect(submit).toBeDisabled();

    fill("Reviewer", "S. Ncube, monitoring nurse");
    expect(submit).toBeDisabled();

    fill("Clinical assessment", "BP settled after guided repeat");
    expect(submit).toBeDisabled();

    fill("Action taken", "Guided repeat; confirmed normal");
    expect(submit).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Outcome classification/i), {
      target: { value: "RESOLVED_NO_HARM" },
    });
    expect(submit).not.toBeDisabled();

    fireEvent.click(submit);
    expect(onResolve).toHaveBeenCalledWith({
      resolvedBy: "S. Ncube, monitoring nurse",
      assessment: "BP settled after guided repeat",
      action: "Guided repeat; confirmed normal",
      outcome: "RESOLVED_NO_HARM",
    });
  });

  it("does not show the assumption-of-care recorder for non-emergency episodes", () => {
    render(<AccountableClosureForm episode={episode({ severity: "RED" })} onResolve={vi.fn()} />);
    expect(screen.queryByText(/Assumption of care/i)).toBeNull();
  });

  it("shows the assumption-of-care recorder for EMERGENCY episodes and requires a note", () => {
    const onAssumption = vi.fn();
    render(
      <AccountableClosureForm
        episode={episode({ severity: "EMERGENCY" })}
        onResolve={vi.fn()}
        onRecordAssumptionOfCare={onAssumption}
      />,
    );

    expect(screen.getByLabelText(/Assumption of care/i)).toBeInTheDocument();
    const record = screen.getByRole("button", { name: /record assumption of care/i });
    expect(record).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Assumption of care/i), {
      target: { value: "Handed over to Parirenyatwa ED, Dr M. at 09:40" },
    });
    expect(record).not.toBeDisabled();
    fireEvent.click(record);
    expect(onAssumption).toHaveBeenCalledWith("Handed over to Parirenyatwa ED, Dr M. at 09:40");
  });

  it("shows the recorded handover instead of the recorder once present", () => {
    render(
      <AccountableClosureForm
        episode={episode({
          severity: "EMERGENCY",
          assumptionOfCareBy: "ED registrar",
          assumptionOfCareNote: "Care assumed at facility",
        })}
        onResolve={vi.fn()}
      />,
    );
    expect(screen.getByText(/Recorded by ED registrar/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /record assumption of care/i })).toBeNull();
  });
});
