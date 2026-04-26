import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import AssessmentsPage from "./page";

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

describe("AssessmentsPage", () => {
  it("surfaces assessment continuity into goals and care plans", () => {
    render(<AssessmentsPage />);

    expect(screen.getByText("Clinical screening tools not loaded")).toBeInTheDocument();
    expect(screen.getByText("Use structured scores as live evidence for goals, plans, and follow-up decisions")).toBeInTheDocument();
    expect(screen.getByText("Assessment continuity")).toBeInTheDocument();
    expect(screen.getByText("Need follow-up")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Goals" })).toHaveAttribute("href", "/ehr/patient-1/goals");
    expect(screen.getByRole("link", { name: "Care Plans" })).toHaveAttribute("href", "/ehr/patient-1/care-plans");
  });
});
