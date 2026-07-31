import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { ConditionalClerkingPanel } from "../ConditionalClerkingPanel";

vi.mock("../useClerkingContinuityContext", () => ({
  useClerkingContinuityContext: () => ({
    patientId: "p1",
    activeProblems: [],
    resolvedProblems: [],
    episodes: [],
    medications: [],
    adherenceFindings: [],
    allergies: [],
    immunizations: [],
    procedures: [],
    functional: [],
    family: [],
    carePlans: [],
    goals: [],
    directives: [],
    admissions: [],
    maternity: null,
    unavailable: [],
    isLoading: false,
  }),
}));

vi.mock("@/hooks/queries/useEncounters", () => ({
  useEncounters: () => ({
    data: {
      data: [{ id: "enc-1", attributes: { isOpen: true, encounterType: "OUTPATIENT", startedAt: "2026-07-31T00:00:00Z" } }],
    },
  }),
}));

vi.mock("@tanstack/react-query", async () => {
  const actual = await vi.importActual<typeof import("@tanstack/react-query")>("@tanstack/react-query");
  return {
    ...actual,
    useQuery: () => ({ data: { data: [] }, isLoading: false }),
    useMutation: () => ({ mutate: vi.fn(), isPending: false }),
    useQueryClient: () => ({ invalidateQueries: vi.fn() }),
  };
});

describe("ConditionalClerkingPanel", () => {
  it("does not pre-select still true", () => {
    render(<ConditionalClerkingPanel patientId="p1" />);
    const radios = screen.getAllByRole("radio") as HTMLInputElement[];
    expect(radios.length).toBeGreaterThan(0);
    expect(radios.every((r) => !r.checked)).toBe(true);
    expect(screen.getByText(/Silence is not confirmation|omitting a stance/i)).toBeTruthy();
  });
});
