import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";

const state: {
  data: { data: Array<Record<string, unknown>> } | undefined;
  isLoading: boolean;
  isError: boolean;
} = { data: undefined, isLoading: false, isError: false };

const extractionsState: {
  data: { data: Array<Record<string, unknown>> } | undefined;
  isLoading: boolean;
  isError: boolean;
} = { data: undefined, isLoading: false, isError: false };

vi.mock("@/hooks/queries/usePharmacy", () => ({
  usePrescriptions: () => state,
}));

vi.mock("@/hooks/queries/useEncounterForms", () => ({
  useEncounterFormExtractions: () => extractionsState,
}));

import { EncounterMedicationsPanel } from "./EncounterMedicationsPanel";

function renderPanel() {
  return render(<EncounterMedicationsPanel patientId="p1" encounterId="42" />);
}

describe("EncounterMedicationsPanel", () => {
  beforeEach(() => {
    state.data = undefined;
    state.isLoading = false;
    state.isError = false;
    extractionsState.data = undefined;
    extractionsState.isLoading = false;
    extractionsState.isError = false;
  });

  it("lists prescriptions with medication, dosage and pharmacy status", () => {
    state.data = {
      data: [
        {
          id: "rx-1",
          type: "prescription",
          attributes: {
            patientId: "p1",
            prescriberId: "dr-1",
            status: "DISPENSED",
            items: [{ medication: "Amoxicillin", dosage: "500mg TDS", quantity: 15 }],
          },
        },
      ],
    };
    renderPanel();
    expect(screen.getByText("Amoxicillin 500mg TDS")).toBeInTheDocument();
    expect(screen.getByText("DISPENSED")).toBeInTheDocument();
  });

  it("shows an honest empty state when the patient has no prescriptions", () => {
    state.data = { data: [] };
    renderPanel();
    expect(screen.getByText("No prescriptions on record for this patient.")).toBeInTheDocument();
  });

  it("shows an honest unavailable state when pharmacy-service is unreachable", () => {
    state.isError = true;
    renderPanel();
    expect(screen.getByRole("alert")).toHaveTextContent("Pharmacy service unreachable");
    // Never fabricate medication rows on failure.
    expect(screen.queryByText(/DISPENSED|ACTIVE/)).not.toBeInTheDocument();
  });

  it("explains the prescribing seam (structured PRESCRIBE forms, not a fake inline prescriber)", () => {
    state.data = { data: [] };
    renderPanel();
    expect(screen.getByText(/PRESCRIBE/)).toBeInTheDocument();
    expect(screen.getByText(/Open pharmacy workspace/)).toBeInTheDocument();
  });

  it("shows medication requests routed from this encounter with their OROS order ref", () => {
    state.data = { data: [] };
    extractionsState.data = {
      data: [
        {
          extractedId: "x-1",
          responseId: "resp-1",
          resourceType: "MEDICATION_REQUEST",
          routeTarget: "OROS",
          status: "ROUTED",
          externalRef: "OROS-77",
          resourcePayload: JSON.stringify({
            code: "AMOX500",
            display: "Amoxicillin",
            dose: "500mg",
            route: "PO",
            frequency: "TDS",
          }),
        },
        // Non-medication extractions must not leak into the medications panel.
        {
          extractedId: "x-2",
          responseId: "resp-1",
          resourceType: "OBSERVATION",
          routeTarget: "BUTANO",
          status: "PENDING",
        },
      ],
    };
    renderPanel();
    expect(screen.getByTestId("encounter-medication-requests")).toBeInTheDocument();
    expect(screen.getByText("Amoxicillin · 500mg · PO · TDS")).toBeInTheDocument();
    expect(screen.getByText(/ROUTED to pharmacy · order OROS-77/)).toBeInTheDocument();
    expect(screen.queryByText(/PENDING/)).not.toBeInTheDocument();
  });

  it("shows FAILED medication requests honestly, naming the R1 contract divergence", () => {
    state.data = { data: [] };
    extractionsState.data = {
      data: [
        {
          extractedId: "x-3",
          responseId: "resp-2",
          resourceType: "MEDICATION_REQUEST",
          routeTarget: "OROS",
          status: "FAILED",
          resourcePayload: JSON.stringify({ code: "PARA1G" }),
        },
      ],
    };
    renderPanel();
    expect(screen.getByText(/FAILED — not received by OROS/)).toBeInTheDocument();
    expect(screen.getByText(/tracked R1/)).toBeInTheDocument();
  });
});
