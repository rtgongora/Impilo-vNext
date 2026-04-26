import type { ReactNode } from "react";
import { render, screen, within } from "@testing-library/react";
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

const { mockUsePatient, mockUseVitals, mockUseGrowth, mockUseRecordGrowth } = vi.hoisted(() => ({
  mockUsePatient: vi.fn(),
  mockUseVitals: vi.fn(),
  mockUseGrowth: vi.fn(),
  mockUseRecordGrowth: vi.fn(),
}));

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatient: () => mockUsePatient(),
}));

vi.mock("@/hooks/queries/useGrowth", () => ({
  useGrowth: () => mockUseGrowth(),
  useRecordGrowth: () => mockUseRecordGrowth(),
}));

vi.mock("@/hooks/queries/useVitals", () => ({
  useVitals: () => mockUseVitals(),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "user-1", displayName: "Nurse User" } }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({ isClinical: true }),
}));

describe("GrowthChartPage", () => {
  it("falls back to vitals until a structured growth row exists", () => {
    mockUsePatient.mockReturnValue({
      data: {
        data: {
          id: "patient-1",
          attributes: { displayName: "Test Child", dateOfBirth: "2023-01-15", cpid: "cpid-1" },
        },
      },
    });
    mockUseGrowth.mockReturnValue({ data: [], isLoading: false });
    mockUseRecordGrowth.mockReturnValue({ mutate: vi.fn(), isPending: false, isError: false });
    mockUseVitals.mockReturnValue({ data: { data: [] }, isLoading: false });

    render(<GrowthChartPage />);

    expect(screen.getByText(/Legacy fallback: vitals-backed anthropometrics/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Record Measurement/i })).toBeInTheDocument();
    expect(screen.getByText(/No growth measurements or vitals-backed anthropometrics yet/i)).toBeInTheDocument();
  });

  it("renders WHO-backed growth rows from the structured growth API", () => {
    mockUsePatient.mockReturnValue({
      data: {
        data: {
          id: "patient-1",
          attributes: { displayName: "Test Child", dateOfBirth: "2023-06-01", cpid: "cpid-1" },
        },
      },
    });
    mockUseGrowth.mockReturnValue({
      data: [
        {
          id: "g-1",
          measuredAt: "2026-04-10T12:00:00.000Z",
          recordedBy: "nurse",
          weightKg: 12.5,
          lengthCm: 88,
          heightCm: null,
          headCircumferenceCm: 48,
          muacCm: null,
          bmi: 16.1,
          measurementMode: "LENGTH",
          notes: null,
          derived: {
            ageDays: 1044,
            standard: "WHO_2006_CHILD_GROWTH_STANDARDS",
            normalizedStatureCm: 88,
            normalizedStatureMode: "HEIGHT",
            statureAdjustmentCm: -0.7,
            bodyMassIndex: 16.1,
            weightForAge: { zScore: 0.42, percentile: 66.3 },
            lengthHeightForAge: { zScore: -0.11, percentile: 45.6 },
            bodyMassIndexForAge: { zScore: 0.08, percentile: 53.2 },
            headCircumferenceForAge: { zScore: 0.02, percentile: 50.8 },
          },
        },
      ],
      isLoading: false,
    });
    mockUseRecordGrowth.mockReturnValue({ mutate: vi.fn(), isPending: false, isError: false });
    mockUseVitals.mockReturnValue({
      data: {
        data: [
          {
            id: "v-1",
            type: "vitals",
            attributes: {
              patientId: "patient-1",
              encounterId: "enc-1",
              recordedBy: "nurse",
              systolic: null,
              diastolic: null,
              heartRate: null,
              temperature: null,
              respiratoryRate: null,
              oxygenSaturation: null,
              weight: 12.5,
              height: 88,
              bmi: 16.1,
              painScore: null,
              notes: null,
              recordedAt: "2026-04-10T12:00:00.000Z",
            },
          },
        ],
      },
      isLoading: false,
    });

    render(<GrowthChartPage />);

    const table = screen.getByRole("table");
    expect(within(table).getByText("12.5")).toBeInTheDocument();
    expect(within(table).getByText("88")).toBeInTheDocument();
    expect(within(table).getByText("16.1")).toBeInTheDocument();
    expect(within(table).getByText("48")).toBeInTheDocument();
    expect(within(table).getByText("0.42")).toBeInTheDocument();
    expect(screen.getByText(/WHO-backed structured growth measurements/i)).toBeInTheDocument();
  });
});
