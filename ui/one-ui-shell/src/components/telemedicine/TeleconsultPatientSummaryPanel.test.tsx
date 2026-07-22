import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TeleconsultPatientSummaryPanel } from "./TeleconsultPatientSummaryPanel";
import type { PatientSummary } from "@/hooks/queries/useSummary";

const { summaryState } = vi.hoisted(() => ({
  summaryState: {
    current: {
      data: undefined as { data: PatientSummary } | undefined,
      isLoading: false,
      isError: false,
      isSuccess: true,
    },
  },
}));

vi.mock("@/hooks/queries/useSummary", () => ({
  usePatientSummary: () => summaryState.current,
}));

function summary(overrides: Partial<PatientSummary> = {}): PatientSummary {
  return {
    cpid: "CPID-1",
    conditions: [],
    medications: [],
    allergies: [],
    immunizations: [],
    recentEncounters: [],
    ...overrides,
  };
}

describe("TeleconsultPatientSummaryPanel", () => {
  beforeEach(() => {
    summaryState.current = {
      data: { data: summary() },
      isLoading: false,
      isError: false,
      isSuccess: true,
    };
  });

  it("shows the loading state", () => {
    summaryState.current = { data: undefined, isLoading: true, isError: false, isSuccess: false };
    render(<TeleconsultPatientSummaryPanel patientId="CPID-1" />);
    expect(screen.getByTestId("teleconsult-summary-loading")).toBeInTheDocument();
  });

  it("shows an honest error state", () => {
    summaryState.current = { data: undefined, isLoading: false, isError: true, isSuccess: false };
    render(<TeleconsultPatientSummaryPanel patientId="CPID-1" />);
    expect(screen.getByTestId("teleconsult-summary-error")).toBeInTheDocument();
  });

  it("renders NKDA when there are no allergies", () => {
    render(<TeleconsultPatientSummaryPanel patientId="CPID-1" />);
    expect(screen.getByText(/No known drug allergies/i)).toBeInTheDocument();
  });

  it("renders allergies prominently (red container) when present", () => {
    summaryState.current.data = {
      data: summary({
        allergies: [{ substance: "Penicillin", reaction: "Anaphylaxis", criticality: "HIGH" }],
      }),
    };
    render(<TeleconsultPatientSummaryPanel patientId="CPID-1" />);
    const box = screen.getByTestId("teleconsult-summary-allergies");
    expect(box.className).toContain("bg-danger-soft");
    expect(box.className).toContain("border-red-300");
    expect(screen.getByText("Penicillin")).toBeInTheDocument();
  });

  it("lists active meds, conditions, and the recent-encounter count", () => {
    summaryState.current.data = {
      data: summary({
        medications: [{ code: "m1", display: "Metformin", status: "active", dosage: "500mg" }],
        conditions: [{ code: "c1", display: "Type 2 Diabetes", clinicalStatus: "active" }],
        recentEncounters: [
          { id: "e1", type: "OUTPATIENT", date: "2026-01-01", facility: "F", status: "finished" },
          { id: "e2", type: "OUTPATIENT", date: "2026-02-01", facility: "F", status: "finished" },
        ],
      }),
    };
    render(<TeleconsultPatientSummaryPanel patientId="CPID-1" />);
    expect(screen.getByText("Metformin")).toBeInTheDocument();
    expect(screen.getByText("Type 2 Diabetes")).toBeInTheDocument();
    expect(screen.getByTestId("teleconsult-summary-encounters")).toHaveTextContent("2");
  });
});
