import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import CarePlansPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
}));

vi.mock("@/components/EHRLayout", () => ({
  EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
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

vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({
    data: {
      data: [
        {
          id: "cp-1",
          title: "Diabetes Management Plan",
          status: "Active",
          category: "Chronic Disease",
          startDate: "2026-01-15",
          targetDate: "2026-07-15",
          author: "Dr. Moyo",
          goals: [{ label: "HbA1c below 7%", progress: 65 }],
          interventions: [{ label: "Dietitian referral", completed: false }],
        },
      ],
    },
    isLoading: false,
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe("CarePlansPage", () => {
  it("surfaces care plan orchestration with goals and ownership continuity", () => {
    render(<CarePlansPage />);

    expect(screen.getByText("Keep goals, interventions, and ownership visible while the current encounter is still in motion")).toBeInTheDocument();
    expect(screen.getByText("Care plan loop status")).toBeInTheDocument();
    expect(screen.getByText("Pending interventions")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Goals" })).toHaveAttribute("href", "/ehr/patient-1/goals");
    expect(screen.getByRole("link", { name: "Care Team" })).toHaveAttribute("href", "/ehr/patient-1/care-team");
  });
});
