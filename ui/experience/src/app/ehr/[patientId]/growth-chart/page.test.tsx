import type { ReactNode } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import GrowthChartPage from "./page";

const postMock = vi.fn().mockResolvedValue({ data: {} });

vi.mock("next/navigation", () => ({ useParams: () => ({ patientId: "patient-1" }) }));
vi.mock("@/components/EHRLayout", () => ({ EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div> }));
vi.mock("@/hooks/useFacilityStore", () => ({ useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) => selector({ facility: { id: "facility-1", name: "Harare Central Hospital" } }) }));
vi.mock("@/hooks/queries/useEncounters", () => ({ useEncounters: () => ({ data: { data: [{ id: "enc-1", attributes: { status: "IN_PROGRESS", encounterType: "OUTPATIENT", startedAt: "2026-04-08T09:00:00.000Z" } }] } }) }));
vi.mock("@tanstack/react-query", () => ({ useQuery: () => ({ data: { data: [{ id: "gm-1", date: "2026-04-01", ageMonths: 24, weight: 12.5, height: 87, headCircumference: 48.2, bmi: 16.5, weightZScore: 0.3, heightZScore: 0.1, bmiZScore: 0.5, hcZScore: 0.2, weightPercentile: 62, heightPercentile: 54, bmiPercentile: 69, hcPercentile: 58 }] }, isLoading: false }) }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get: vi.fn(), post: (...args: unknown[]) => postMock(...args) } }));

describe("GrowthChartPage", () => {
  it("keeps growth review encounter-aware and records measurements in place", async () => {
    render(<GrowthChartPage />);

    expect(screen.getByText("Growth continuity")).toBeInTheDocument();
    expect(screen.getByText("Growth loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Vitals" })).toHaveAttribute("href", "/ehr/patient-1/vitals");

    fireEvent.click(screen.getByRole("button", { name: "Record Measurement" }));
    fireEvent.change(screen.getByLabelText("Age (months)"), { target: { value: "27" } });
    fireEvent.change(screen.getByLabelText("Weight (kg)"), { target: { value: "13.2" } });
    fireEvent.change(screen.getByLabelText("Height (cm)"), { target: { value: "90" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Measurement" }));

    await waitFor(() => {
      expect(postMock).toHaveBeenCalledWith("/patients/patient-1/growth-chart", expect.objectContaining({
        encounter_id: "enc-1",
        facility_id: "facility-1",
        age_months: 27,
        weight: 13.2,
        height: 90,
      }));
    });

    expect(screen.getByText("13.2")).toBeInTheDocument();
  });
});
