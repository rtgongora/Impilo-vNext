import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ConditionsPage from "./page";

type ConditionsState = {
  data?: { data: Array<{ id: string; attributes: Record<string, unknown> }> };
  isLoading: boolean;
  isError: boolean;
};

type CreateState = { mutate: (...args: unknown[]) => void; isPending: boolean; isError: boolean; error?: unknown };

const { conditionsState, createState } = vi.hoisted(() => ({
  conditionsState: { current: {} as ConditionsState },
  createState: { current: {} as CreateState },
}));

const POPULATED: ConditionsState = {
  data: {
    data: [
      { id: "cond-1", attributes: { conditionName: "Hypertension", icdCode: "I10", category: "CHRONIC", clinicalStatus: "ACTIVE", severity: "MODERATE", onsetDate: null, notes: null } },
      { id: "cond-2", attributes: { conditionName: "Pneumonia", icdCode: null, category: "PROBLEM_LIST", clinicalStatus: "RESOLVED", severity: "MILD", onsetDate: null, notes: null } },
    ],
  },
  isLoading: false,
  isError: false,
};

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
}));

vi.mock("@/components/EHRLayout", () => ({
  EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/queries/useEncounters", () => ({
  useEncounters: () => ({
    data: {
      data: [
        {
          id: "enc-1",
          attributes: {
            status: "IN_PROGRESS",
            encounterType: "OUTPATIENT",
            startedAt: "2026-04-08T09:00:00.000Z",
          },
        },
      ],
    },
  }),
}));

vi.mock("@/hooks/queries/useConditions", async (importOriginal) => ({
  // asDuplicateReport is a pure narrowing helper and is deliberately NOT mocked — mocking it
  // would mean the test proves the page renders whatever the mock says, rather than that the
  // page recognises a real 409 body.
  ...(await importOriginal<object>()),
  useConditions: () => conditionsState.current,
  useCreateCondition: () => createState.current,
  useResolveCondition: () => ({ mutate: vi.fn(), isPending: false }),
}));

describe("ConditionsPage", () => {
  beforeEach(() => {
    conditionsState.current = POPULATED;
    createState.current = { mutate: vi.fn(), isPending: false, isError: false };
  });

  it("adds encounter-aware review scaffolding to the problem list", () => {
    render(<ConditionsPage />);

    expect(screen.getByText("Keep the active problem list aligned with the encounter story and medication safety review")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("Resolved")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Medications" })).toHaveAttribute("href", "/ehr/patient-1/medications");
  });

  /**
   * The distinction the whole pack rests on: "this patient has no conditions" is an affirmative
   * clinical finding, and it must never be produced by a read that failed. The BFF returns 502
   * for exactly this reason; the page has to stop turning it back into an empty list.
   */
  describe("when the problem list cannot be read", () => {
    beforeEach(() => {
      conditionsState.current = { data: undefined, isLoading: false, isError: true };
    });

    it("says the list is unavailable", () => {
      render(<ConditionsPage />);
      expect(screen.getByText(/problem list unavailable/i)).toBeInTheDocument();
      expect(
        screen.getByText(/not a record that the patient has no conditions/i)
      ).toBeInTheDocument();
    });

    it("never claims the patient has no conditions", () => {
      render(<ConditionsPage />);
      expect(screen.queryByText(/no conditions recorded yet/i)).not.toBeInTheDocument();
    });
  });

  /**
   * A duplicate is the server asking a question, not reporting a failure. Rendering it as
   * "failed, please try again" is a dead end — the retry returns the same 409 forever — and it
   * throws away the existing problem, which is the one thing needed to answer.
   */
  describe("when the condition is already on the list", () => {
    const duplicate = {
      status: 409,
      error: "condition_already_on_list",
      message: "A problem matching 'Hypertension' is already on this patient's list (status ACTIVE).",
      existing: {
        id: "cond-1",
        attributes: { conditionName: "Hypertension", clinicalStatus: "ACTIVE", onsetDate: "2026-01-15" },
      },
    };

    beforeEach(() => {
      createState.current = { mutate: vi.fn(), isPending: false, isError: true, error: duplicate };
    });

    it("offers the two answers instead of a retry", async () => {
      render(<ConditionsPage />);
      await userEvent.click(screen.getByRole("button", { name: /add condition/i }));

      expect(screen.getByText(/already on this patient's problem list/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /it has recurred/i })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /a separate problem/i })).toBeInTheDocument();
    });

    it("shows the condition already recorded, so the question can be answered", async () => {
      render(<ConditionsPage />);
      await userEvent.click(screen.getByRole("button", { name: /add condition/i }));
      expect(screen.getAllByText(/Hypertension/i).length).toBeGreaterThan(0);
    });

    it("does not tell the clinician the write failed", async () => {
      render(<ConditionsPage />);
      await userEvent.click(screen.getByRole("button", { name: /add condition/i }));
      expect(screen.queryByText(/was not saved/i)).not.toBeInTheDocument();
    });

    it("sends the chosen answer back rather than repeating the same request", async () => {
      render(<ConditionsPage />);
      await userEvent.click(screen.getByRole("button", { name: /add condition/i }));
      await userEvent.click(screen.getByRole("button", { name: /it has recurred/i }));

      const payload = (createState.current.mutate as ReturnType<typeof vi.fn>).mock.calls[0][0];
      expect(payload.duplicateResolution).toBe("SAME_PROBLEM_RETURNING");
    });
  });

  it("says there are none only when the record actually answered", () => {
    conditionsState.current = { data: { data: [] }, isLoading: false, isError: false };
    render(<ConditionsPage />);

    expect(screen.getByText(/no conditions recorded yet/i)).toBeInTheDocument();
    expect(screen.queryByText(/problem list unavailable/i)).not.toBeInTheDocument();
  });
});
