import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const state = vi.hoisted(() => ({
  facility: { id: "fac-1" } as { id: string } | null,
  admission: { data: undefined as unknown, isError: false, isLoading: false },
  rounds: { data: undefined as unknown, isError: false, isLoading: false },
  conditions: { data: undefined as unknown, isError: false, isLoading: false },
  programmes: { data: undefined as unknown, isError: false, isLoading: false },
  allergies: { data: undefined as unknown, isError: false, isLoading: false },
  prescriptions: { data: undefined as unknown, isError: false, isLoading: false },
  consultations: { data: undefined as unknown, isError: false, isLoading: false },
  fluidBalance: { data: undefined as unknown, isError: false, isLoading: false },
  startRoundMutate: vi.fn(),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (sel: (s: { facility: unknown }) => unknown) => sel({ facility: state.facility }),
}));
vi.mock("@/hooks/queries/useInpatient", () => ({
  useActiveAdmission: () => state.admission,
  useWardRounds: () => state.rounds,
  useStartWardRound: () => ({
    mutate: state.startRoundMutate,
    isPending: false,
    isError: false,
  }),
  useAddWardRoundEntry: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));
vi.mock("@/hooks/queries/useConditions", () => ({ useConditions: () => state.conditions }));
vi.mock("@/hooks/queries/usePrograms", () => ({ useProgrammeEnrolments: () => state.programmes }));
vi.mock("@/hooks/queries/useAllergies", () => ({ useAllergies: () => state.allergies }));
vi.mock("@/hooks/queries/usePharmacy", () => ({ usePrescriptions: () => state.prescriptions }));
vi.mock("@/hooks/queries/useConsultations", () => ({ useConsultations: () => state.consultations }));
vi.mock("@/hooks/queries/useFluidBalance", () => ({ useFluidBalance: () => state.fluidBalance }));

// eslint-disable-next-line import/first
import { MedicalWardRoundShell, WARD_ROUND_TYPES } from "../MedicalWardRoundShell";

const NOT_COMPOSED_PANELS = [
  "ward-oxygen",
  "ward-devices",
  "ward-nutrition",
  "ward-mobility",
  "ward-vte",
  "ward-infection",
  "ward-pending-results",
  "ward-required-obs",
  "ward-goals",
  "ward-discharge-barriers",
  "ward-escalation",
  "ward-ceiling-of-care",
];

describe("MedicalWardRoundShell — admission status has three states, not two", () => {
  beforeEach(() => {
    state.facility = { id: "fac-1" };
    state.admission = { data: undefined, isError: false, isLoading: false };
    state.rounds = { data: { data: [] }, isError: false, isLoading: false };
    state.conditions = { data: { data: [] }, isError: false, isLoading: false };
    state.programmes = { data: { data: [] }, isError: false, isLoading: false };
    state.allergies = { data: { data: [] }, isError: false, isLoading: false };
    state.prescriptions = { data: { data: [] }, isError: false, isLoading: false };
    state.consultations = { data: { data: [] }, isError: false, isLoading: false };
    state.fluidBalance = {
      data: { data: [], summary: { totalIntake: 0, totalOutput: 0, balance: 0 } },
      isError: false,
      isLoading: false,
    };
    state.startRoundMutate.mockClear();
  });

  it("a FAILED admission read never renders as 'not admitted'", () => {
    state.admission = { data: undefined, isError: true, isLoading: false };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("admission-unknown")).toHaveTextContent(/not.*a statement that they are not admitted/i);
    expect(screen.queryByTestId("admission-none")).not.toBeInTheDocument();
  });

  it("no facility in context is 'not checked', not 'not admitted'", () => {
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
    state.admission = {
      data: { data: { admissionRef: "adm-1", wardName: "Medical A" } },
      isError: false,
      isLoading: false,
    };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("admission-active")).toHaveTextContent("Medical A");
    expect(screen.getByTestId("field-assessment")).toBeInTheDocument();
    expect(screen.queryByTestId("entry-requires-admission")).not.toBeInTheDocument();
  });

  it("without an admission the entry form is not offered at all", () => {
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
    state.admission = {
      data: { data: { admissionRef: "adm-1", wardName: "Medical A" } },
      isError: false,
      isLoading: false,
    };
    state.conditions = {
      data: {
        data: [
          {
            id: "c1",
            type: "condition",
            attributes: {
              conditionName: "Type 2 diabetes",
              icdCode: "E11",
              clinicalStatus: "ACTIVE",
              severity: "MODERATE",
            },
          },
        ],
      },
      isError: false,
      isLoading: false,
    };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("ward-problems")).toHaveTextContent("Type 2 diabetes");
    expect(screen.getByTestId("ward-round-entry")).toBeInTheDocument();
  });
});

