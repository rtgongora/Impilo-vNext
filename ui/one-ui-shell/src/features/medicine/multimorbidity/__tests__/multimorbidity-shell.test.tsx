import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";

const state = vi.hoisted(() => ({
  query: { data: undefined as unknown, isError: false, isLoading: false },
}));

vi.mock("@/hooks/queries/useMultimorbidity", () => ({
  useMultimorbidity: () => state.query,
}));

// eslint-disable-next-line import/first
import { MultimorbidityShell } from "../MultimorbidityShell";

// Shaped as the hook's `data` (an ApiResponse), so `query.data?.data` reaches the assessment. A
// helper one level out would make every assertion below test an undefined view.
const view = (over: Record<string, unknown> = {}) => ({
  data: {
    panels: [], detections: [], incomplete: false,
    content_version: "1.0.0", approval_status: "ENGINEERING_SEED", ...over,
  },
});

describe("MultimorbidityShell — a check that could not run is not a check that passed", () => {
  beforeEach(() => {
    state.query = { data: undefined, isError: false, isLoading: false };
  });

  it("an UNDETERMINED detection is rendered distinctly from a clear one and says it is not clear", () => {
    // The failure this prevents is the whole reason the three states exist. A clinician opens this
    // screen because the single-disease views hid a cross-disease problem; a green tick over a source
    // nobody could read would confirm the hiding with more authority than the views it replaces.
    state.query = {
      ...state.query,
      data: view({
        detections: [{
          key: "duplicate_medicines", title: "Duplicate medicines", state: "UNDETERMINED",
          findings: [], undetermined_reason: "The medicine list could not be obtained.",
        }],
      }),
    };
    render(<MultimorbidityShell patientId="p1" />);

    expect(screen.getByTestId("detection-undetermined-duplicate_medicines"))
      .toHaveTextContent(/not.*a clear result/i);
    expect(screen.queryByTestId("detection-clear-duplicate_medicines")).not.toBeInTheDocument();
  });

  it("a NOT_DETECTED detection says it was checked, so the reassuring answer stays reachable", () => {
    // If the clear state were unreachable the screen would be permanent noise, and permanent noise
    // is ignored — including on the day it is right.
    state.query = {
      ...state.query,
      data: view({
        detections: [{ key: "duplicate_medicines", title: "Duplicate medicines", state: "NOT_DETECTED", findings: [] }],
      }),
    };
    render(<MultimorbidityShell patientId="p1" />);

    expect(screen.getByTestId("detection-clear-duplicate_medicines"))
      .toHaveTextContent(/checked, nothing found/i);
    expect(screen.queryByTestId("detection-undetermined-duplicate_medicines")).not.toBeInTheDocument();
  });

  it("a DETECTED finding always shows its required action, not only the problem", () => {
    state.query = {
      ...state.query,
      data: view({
        detections: [{
          key: "unsafe_combinations", title: "Unsafe combinations", state: "DETECTED",
          findings: [{
            code: "MM_COMBO_ACEI_ARB", severity: "HIGH",
            message: "An ACE inhibitor and an ARB are prescribed together.",
            explanation: "Dual blockade raises hyperkalaemia and AKI risk.",
            required_action: "Review which agent to retain and check potassium.",
          }],
        }],
      }),
    };
    render(<MultimorbidityShell patientId="p1" />);

    expect(screen.getByTestId("finding-MM_COMBO_ACEI_ARB"))
      .toHaveTextContent("Review which agent to retain");
  });

  it("an UNKNOWN panel says why it is empty rather than rendering as empty", () => {
    state.query = {
      ...state.query,
      data: view({
        panels: [{
          key: "appointment_burden", title: "Appointment burden", state: "UNKNOWN", entries: [],
          unknown_reason: "The appointment book could not be read.",
        }],
      }),
    };
    render(<MultimorbidityShell patientId="p1" />);

    expect(screen.getByTestId("panel-unknown-appointment_burden"))
      .toHaveTextContent(/could not be read/);
    expect(screen.queryByTestId("panel-empty-appointment_burden")).not.toBeInTheDocument();
  });

  it("a KNOWN but empty panel says the source WAS read", () => {
    // "Nothing recorded" and "we could not look" must never render the same way.
    state.query = {
      ...state.query,
      data: view({
        panels: [{ key: "shared_risk_factors", title: "Shared risk factors", state: "KNOWN", entries: [] }],
      }),
    };
    render(<MultimorbidityShell patientId="p1" />);

    expect(screen.getByTestId("panel-empty-shared_risk_factors"))
      .toHaveTextContent(/source was read/i);
  });

  it("a FAILED assembly never renders as an absence of conflicts", () => {
    state.query = { data: undefined, isError: true, isLoading: false };
    render(<MultimorbidityShell patientId="p1" />);

    expect(screen.getByTestId("multimorbidity-failed"))
      .toHaveTextContent(/not.*read this as an absence of conflicts/i);
    expect(screen.queryByTestId("multimorbidity")).not.toBeInTheDocument();
  });

  it("the partial-view banner is shown whenever anything went unchecked", () => {
    state.query = {
      ...state.query,
      data: view({ incomplete: true, completeness_note: "This view is partial. Not answered: Duplicate medicines." }),
    };
    render(<MultimorbidityShell patientId="p1" />);

    expect(screen.getByTestId("multimorbidity-incomplete")).toHaveTextContent(/partial/i);
  });

  it("a complete view shows no partial banner", () => {
    state.query = { ...state.query, data: view({ incomplete: false }) };
    render(<MultimorbidityShell patientId="p1" />);

    expect(screen.queryByTestId("multimorbidity-incomplete")).not.toBeInTheDocument();
  });
});
