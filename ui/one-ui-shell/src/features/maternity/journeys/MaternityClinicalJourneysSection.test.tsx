import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";

const submitMutate = vi.fn();
const pncMaternalMutate = vi.fn();

vi.mock("@/hooks/queries/useGovernedFormDefinition", () => ({
  useGovernedFormDefinition: () => ({
    isLoading: false,
    isError: false,
    definition: { id: "t", title: "T", sections: [] },
    catalogEntry: { version: 1 },
  }),
}));

vi.mock("@/hooks/queries/useEncounterForms", () => ({
  useSubmitEncounterForm: () => ({ mutateAsync: submitMutate, isPending: false }),
}));

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatient: () => ({
    data: { data: { id: "P1", attributes: { dateOfBirth: "1990-01-01", gender: "FEMALE" } } },
  }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({ useRoleGroup: () => ({ isClinical: true }) }));

vi.mock("@/hooks/queries/useMecAssess", () => ({
  useMecAssess: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));

vi.mock("@/hooks/queries/usePostnatalAssess", () => ({
  usePncMaternalAssess: () => ({ mutate: pncMaternalMutate, isPending: false }),
  usePncNewbornAssess: () => ({ mutate: vi.fn(), isPending: false }),
  screeningIncomplete: (a: Record<string, unknown>) => a.screeningComplete !== "YES",
}));

vi.mock("@/lib/clinical-forms/clinical-form-renderer/DakFormRenderer", () => ({
  DakFormRenderer: ({ onSubmit }: { onSubmit: (v: Record<string, unknown>) => void }) => (
    <button type="button" data-testid="mock-form-submit" onClick={() => onSubmit({ screeningComplete: "NO" })}>
      Submit
    </button>
  ),
}));

import { MaternityClinicalJourneysSection } from "./MaternityClinicalJourneysSection";

describe("MaternityClinicalJourneysSection", () => {
  beforeEach(() => {
    submitMutate.mockReset();
    pncMaternalMutate.mockReset();
    submitMutate.mockResolvedValue({});
  });

  it("renders forced journey tabs", () => {
    render(<MaternityClinicalJourneysSection patientId="P1" encounterId="E1" hasActiveEncounter />);
    expect(screen.getByTestId("journey-tab-impilo.anc.contact1.v1")).toBeInTheDocument();
    expect(screen.getByTestId("anc-first-contact-panel")).toBeInTheDocument();
  });

  it("PNC maternal: incomplete screening is honest", async () => {
    const user = userEvent.setup();
    render(<MaternityClinicalJourneysSection patientId="P1" encounterId="E1" hasActiveEncounter />);
    await user.click(screen.getByTestId("journey-tab-impilo.pnc.maternal.contact.v1"));
    await user.click(screen.getByTestId("mock-form-submit"));
    expect(pncMaternalMutate).not.toHaveBeenCalled();
    expect(screen.getByTestId("pnc-maternal-incomplete-screen")).toBeInTheDocument();
  });

  it("PNC newborn links young-infant path", async () => {
    const user = userEvent.setup();
    render(<MaternityClinicalJourneysSection patientId="P1" encounterId="E1" hasActiveEncounter />);
    await user.click(screen.getByTestId("journey-tab-impilo.pnc.newborn.contact.v1"));
    expect(screen.getByTestId("young-infant-path-link").querySelector("a")?.getAttribute("href")).toBe("/ehr/P1/paediatrics");
  });
});
