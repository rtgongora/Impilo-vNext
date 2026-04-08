import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import FunctionalStatusPage from "./page";

vi.mock("next/navigation", () => ({ useParams: () => ({ patientId: "patient-1" }) }));
vi.mock("@/components/EHRLayout", () => ({ EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div> }));
vi.mock("@/hooks/useFacilityStore", () => ({ useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) => selector({ facility: { id: "facility-1", name: "Harare Central" } }) }));
vi.mock("@/hooks/queries/useEncounters", () => ({ useEncounters: () => ({ data: { data: [{ id: "enc-1", attributes: { status: "IN_PROGRESS", encounterType: "OUTPATIENT", startedAt: "2026-04-08T09:00:00.000Z" } }] } }) }));
vi.mock("@tanstack/react-query", () => ({ useQuery: () => ({ data: { data: [{ id: "fa-1", type: "barthel", date: "2026-04-01", assessor: "Sr. N. Phiri", totalScore: 85, maxScore: 100, interpretation: "Moderate dependence", activities: [{ activity: "Mobility", score: 10, maxScore: 15, level: "Walks with help" }] }] }, isLoading: false }) }));

describe("FunctionalStatusPage", () => {
  it("surfaces functional continuity into plans and social context", () => {
    render(<FunctionalStatusPage />);

    expect(screen.getByText("Keep daily-function risk visible so plans, team actions, and follow-up needs stay grounded in what the patient can actually do")).toBeInTheDocument();
    expect(screen.getByText("Functional continuity")).toBeInTheDocument();
    expect(screen.getByText("Assist needs")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Care Plans" })).toHaveAttribute("href", "/ehr/patient-1/care-plans");
    expect(screen.getByRole("link", { name: "Social History" })).toHaveAttribute("href", "/ehr/patient-1/social-history");
  });
});
