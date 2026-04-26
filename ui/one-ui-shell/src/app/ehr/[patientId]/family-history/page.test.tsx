import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import FamilyHistoryPage from "./page";

vi.mock("next/navigation", () => ({ useParams: () => ({ patientId: "patient-1" }) }));
vi.mock("@/components/EHRLayout", () => ({ EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div> }));
vi.mock("@/hooks/useFacilityStore", () => ({ useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) => selector({ facility: { id: "facility-1", name: "Harare Central" } }) }));
vi.mock("@/hooks/queries/useEncounters", () => ({ useEncounters: () => ({ data: { data: [{ id: "enc-1", attributes: { status: "IN_PROGRESS", encounterType: "OUTPATIENT", startedAt: "2026-04-08T09:00:00.000Z" } }] } }) }));
vi.mock("@/hooks/queries/useStructuredHistory", () => ({
  __esModule: true,
  useFamilyHistory: () => ({
    data: {
      data: [
        {
          id: "fm-1",
          name: "John M.",
          relationship: "Father",
          age: 68,
          deceased: false,
          deceasedAge: null,
          causeOfDeath: null,
          conditions: [{ id: "fc-1", condition: "Type 2 Diabetes Mellitus", onsetAge: 52, status: "Active", notes: "On insulin" }],
        },
        {
          id: "fm-2",
          name: "Robert M.",
          relationship: "Grandfather",
          age: null,
          deceased: true,
          deceasedAge: 72,
          causeOfDeath: "Myocardial Infarction",
          conditions: [{ id: "fc-2", condition: "Type 2 Diabetes Mellitus", onsetAge: 48, status: "Deceased", notes: "" }],
        },
      ],
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
}));

describe("FamilyHistoryPage", () => {
  it("surfaces hereditary continuity into conditions and planning", () => {
    render(<FamilyHistoryPage />);

    expect(screen.getByText("Keep hereditary risk visible so current planning and preventive decisions stay tied to the wider family story")).toBeInTheDocument();
    expect(screen.getByText("Family continuity")).toBeInTheDocument();
    expect(screen.getByText("Risk clusters")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Conditions" })).toHaveAttribute("href", "/ehr/patient-1/conditions");
    expect(screen.getByRole("link", { name: "Care Plans" })).toHaveAttribute("href", "/ehr/patient-1/care-plans");
  });
});
