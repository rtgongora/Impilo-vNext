import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ProceduresPage from "./page";

vi.mock("next/navigation", () => ({ useParams: () => ({ patientId: "patient-1" }) }));
vi.mock("@/components/EHRLayout", () => ({ EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div> }));
vi.mock("@/hooks/useFacilityStore", () => ({ useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) => selector({ facility: { id: "facility-1", name: "Harare Central Hospital" } }) }));
vi.mock("@/hooks/queries/useEncounters", () => ({ useEncounters: () => ({ data: { data: [{ id: "enc-1", attributes: { status: "IN_PROGRESS", encounterType: "OUTPATIENT", startedAt: "2026-04-08T09:00:00.000Z" } }] } }) }));
vi.mock("@tanstack/react-query", () => ({ useQuery: () => ({ data: { data: [{ id: "pr-1", name: "Colonoscopy", type: "Endoscopy", date: "2026-04-15", surgeon: "Dr. Chikwava", facility: "Harare Central Hospital", status: "Scheduled", notes: "Prep instructions given" }, { id: "pr-2", name: "Appendectomy", type: "General Surgery", date: "2026-03-10", surgeon: "Dr. Mutasa", facility: "Parirenyatwa Hospital", status: "Completed", notes: "No complications" }] }, isLoading: false }) }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get: vi.fn() } }));

describe("ProceduresPage", () => {
  it("surfaces procedure continuity with documents and care planning", () => {
    render(<ProceduresPage />);

    expect(screen.getByText("Keep scheduled and completed procedures connected to the wider care loop instead of leaving them as isolated history entries")).toBeInTheDocument();
    expect(screen.getAllByText("Procedure continuity")).toHaveLength(2);
    expect(
      screen.getByText("Upcoming procedures that still need preparation and communication.")
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Documents" })).toHaveAttribute("href", "/ehr/patient-1/documents");
    expect(screen.getByRole("link", { name: "Care Plans" })).toHaveAttribute("href", "/ehr/patient-1/care-plans");
  });
});
