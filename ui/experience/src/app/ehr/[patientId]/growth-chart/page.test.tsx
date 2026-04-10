import type { ReactNode } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import GrowthChartPage from "./page";

vi.mock("next/navigation", () => ({ useParams: () => ({ patientId: "patient-1" }) }));
vi.mock("@/components/EHRLayout", () => ({ EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
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
    data: { data: [{ id: "enc-1", attributes: { status: "IN_PROGRESS", encounterType: "OUTPATIENT", startedAt: "2026-04-08T09:00:00.000Z" } }] },
  }),
}));

describe("GrowthChartPage", () => {
  it("keeps growth review encounter-aware and records session-only measurements without a fake API", () => {
    render(<GrowthChartPage />);

    expect(screen.getByText("Growth data not persisted")).toBeInTheDocument();
    expect(screen.getByText("Growth continuity")).toBeInTheDocument();
    expect(screen.getByText("Growth loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Vitals" })).toHaveAttribute("href", "/ehr/patient-1/vitals");

    fireEvent.click(screen.getByRole("button", { name: "Record Measurement" }));
    fireEvent.change(screen.getByLabelText("Age (months)"), { target: { value: "27" } });
    fireEvent.change(screen.getByLabelText("Weight (kg)"), { target: { value: "13.2" } });
    fireEvent.change(screen.getByLabelText("Height (cm)"), { target: { value: "90" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Measurement" }));

    expect(screen.getByText("13.2")).toBeInTheDocument();
  });
});
