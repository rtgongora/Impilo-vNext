import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";

const state = vi.hoisted(() => ({
  facility: { id: "fac-1" } as { id: string } | null,
  query: { data: undefined as unknown, isError: false, isLoading: false },
  lastArgs: [] as unknown[],
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (sel: (s: { facility: unknown }) => unknown) => sel({ facility: state.facility }),
}));
vi.mock("@/hooks/queries/useChronicRegister", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/hooks/queries/useChronicRegister")>();
  // CHRONIC_REGISTERS is a plain constant and is deliberately NOT mocked — mocking it would make the
  // tab coverage assertion prove the tabs match a fake list rather than the real one.
  return {
    ...actual,
    useChronicRegister: (...args: unknown[]) => {
      state.lastArgs = args;
      return state.query;
    },
  };
});

// eslint-disable-next-line import/first
import { ChronicRegisterShell } from "../ChronicRegisterShell";
// eslint-disable-next-line import/first
import { CHRONIC_REGISTERS } from "@/hooks/queries/useChronicRegister";

const entry = (over: Record<string, unknown> = {}) => ({
  enrolment_id: "e1", subject_cpid: "CPID-1", programme_number: "HTN-1",
  status: "ON_TREATMENT", enrolled_on: "2026-01-01", managing_facility_id: "fac-1",
  control_status: null, control_assessed_on: null, individualised_target: null, ...over,
});

const register = (over: Record<string, unknown> = {}) => ({
  data: {
    programme: "HYPERTENSION", facility_id: "fac-1", entries: [], total: 0, never_assessed: 0,
    control_status_null_means_never_assessed: "A null control_status means nobody has assessed control.",
    ...over,
  },
});

describe("ChronicRegisterShell — a blank control column would read as 'fine'", () => {
  beforeEach(() => {
    state.facility = { id: "fac-1" };
    state.query = { data: undefined, isError: false, isLoading: false };
  });

  it("a never-assessed entry says 'Not assessed' rather than rendering blank", () => {
    // The harm: every reader of a table treats an empty cell in a status column as "nothing wrong".
    // These are the people the recall list exists to find, so they must be the loudest row, not the
    // quietest one.
    state.query = { ...state.query, data: register({ entries: [entry()], total: 1, never_assessed: 1 }) };
    render(<ChronicRegisterShell />);

    expect(screen.getByTestId("control-unassessed-e1")).toHaveTextContent("Not assessed");
    expect(screen.queryByTestId("control-e1")).not.toBeInTheDocument();
  });

  it("an assessed entry shows its value and the date it was assessed", () => {
    // A control status with no date is a claim about now built on an assessment of unknown age.
    state.query = {
      ...state.query,
      data: register({
        entries: [entry({ control_status: "NOT_CONTROLLED", control_assessed_on: "2026-06-01" })],
        total: 1,
      }),
    };
    render(<ChronicRegisterShell />);

    expect(screen.getByTestId("control-e1")).toHaveTextContent("Above target");
    expect(screen.getByTestId("control-e1")).toHaveTextContent("2026-06-01");
  });

  it("an individualised target is shown, so nobody re-escalates one that was deliberately relaxed", () => {
    state.query = {
      ...state.query,
      data: register({
        entries: [entry({ individualised_target: "HbA1c 8.0% — relaxed for frailty" })],
        total: 1,
      }),
    };
    render(<ChronicRegisterShell />);

    expect(screen.getByTestId("register-row-e1")).toHaveTextContent("relaxed for frailty");
  });

  it("no individualised target falls back to naming the guideline default explicitly", () => {
    state.query = { ...state.query, data: register({ entries: [entry()], total: 1 }) };
    render(<ChronicRegisterShell />);

    expect(screen.getByTestId("register-row-e1")).toHaveTextContent("Guideline default");
  });

  it("a FAILED read never renders as nobody being on the register", () => {
    state.query = { data: undefined, isError: true, isLoading: false };
    render(<ChronicRegisterShell />);

    expect(screen.getByTestId("register-failed")).toHaveTextContent(/not.*a statement that nobody is on it/i);
    expect(screen.queryByTestId("register-empty")).not.toBeInTheDocument();
  });

  it("a clean but empty register says the register WAS read", () => {
    // "Nobody is on it" and "we could not look" must never render the same way.
    state.query = { ...state.query, data: register({ entries: [], total: 0 }) };
    render(<ChronicRegisterShell />);

    expect(screen.getByTestId("register-empty")).toHaveTextContent(/register was read/i);
    expect(screen.queryByTestId("register-failed")).not.toBeInTheDocument();
  });

  it("switching tab re-queries for the other register", () => {
    state.query = { ...state.query, data: register() };
    render(<ChronicRegisterShell />);
    fireEvent.click(screen.getByTestId("register-tab-DIABETES"));

    expect(state.lastArgs[0]).toBe("DIABETES");
    expect(state.lastArgs[1]).toBe("fac-1");
  });

  it("no facility in context reads the whole tenant and says so", () => {
    // Rather than silently showing one facility's register as though it were everyone's.
    state.facility = null;
    state.query = { ...state.query, data: register() };
    render(<ChronicRegisterShell />);

    expect(screen.getByTestId("register-no-facility")).toBeInTheDocument();
    expect(state.lastArgs[1]).toBeNull();
  });

  it("every register the API serves has a tab, and none is invented", () => {
    state.query = { ...state.query, data: register() };
    render(<ChronicRegisterShell />);

    for (const r of CHRONIC_REGISTERS) {
      expect(screen.getByTestId(`register-tab-${r.programme}`)).toBeInTheDocument();
    }
  });

  it("no confidential programme is offered as a register tab", () => {
    // A patient-level HIV register is refused by the server. Offering a tab for it would present a
    // 403 as though it were a broken page, rather than as the deliberate protection it is.
    const offered = CHRONIC_REGISTERS.map((r) => r.programme as string);
    expect(offered).not.toContain("HIV_CARE");
    expect(offered).not.toContain("HIV_PREVENTION");
  });
});
