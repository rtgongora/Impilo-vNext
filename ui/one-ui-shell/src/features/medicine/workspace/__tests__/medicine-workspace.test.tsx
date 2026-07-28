import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { groupProblems, openEnrolments, exitedEnrolments, isConfidential } from "../medicine-summary";
import type { ProgrammeEnrolment } from "@/hooks/queries/usePrograms";
import type { ConditionResource } from "@/hooks/queries/useConditions";

// ── Pure logic ────────────────────────────────────────────────────────────────
// Tested directly, without React, the way paediatric-age.ts is: these are the clinical rules and
// they should fail here rather than through a rendered component.

const enrolment = (over: Partial<ProgrammeEnrolment>): ProgrammeEnrolment =>
  ({
    enrolment_id: "e1", subject_cpid: "c1", programme: "HIV_CARE", status: "ON_TREATMENT",
    enrolled_on: "2026-01-01", confidential: true, ...over,
  } as ProgrammeEnrolment);

const condition = (name: string, status: string): ConditionResource =>
  ({
    id: name, type: "condition",
    attributes: {
      patientId: "p1", encounterId: null, conditionName: name, icdCode: "B20",
      category: "DIAGNOSIS", clinicalStatus: status, severity: "MODERATE", onsetDate: null,
      recordedBy: "dr", notes: null, createdAt: "2026-01-01",
    },
  } as ConditionResource);

describe("medicine-summary", () => {
  it("counts a relapse and a recurrence as ACTIVE problems, not resolved ones", () => {
    // The whole point of pct's status widening: a returning disease must not read as a finished one.
    const groups = groupProblems([
      condition("hiv", "ACTIVE"),
      condition("tb", "RELAPSE"),
      condition("asthma", "RECURRENCE"),
      condition("old", "RESOLVED"),
    ]);
    expect(groups.active.map((c) => c.id).sort()).toEqual(["asthma", "hiv", "tb"]);
    expect(groups.inactive.map((c) => c.id)).toEqual(["old"]);
  });

  it("keeps remission separate from both active and resolved", () => {
    // Remission is neither. Collapsing it into either direction loses the distinction the record
    // exists to hold.
    const groups = groupProblems([condition("ca", "REMISSION")]);
    expect(groups.active).toHaveLength(0);
    expect(groups.inactive).toHaveLength(0);
    expect(groups.remission.map((c) => c.id)).toEqual(["ca"]);
  });

  it("treats an interrupted treatment as still enrolled, and an exit as ended", () => {
    // TREATMENT_INTERRUPTED is a patient still in the programme who has stopped attending — the
    // single most important person to see on this screen, so it must not fall out of the open list.
    const list = [
      enrolment({ enrolment_id: "open", status: "TREATMENT_INTERRUPTED" }),
      enrolment({ enrolment_id: "gone", status: "EXITED", exit_reason: "LOST_TO_FOLLOW_UP" }),
    ];
    expect(openEnrolments(list).map((e) => e.enrolment_id)).toEqual(["open"]);
    expect(exitedEnrolments(list).map((e) => e.enrolment_id)).toEqual(["gone"]);
  });

  it("falls back to the HIV programmes when the server omits the confidential flag", () => {
    // An older payload must not cause an HIV enrolment to render without its badge — the badge is
    // the cue a clinician uses before discussing the record aloud.
    expect(isConfidential(enrolment({ confidential: undefined as never }))).toBe(true);
    expect(isConfidential(enrolment({ programme: "TB_TREATMENT", confidential: undefined as never }))).toBe(false);
    // An explicit server value always wins over the fallback.
    expect(isConfidential(enrolment({ programme: "TB_TREATMENT", confidential: true }))).toBe(true);
  });
});

// ── The shell's honesty contract ──────────────────────────────────────────────

const state = vi.hoisted(() => ({
  patient: { data: undefined as unknown, isError: false, isLoading: false },
  conditions: { data: undefined as unknown, isError: false, isLoading: false },
  allergies: { data: undefined as unknown, isError: false, isLoading: false },
  programmes: { data: undefined as unknown, isError: false, isLoading: false },
}));

