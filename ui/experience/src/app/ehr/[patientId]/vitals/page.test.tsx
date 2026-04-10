import type { ReactNode } from "react";
import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import VitalsPage from "./page";

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
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));
vi.mock("@/hooks/queries/useEncounters", () => ({
  useEncounters: () => ({
    data: {
      data: [
        {
          id: "enc-1",
          attributes: { status: "IN_PROGRESS", encounterType: "OUTPATIENT", startedAt: "2026-04-08T09:00:00.000Z" },
        },
      ],
    },
  }),
}));
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "u1", displayName: "Dr. Test" } }),
}));
vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({ isClinical: true }),
}));

vi.mock("@/components/VitalsTrendChart", () => ({
  VitalsTrendPanel: () => null,
}));

vi.mock("@/hooks/queries/useVitals", () => ({
  useVitals: () => ({ data: { data: [] }, isLoading: false }),
  useRecordVitals: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));

const { mockUseEws, mockUseFluid, mockUseObs, mockUseXfer } = vi.hoisted(() => ({
  mockUseEws: vi.fn(),
  mockUseFluid: vi.fn(),
  mockUseObs: vi.fn(),
  mockUseXfer: vi.fn(),
}));

vi.mock("@/hooks/queries/useEWS", () => ({
  useEarlyWarningScores: () => mockUseEws(),
  useRecordEWS: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));

vi.mock("@/hooks/queries/useFluidBalance", () => ({
  useFluidBalance: () => mockUseFluid(),
  useRecordFluid: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));

vi.mock("@/hooks/queries/useObservations", () => ({
  useObservations: () => mockUseObs(),
  useRecordObservation: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));

vi.mock("@/hooks/queries/usePatientTransfers", () => ({
  usePatientTransfers: () => mockUseXfer(),
  useRequestPatientTransfer: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
  useAcceptPatientTransfer: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));

function stubEmpty() {
  return { data: { data: [] }, isLoading: false, isError: false, refetch: vi.fn() };
}

function defaultMocks() {
  mockUseEws.mockReturnValue(stubEmpty());
  mockUseFluid.mockReturnValue({
    data: { data: [], summary: { totalIntake: 0, totalOutput: 0, balance: 0 } },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  });
  mockUseObs.mockReturnValue(stubEmpty());
  mockUseXfer.mockReturnValue(stubEmpty());
}

describe("VitalsPage EWS integration", () => {
  beforeEach(defaultMocks);

  it("surfaces early warning scores section with honest empty state", () => {
    render(<VitalsPage />);

    expect(screen.getByRole("heading", { name: "Vitals" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Early warning scores" })).toBeInTheDocument();
    expect(screen.getByText(/No early warning scores recorded for this patient yet/)).toBeInTheDocument();
    expect(screen.getByText(/early_warning_scores/)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Fluid balance" })).toBeInTheDocument();
    expect(screen.getByText(/No intake or output entries for this day/)).toBeInTheDocument();
  });

  it("renders EWS rows from BFF-shaped data", () => {
    mockUseEws.mockReturnValue({
      data: {
        data: [
          {
            id: "ews-1",
            total_score: 8,
            risk_level: "HIGH",
            score_type: "NEWS2",
            recorded_at: "2026-04-10T12:00:00.000Z",
            escalation_required: true,
          },
        ],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    render(<VitalsPage />);

    expect(screen.getByText("HIGH")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();
    expect(screen.getByText("NEWS2")).toBeInTheDocument();
    expect(screen.getByText("Yes")).toBeInTheDocument();
  });
});

describe("VitalsPage fluid balance integration", () => {
  beforeEach(defaultMocks);

  it("renders fluid rows and summary from BFF-shaped data", () => {
    mockUseFluid.mockReturnValue({
      data: {
        data: [
          {
            id: "f1",
            entry_type: "INTAKE",
            category: "ORAL",
            volume_ml: 250,
            description: "Water",
            recorded_at: "2026-04-10T08:00:00.000Z",
          },
        ],
        summary: { totalIntake: 250, totalOutput: 0, balance: 250 },
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    render(<VitalsPage />);

    const table = screen.getByRole("table");
    expect(within(table).getByText("INTAKE")).toBeInTheDocument();
    expect(within(table).getByText("ORAL")).toBeInTheDocument();
    expect(within(table).getByText("Water")).toBeInTheDocument();
    expect(within(table).getByText("250")).toBeInTheDocument();
  });
});

describe("VitalsPage observation entries integration", () => {
  beforeEach(defaultMocks);

  it("surfaces empty observations section", () => {
    render(<VitalsPage />);
    expect(screen.getByRole("heading", { name: "Observation entries" })).toBeInTheDocument();
    expect(screen.getByText(/No observation entries for this patient yet/)).toBeInTheDocument();
  });

  it("renders observation rows from BFF-shaped data", () => {
    mockUseObs.mockReturnValue({
      data: {
        data: [
          {
            id: "obs-1",
            chart_type: "PAIN",
            parameters: '{"summary":"moderate discomfort"}',
            recorded_at: "2026-04-10T14:00:00.000Z",
            recorded_by: "nurse-1",
          },
        ],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    render(<VitalsPage />);
    expect(screen.getByText("PAIN")).toBeInTheDocument();
    expect(screen.getByText(/moderate discomfort/)).toBeInTheDocument();
    expect(screen.getByText("nurse-1")).toBeInTheDocument();
  });
});

describe("VitalsPage patient transfers integration", () => {
  beforeEach(defaultMocks);

  it("surfaces empty transfers section", () => {
    render(<VitalsPage />);
    expect(screen.getByRole("heading", { name: "Transfers" })).toBeInTheDocument();
    expect(screen.getByText(/No transfer records for this patient yet/)).toBeInTheDocument();
  });

  it("renders transfer rows and accept button for REQUESTED status", () => {
    mockUseXfer.mockReturnValue({
      data: {
        data: [
          {
            id: "xfer-1",
            status: "REQUESTED",
            reason: "CLINICAL",
            transfer_type: "INTERNAL",
            clinical_notes: "Needs ICU bed",
            requested_at: "2026-04-10T15:00:00.000Z",
            requested_by: "doc-1",
          },
        ],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    render(<VitalsPage />);
    expect(screen.getByText("REQUESTED")).toBeInTheDocument();
    expect(screen.getByText("CLINICAL")).toBeInTheDocument();
    expect(screen.getByText("INTERNAL")).toBeInTheDocument();
    expect(screen.getByText("Needs ICU bed")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Accept" })).toBeInTheDocument();
  });
});
