import type { ReactNode } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { NewAdmissionForm } from "./NewAdmissionForm";

const push = vi.fn();
const createMutate = vi.fn();
let activeAdmissionData: unknown = { data: null };

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (s: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Chitungwiza Central" } }),
}));

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatient: () => ({
    data: { data: { id: "p1", attributes: { displayName: "Tapiwa Ncube", cpid: "CPID-ZW-1", gender: "MALE" } } },
    isPending: false,
  }),
}));

vi.mock("@/hooks/queries/useInpatient", () => ({
  useActiveAdmission: () => ({ data: activeAdmissionData }),
  useCreateAdmission: () => ({ mutate: createMutate, isPending: false, isError: false, error: null }),
}));

// Stub the bed picker so the form test doesn't need ward/bed queries.
vi.mock("./BedPickerField", () => ({
  BedPickerField: ({ onChange }: { onChange: (b: string, w: string) => void }) => (
    <button type="button" onClick={() => onChange("bed-9", "ward-2")}>pick-bed</button>
  ),
}));

function renderForm(encounterId: string | null) {
  return render(<NewAdmissionForm patientId="p1" encounterId={encounterId} source="visit-outcome" />);
}

describe("NewAdmissionForm", () => {
  beforeEach(() => {
    push.mockReset();
    createMutate.mockReset();
    activeAdmissionData = { data: null };
  });

  it("submits an admission with the resolved CPID, encounter and facility", () => {
    renderForm("enc-1");
    fireEvent.click(screen.getByText("pick-bed"));
    fireEvent.click(screen.getByRole("button", { name: /admit patient/i }));

    expect(createMutate).toHaveBeenCalledTimes(1);
    const [body] = createMutate.mock.calls[0];
    expect(body).toMatchObject({
      subjectCpid: "CPID-ZW-1",
      encounterId: "enc-1",
      facilityId: "facility-1",
      admissionType: "EMERGENCY",
      bedId: "bed-9",
      wardId: "ward-2",
    });
  });

  it("blocks submission and links to the open episode when one is already active", () => {
    activeAdmissionData = { data: [{ admissionRef: "adm-existing" }] };
    renderForm("enc-1");

    expect(screen.getByText(/already has an active admission/i)).toBeTruthy();
    const link = screen.getByText(/open the current episode/i).closest("a");
    expect(link?.getAttribute("href")).toBe("/clinical/inpatient/admissions/adm-existing");

    fireEvent.click(screen.getByRole("button", { name: /admit patient/i }));
    expect(createMutate).not.toHaveBeenCalled();
  });

  it("does not submit without an encounter context", () => {
    renderForm(null);
    expect(screen.getByText(/no encounter context/i)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: /admit patient/i }));
    expect(createMutate).not.toHaveBeenCalled();
  });
});
