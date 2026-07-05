import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";

const state: {
  data: { data: Array<Record<string, unknown>> } | undefined;
  isLoading: boolean;
  isError: boolean;
} = { data: undefined, isLoading: false, isError: false };

vi.mock("@/hooks/queries/usePharmacy", () => ({
  usePrescriptions: () => state,
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
});
