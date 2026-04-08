import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import SocialHistoryPage from "./page";

vi.mock("next/navigation", () => ({ useParams: () => ({ patientId: "patient-1" }) }));
vi.mock("@/components/EHRLayout", () => ({ EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div> }));
vi.mock("@/hooks/useFacilityStore", () => ({ useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) => selector({ facility: { id: "facility-1", name: "Harare Central" } }) }));
vi.mock("@/hooks/queries/useEncounters", () => ({ useEncounters: () => ({ data: { data: [{ id: "enc-1", attributes: { status: "IN_PROGRESS", encounterType: "OUTPATIENT", startedAt: "2026-04-08T09:00:00.000Z" } }] } }) }));
vi.mock("@tanstack/react-query", () => ({ useQuery: () => ({ data: { data: [{ id: "s-1", category: "Smoking", icon: "home", status: "Former Smoker", detail: "Quit 3 years ago", lastUpdated: "2026-03-15", riskLevel: "Moderate" }] }, isLoading: false }) }));

describe("SocialHistoryPage", () => {
  it("surfaces social continuity into goals and care plans", () => {
    render(<SocialHistoryPage />);

    expect(screen.getByText("Keep practical barriers and supports visible so plans and goals still match real life outside the visit")).toBeInTheDocument();
    expect(screen.getByText("Social continuity")).toBeInTheDocument();
    expect(screen.getByText("Moderate risk")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Care Plans" })).toHaveAttribute("href", "/ehr/patient-1/care-plans");
    expect(screen.getByRole("link", { name: "Goals" })).toHaveAttribute("href", "/ehr/patient-1/goals");
  });
});
