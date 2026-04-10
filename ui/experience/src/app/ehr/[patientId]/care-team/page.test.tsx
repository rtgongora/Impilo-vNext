import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import CareTeamPage from "./page";

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
    selector({ facility: { id: "facility-1", name: "Harare Central Hospital" } }),
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

describe("CareTeamPage", () => {
  it("surfaces team ownership continuity across care planning surfaces", () => {
    render(<CareTeamPage />);

    expect(screen.getByText("Care team roster not connected")).toBeInTheDocument();
    expect(screen.getByText("Keep the accountable team visible so plans, consults, and next actions still have an owner")).toBeInTheDocument();
    expect(screen.getByText("Team continuity")).toBeInTheDocument();
    expect(screen.getByText("Cross-facility")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Care Plans" })).toHaveAttribute("href", "/ehr/patient-1/care-plans");
    expect(screen.getByRole("link", { name: "Notes" })).toHaveAttribute("href", "/ehr/patient-1/notes");
  });
});