describe("MedicalWardRoundShell — §13 ward concern grid", () => {
  beforeEach(() => {
    state.facility = { id: "fac-1" };
    state.admission = {
      data: { data: { admissionRef: "adm-1", wardName: "Medical A" } },
      isError: false,
      isLoading: false,
    };
    state.rounds = { data: { data: [] }, isError: false, isLoading: false };
    state.conditions = { data: { data: [] }, isError: false, isLoading: false };
    state.programmes = { data: { data: [] }, isError: false, isLoading: false };
    state.allergies = { data: { data: [] }, isError: false, isLoading: false };
    state.prescriptions = { data: { data: [] }, isError: false, isLoading: false };
    state.consultations = { data: { data: [] }, isError: false, isLoading: false };
    state.fluidBalance = {
      data: { data: [], summary: { totalIntake: 0, totalOutput: 0, balance: 0 } },
      isError: false,
      isLoading: false,
    };
  });

  it("renders all eighteen §13 concern panels", () => {
    render(<MedicalWardRoundShell patientId="p1" />);
    const grid = screen.getByTestId("ward-concerns-grid");
    const panels = within(grid).getAllByRole("heading", { level: 3 });
    expect(panels).toHaveLength(18);
  });

  it("not-yet-composed concerns say so honestly — never empty-as-none", () => {
    render(<MedicalWardRoundShell patientId="p1" />);
    for (const id of NOT_COMPOSED_PANELS) {
      expect(screen.getByTestId(`${id}-not-composed`)).toHaveTextContent(
        /Not composed — no SoR\/read on this surface yet/i,
      );
      expect(screen.queryByTestId(`${id}-empty`)).not.toBeInTheDocument();
    }
  });

  it("a failed allergy read never claims there are no allergies", () => {
    state.allergies = { data: undefined, isError: true, isLoading: false };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("ward-allergies-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("ward-allergies-empty")).not.toBeInTheDocument();
  });

  it("composes allergies when the read succeeds", () => {
    state.allergies = {
      data: {
        data: [
          {
            id: "a1",
            type: "allergy",
            attributes: { allergen: "Penicillin", severity: "SEVERE" },
          },
        ],
      },
      isError: false,
      isLoading: false,
    };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("ward-allergies")).toHaveTextContent("Penicillin (SEVERE)");
  });

  it("composes current medicines from pharmacy prescriptions", () => {
    state.prescriptions = {
      data: {
        data: [
          {
            id: "rx1",
            type: "prescription",
            attributes: {
              status: "ACTIVE",
              items: [{ medication: "Metformin", dosage: "500mg BD" }],
            },
          },
        ],
      },
      isError: false,
      isLoading: false,
    };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("ward-medicines")).toHaveTextContent("Metformin 500mg BD");
  });

  it("a failed fluid-balance read is unavailable, not empty", () => {
    state.fluidBalance = { data: undefined, isError: true, isLoading: false };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("ward-fluid-balance-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("ward-fluid-balance-empty")).not.toBeInTheDocument();
  });

  it("composes severity from active problems", () => {
    state.conditions = {
      data: {
        data: [
          {
            id: "c1",
            type: "condition",
            attributes: {
              conditionName: "Heart failure",
              clinicalStatus: "ACTIVE",
              severity: "SEVERE",
            },
          },
        ],
      },
      isError: false,
      isLoading: false,
    };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("ward-severity")).toHaveTextContent("Heart failure");
    expect(screen.getByTestId("ward-severity")).toHaveTextContent("SEVERE");
  });

  it("composes open consultations", () => {
    state.consultations = {
      data: {
        data: [
          {
            consultation_id: "con-1",
            asked_of_service: "Nephrology",
            status: "REQUESTED",
            question: "AKI on CKD — dialyse?",
          },
        ],
      },
      isError: false,
      isLoading: false,
    };
    render(<MedicalWardRoundShell patientId="p1" />);
    expect(screen.getByTestId("ward-consultations")).toHaveTextContent("Nephrology");
    expect(screen.getByTestId("ward-consultations")).toHaveTextContent("AKI on CKD");
  });

  it("lists failed reads in the unavailable banner", () => {
    state.allergies = { data: undefined, isError: true, isLoading: false };
    state.prescriptions = { data: undefined, isError: true, isLoading: false };
    render(<MedicalWardRoundShell patientId="p1" />);
    const banner = screen.getByTestId("ward-unavailable");
    expect(banner).toHaveTextContent("allergies");
    expect(banner).toHaveTextContent("current medicines");
  });
});

describe("MedicalWardRoundShell — structured round type selector", () => {
  beforeEach(() => {
    state.facility = { id: "fac-1" };
    state.admission = {
      data: { data: { admissionRef: "adm-1", wardName: "Medical A" } },
      isError: false,
      isLoading: false,
    };
    state.rounds = { data: { data: [] }, isError: false, isLoading: false };
    state.conditions = { data: { data: [] }, isError: false, isLoading: false };
    state.programmes = { data: { data: [] }, isError: false, isLoading: false };
    state.allergies = { data: { data: [] }, isError: false, isLoading: false };
    state.prescriptions = { data: { data: [] }, isError: false, isLoading: false };
    state.consultations = { data: { data: [] }, isError: false, isLoading: false };
    state.fluidBalance = {
      data: { data: [], summary: { totalIntake: 0, totalOutput: 0, balance: 0 } },
      isError: false,
      isLoading: false,
    };
    state.startRoundMutate.mockClear();
  });

  it("offers all brief §13 structured round types", () => {
    render(<MedicalWardRoundShell patientId="p1" />);
    const select = screen.getByTestId("field-round-type") as HTMLSelectElement;
    const options = Array.from(select.options).map((o) => o.value);
    expect(options).toEqual([...WARD_ROUND_TYPES]);
  });

  it("passes the selected round type when starting a round", async () => {
    const user = userEvent.setup();
    render(<MedicalWardRoundShell patientId="p1" />);
    await user.selectOptions(screen.getByTestId("field-round-type"), "POST_TAKE");
    await user.type(screen.getByTestId("field-assessment"), "Stable overnight");
    await user.type(screen.getByTestId("field-plan"), "Continue treatment");
    await user.click(screen.getByTestId("save-round-entry"));
    expect(state.startRoundMutate).toHaveBeenCalledWith(
      { admissionRef: "adm-1", roundType: "POST_TAKE" },
      expect.any(Object),
    );
  });
});
