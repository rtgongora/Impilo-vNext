import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import GoalsPage from "./page";

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

vi.mock("@/hooks/queries/useCareContinuity", () => ({
  __esModule: true,
  usePatientGoalsFromCarePlans: () => ({
    data: {
      data: [
        {
          id: "goal-1",
          title: "Reduce HbA1c to below 7%",
          description: "Clinical goal",
          status: "At Risk" as const,
          priority: "High" as const,
          progress: 40,
          targetDate: "2026-06-30",
          createdDate: "2026-01-15",
          linkedCarePlan: "Diabetes Management Plan",
          category: "Clinical",
          notes: "Needs reinforcement",
        },
      ],
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useCreateGoal: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}));

describe("GoalsPage", () => {
  it("surfaces goal continuity with care plans and assessments", () => {
    render(<GoalsPage />);

    expect(screen.getByText("Track where the care plan is winning, slipping, or needs a new next step before the encounter closes")).toBeInTheDocument();
    expect(screen.getByText("Goal continuity")).toBeInTheDocument();
    expect(screen.getByText("Needs attention")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Care Plans" })).toHaveAttribute("href", "/ehr/patient-1/care-plans");
    expect(screen.getByRole("link", { name: "Assessments" })).toHaveAttribute("href", "/ehr/patient-1/assessments");
  });
});
