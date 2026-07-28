import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";

const state = vi.hoisted(() => ({
  facility: { id: "fac-1" } as { id: string } | null,
  admission: { data: undefined as unknown, isError: false, isLoading: false },
  rounds: { data: undefined as unknown, isError: false, isLoading: false },
  conditions: { data: undefined as unknown, isError: false, isLoading: false },
  programmes: { data: undefined as unknown, isError: false, isLoading: false },
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (sel: (s: { facility: unknown }) => unknown) => sel({ facility: state.facility }),
}));
vi.mock("@/hooks/queries/useInpatient", () => ({
  useActiveAdmission: () => state.admission,
  useWardRounds: () => state.rounds,
  useStartWardRound: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
  useAddWardRoundEntry: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));
vi.mock("@/hooks/queries/useConditions", () => ({ useConditions: () => state.conditions }));
vi.mock("@/hooks/queries/usePrograms", () => ({ useProgrammeEnrolments: () => state.programmes }));

// eslint-disable-next-line import/first
import { MedicalWardRoundShell } from "../MedicalWardRoundShell";

describe("MedicalWardRoundShell — admission status has three states, not two", () => {
  beforeEach(() => {
    state.facility = { id: "fac-1" };
    state.admission = { data: undefined, isError: false, isLoading: false };
    state.rounds = { data: { data: [] }, isError: false, isLoading: false };
    state.conditions = { data: { data: [] }, isError: false, isLoading: false };
    state.programmes = { data: { data: [] }, isError: false, isLoading: false };
  });

  it("a FAILED admission read never renders as 'not admitted'", () => {
    // The harm this prevents: telling a doctor a patient is not in a bed when they are, which sends
    // them away from someone who is admitted.
    state.admission = { data: undefined, isError: true, isLoading: false };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("admission-unknown")).toHaveTextContent(/not.*a statement that they are not admitted/i);
    expect(screen.queryByTestId("admission-none")).not.toBeInTheDocument();
  });

  it("no facility in context is 'not checked', not 'not admitted'", () => {
    // We have not asked the question, which is not an answer to it.
    state.facility = null;
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("admission-unknown")).toHaveTextContent(/has not been checked/i);
    expect(screen.queryByTestId("admission-none")).not.toBeInTheDocument();
  });

  it("a clean read with no admission says so plainly", () => {
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("admission-none")).toBeInTheDocument();
    expect(screen.queryByTestId("admission-unknown")).not.toBeInTheDocument();
  });

  it("an active admission shows the ward and enables the entry form", () => {
    state.admission = { data: { data: { admissionRef: "adm-1", wardName: "Medical A" } }, isError: false, isLoading: false };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("admission-active")).toHaveTextContent("Medical A");
    expect(screen.getByTestId("field-assessment")).toBeInTheDocument();
    expect(screen.queryByTestId("entry-requires-admission")).not.toBeInTheDocument();
  });

  it("without an admission the entry form is not offered at all", () => {
    // Rather than showing a form whose save cannot succeed.
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("entry-requires-admission")).toBeInTheDocument();
    expect(screen.queryByTestId("field-assessment")).not.toBeInTheDocument();
  });

  it("a failed problem-list read never claims there are no active problems", () => {
    state.conditions = { data: undefined, isError: true, isLoading: false };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("ward-problems-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("ward-problems-empty")).not.toBeInTheDocument();
  });

  it("the problem list is on screen while the round entry is written", () => {
    // The reason this page exists: the ward-centred round surfaces show assessment/plan with no
    // sight of the problems being assessed.
    state.admission = { data: { data: { admissionRef: "adm-1", wardName: "Medical A" } }, isError: false, isLoading: false };
    state.conditions = {
      data: { data: [{ id: "c1", type: "condition", attributes: { conditionName: "Type 2 diabetes", icdCode: "E11", clinicalStatus: "ACTIVE" } }] },
      isError: false, isLoading: false,
    };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("ward-problems")).toHaveTextContent("Type 2 diabetes");
    expect(screen.getByTestId("ward-round-entry")).toBeInTheDocument();
  });
});
