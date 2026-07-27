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

const {
  mockUsePatient,
  mockUseVitals,
  mockUseGrowth,
  mockUseRecordGrowth,
  mockUseGrowthReferenceCurves,
} = vi.hoisted(() => ({
  mockUsePatient: vi.fn(),
  mockUseVitals: vi.fn(),
  mockUseGrowth: vi.fn(),
  mockUseGrowthReferenceCurves: vi.fn(),
  mockUseRecordGrowth: vi.fn(),
}));

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatient: () => mockUsePatient(),
}));

vi.mock("@/hooks/queries/useGrowth", () => ({
  useGrowth: () => mockUseGrowth(),
  useRecordGrowth: () => mockUseRecordGrowth(),
}));

vi.mock("@/hooks/queries/useGrowthReferenceCurves", () => ({
  useGrowthReferenceCurves: () => mockUseGrowthReferenceCurves(),
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
    mockUseGrowthReferenceCurves.mockReturnValue({ data: undefined, isError: false });
    mockUseGrowthReferenceCurves.mockReturnValue({
      data: {
        indicator: "weight_for_age",
        unit: "kg",
        standard: "WHO_2006_CHILD_GROWTH_STANDARDS",
        standardLabel: "WHO Child Growth Standards (2006)",
        axis: "age_days",
        attribution: null,
        currentAxisPosition: 1044,
        curves: [
          { z: -3, points: [{ x: 900, value: 9.5 }, { x: 1100, value: 10.4 }] },
          { z: -2, points: [{ x: 900, value: 10.4 }, { x: 1100, value: 11.4 }] },
          { z: 0, points: [{ x: 900, value: 12.4 }, { x: 1100, value: 13.6 }] },
          { z: 2, points: [{ x: 900, value: 15.0 }, { x: 1100, value: 16.5 }] },
          { z: 3, points: [{ x: 900, value: 16.6 }, { x: 1100, value: 18.3 }] },
        ],
        unavailableReason: null,
      },
      isError: false,
    });
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
            correctedAgeDays: 1044,
            correctedAgeApplied: false,
            gestationalAgeWeeks: null,
            gestationalAgeSource: "NOT_RECORDED",
            postmenstrualAgeWeeks: null,
            standard: "WHO_2006_CHILD_GROWTH_STANDARDS",
            standardLabel: "WHO Child Growth Standards (2006)",
            standardAttribution: {
              label: "WHO Child Growth Standards (2006)",
              requiredChartLabel: null,
              citation: "WHO Multicentre Growth Reference Study Group. WHO Child Growth Standards. Geneva: World Health Organization, 2006.",
              licence: null,
              approvalStatus: null,
              contentVersion: null,
            },
            engineVersion: "2.0.0",
            scoringNote: null,
            scoringGaps: null,
            normalizedStatureCm: 88,
            normalizedStatureMode: "HEIGHT",
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
    expect(screen.getByTestId("growth-standards-chart")).toBeInTheDocument();
  });

  it("plots a preterm infant on the preterm chart, in postmenstrual weeks, with its attribution", () => {
    mockUsePatient.mockReturnValue({
      data: {
        data: {
          id: "patient-1",
          attributes: { displayName: "Preterm Baby", dateOfBirth: "2026-01-01", cpid: "cpid-2" },
        },
      },
    });
    mockUseGrowth.mockReturnValue({
      data: [
        {
          id: "g-2",
          measuredAt: "2026-01-15T09:00:00.000Z",
          recordedBy: "nurse",
          weightKg: 1.05,
          lengthCm: null,
          heightCm: null,
          headCircumferenceCm: null,
          muacCm: null,
          bmi: null,
          measurementMode: null,
          notes: null,
          derived: {
            ageDays: 14,
            correctedAgeDays: 0,
            correctedAgeApplied: true,
            gestationalAgeWeeks: 28,
            gestationalAgeSource: "NEWBORN_BIRTH_RECORD",
            postmenstrualAgeWeeks: 30,
            standard: "FENTON_2013",
            standardLabel: "Fenton 2013 Preterm Growth Chart",
            standardAttribution: {
              label: "Fenton 2013 Preterm Growth Chart",
              requiredChartLabel: "Fenton 2013 Preterm Growth Chart",
              citation: "Fenton TR, Kim JH. BMC Pediatr. 2013;13:59.",
              licence: "CC BY-NC-ND 4.0",
              approvalStatus: "ENGINEERING_SEED",
              contentVersion: "fenton-2013-bulk-calculator-v6",
            },
            engineVersion: "2.0.0",
            scoringNote: null,
            scoringGaps: null,
            normalizedStatureCm: null,
            normalizedStatureMode: null,
            bodyMassIndex: null,
            weightForAge: { zScore: -0.41, percentile: 34.1 },
            lengthHeightForAge: null,
            bodyMassIndexForAge: null,
            headCircumferenceForAge: null,
          },
        },
      ],
      isLoading: false,
    });
    mockUseGrowthReferenceCurves.mockReturnValue({
      data: {
        indicator: "weight_for_age",
        unit: "kg",
        standard: "FENTON_2013",
        standardLabel: "Fenton 2013 Preterm Growth Chart",
        axis: "postmenstrual_weeks",
        attribution: {
          label: "Fenton 2013 Preterm Growth Chart",
          requiredChartLabel: "Fenton 2013 Preterm Growth Chart",
          citation: "Fenton TR, Kim JH. BMC Pediatr. 2013;13:59.",
          licence: "CC BY-NC-ND 4.0",
          approvalStatus: "ENGINEERING_SEED",
          contentVersion: "fenton-2013-bulk-calculator-v6",
        },
        currentAxisPosition: 30,
        curves: [
          { z: -3, points: [{ x: 28, value: 0.78 }, { x: 32, value: 1.15 }] },
          { z: -2, points: [{ x: 28, value: 0.9 }, { x: 32, value: 1.32 }] },
          { z: 0, points: [{ x: 28, value: 1.15 }, { x: 32, value: 1.7 }] },
          { z: 2, points: [{ x: 28, value: 1.45 }, { x: 32, value: 2.15 }] },
          { z: 3, points: [{ x: 28, value: 1.62 }, { x: 32, value: 2.4 }] },
        ],
        unavailableReason: null,
      },
      isError: false,
    });
    mockUseVitals.mockReturnValue({ data: { data: [] }, isLoading: false });

    render(<GrowthChartPage />);

    expect(screen.getByTestId("growth-standards-chart")).toBeInTheDocument();
    // The licence obliges the label to appear on the chart itself, not merely nearby.
    expect(screen.getByTestId("growth-standard-label")).toHaveTextContent(
      "Fenton 2013 Preterm Growth Chart",
    );
    expect(screen.getByTestId("growth-standard-citation")).toHaveTextContent("BMC Pediatr. 2013;13:59");
    expect(screen.getByTestId("growth-standard-unratified")).toBeInTheDocument();
    // Plotted at 30 weeks postmenstrual age, not at 14 days old.
    expect(screen.getByTestId("growth-latest-summary")).toHaveTextContent("30w PMA");
  });

  it("says why nothing is plotted rather than drawing an empty chart", () => {
    mockUseGrowthReferenceCurves.mockReturnValue({
      data: {
        indicator: "weight_for_age",
        unit: "kg",
        standard: null,
        standardLabel: null,
        axis: "age_days",
        attribution: null,
        currentAxisPosition: null,
        curves: [],
        unavailableReason:
          "This infant was born at 28 weeks and is now 30 weeks postmenstrual age. The preterm"
          + " growth reference is not loaded. The measurement is recorded but not scored: WHO term"
          + " standards are not valid for a preterm infant and are deliberately not substituted.",
      },
      isError: false,
    });

    render(<GrowthChartPage />);

    expect(screen.queryByTestId("growth-standards-chart")).not.toBeInTheDocument();
    expect(screen.getByTestId("growth-curves-unavailable")).toHaveTextContent("not substituted");
  });
});
