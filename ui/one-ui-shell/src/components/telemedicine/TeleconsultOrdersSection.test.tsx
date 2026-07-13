import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { TeleconsultOrdersSection } from "./TeleconsultOrdersSection";

const { createRx } = vi.hoisted(() => ({ createRx: vi.fn() }));

vi.mock("@/components/encounter/EncounterLabOrdersPanel", () => ({
  EncounterLabOrdersPanel: () => <div data-testid="lab-panel" />,
}));
vi.mock("@/components/encounter/EncounterImagingOrdersPanel", () => ({
  EncounterImagingOrdersPanel: () => <div data-testid="imaging-panel" />,
}));
vi.mock("@/hooks/queries/usePharmacy", () => ({
  useCreatePrescription: () => ({ mutateAsync: createRx, isPending: false }),
  usePrescriptions: () => ({ data: { data: [] }, isLoading: false }),
}));
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "prov-1", displayName: "Dr X" } }),
}));
vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (sel: (s: unknown) => unknown) => sel({ facility: { id: "fac-1" } }),
}));

describe("TeleconsultOrdersSection", () => {
  beforeEach(() => createRx.mockReset().mockResolvedValue({ data: {} }));

  it("mounts the lab + imaging rails and prescribes to the pharmacy SoR", async () => {
    render(<TeleconsultOrdersSection patientId="CPID-1" encounterId="enc-1" patientCpid="CPID-1" />);
    expect(screen.getByTestId("lab-panel")).toBeInTheDocument();
    expect(screen.getByTestId("imaging-panel")).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText(/Medication/), { target: { value: "Amoxicillin 500mg" } });
    fireEvent.change(screen.getByPlaceholderText(/Dosage/), { target: { value: "500mg" } });
    fireEvent.change(screen.getByPlaceholderText(/Frequency/), { target: { value: "TDS" } });
    fireEvent.click(screen.getByRole("button", { name: /^Prescribe$/ }));

    await waitFor(() => expect(createRx).toHaveBeenCalledTimes(1));
    expect(createRx).toHaveBeenCalledWith(
      expect.objectContaining({
        patient_id: "CPID-1",
        encounter_id: "enc-1",
        medication_name: "Amoxicillin 500mg",
        dosage: "500mg",
        frequency: "TDS",
        prescribed_by: "prov-1",
      }),
    );
  });
});