vi.mock("@/hooks/queries/usePatients", () => ({ usePatient: () => state.patient }));
vi.mock("@/hooks/queries/useConditions", () => ({ useConditions: () => state.conditions }));
vi.mock("@/hooks/queries/useAllergies", () => ({ useAllergies: () => state.allergies }));
vi.mock("@/hooks/queries/usePrograms", () => ({ useProgrammeEnrolments: () => state.programmes }));

// eslint-disable-next-line import/first
import { MedicineWorkspaceShell } from "../MedicineWorkspaceShell";

describe("MedicineWorkspaceShell", () => {
  beforeEach(() => {
    state.patient = { data: { data: { attributes: {} } }, isError: false, isLoading: false };
    state.conditions = { data: { data: [] }, isError: false, isLoading: false };
    state.allergies = { data: { data: [] }, isError: false, isLoading: false };
    state.programmes = { data: { data: [] }, isError: false, isLoading: false };
  });

  it("a failed programme read never renders as 'not in any programme'", () => {
    state.programmes = { data: undefined, isError: true, isLoading: false };
    render(<MedicineWorkspaceShell patientId="p1" />);

    // Both halves matter: the failure must be stated AND the empty-state claim must be absent.
    expect(screen.getByTestId("programmes-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("programmes-empty")).not.toBeInTheDocument();
    expect(screen.queryByText(/No open HIV or TB programme enrolment/i)).not.toBeInTheDocument();
  });

  it("a failed problem-list read never claims the patient has no active conditions", () => {
    state.conditions = { data: undefined, isError: true, isLoading: false };
    render(<MedicineWorkspaceShell patientId="p1" />);
    expect(screen.getByTestId("problems-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("problems-empty")).not.toBeInTheDocument();
  });

  it("a failed allergy read never reads as 'no known allergies'", () => {
    state.allergies = { data: undefined, isError: true, isLoading: false };
    render(<MedicineWorkspaceShell patientId="p1" />);
    expect(screen.getByTestId("allergies-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("allergies-empty")).not.toBeInTheDocument();
  });

  it("one failed block does not blank the others — each degrades independently", () => {
    // The failure this prevents: a single broken query making the whole workspace look empty.
    state.programmes = { data: undefined, isError: true, isLoading: false };
    state.conditions = { data: { data: [condition("hiv", "ACTIVE")] }, isError: false, isLoading: false };
    render(<MedicineWorkspaceShell patientId="p1" />);

    expect(screen.getByTestId("programmes-unavailable")).toBeInTheDocument();
    expect(screen.getByText("hiv")).toBeInTheDocument();
    expect(screen.queryByTestId("problems-unavailable")).not.toBeInTheDocument();
  });

  it("when everything reads cleanly the empty states are the ones shown", () => {
    render(<MedicineWorkspaceShell patientId="p1" />);
    expect(screen.getByTestId("programmes-empty")).toBeInTheDocument();
    expect(screen.queryByTestId("programmes-unavailable")).not.toBeInTheDocument();
    expect(screen.queryByTestId("medicine-workspace-unavailable")).not.toBeInTheDocument();
  });

  it("an ended enrolment is shown with its reason rather than silently dropped", () => {
    // "Never enrolled" and "stopped coming" must not share the same blank space.
    state.programmes = {
      data: { data: [enrolment({ status: "EXITED", exit_reason: "LOST_TO_FOLLOW_UP" })] },
      isError: false, isLoading: false,
    };
    render(<MedicineWorkspaceShell patientId="p1" />);
    expect(screen.getByTestId("programmes-exited")).toHaveTextContent(/lost to follow up/i);
  });

  it("a confidential programme carries its badge", () => {
    state.programmes = {
      data: { data: [enrolment({ programme: "HIV_CARE", confidential: true })] },
      isError: false, isLoading: false,
    };
    render(<MedicineWorkspaceShell patientId="p1" />);
    expect(screen.getByText(/Confidential/i)).toBeInTheDocument();
  });
});
