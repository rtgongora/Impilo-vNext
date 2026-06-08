import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import EncounterPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1", encounterId: "enc-1" }),
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("@/components/EHRLayout", () => ({ EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div> }));
vi.mock("@/components/ClinicalAlerts", () => ({ ClinicalAlerts: () => <div>Clinical alerts</div> }));
vi.mock("@/components/clinical/PatientJourneyContextPanel", () => ({
  PatientJourneyContextPanel: () => <div data-testid="patient-journey-context" />,
}));
vi.mock("@/components/clinical/ClinicalFinanceContextStrip", () => ({
  ClinicalFinanceContextStrip: () => <div data-testid="clinical-finance-context" />,
}));
vi.mock("@/components/clinical/EncounterVitalsGuidance", () => ({
  EncounterVitalsGuidance: () => <div data-testid="encounter-vitals-guidance" />,
}));
vi.mock("@/components/encounter/EncounterOrchestrationRail", () => ({
  EncounterOrchestrationRail: () => <div data-testid="encounter-orchestration-rail" />,
}));
vi.mock("@/components/encounter/EncounterLabOrdersPanel", () => ({
  EncounterLabOrdersPanel: () => <div data-testid="encounter-lab-orders-panel">Lab orders</div>,
}));
vi.mock("@/components/encounter/EncounterImagingOrdersPanel", () => ({
  EncounterImagingOrdersPanel: () => <div data-testid="encounter-imaging-orders-panel">Imaging orders</div>,
}));
vi.mock("@/components/encounter/EncounterDischargePanel", () => ({
  EncounterDischargePanel: () => <div data-testid="encounter-discharge-panel">Discharge</div>,
}));
vi.mock("@/hooks/useClinicalAlerts", () => ({ useClinicalAlerts: () => [] }));
vi.mock("@/hooks/useFacilityStore", () => ({ useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) => selector({ facility: { id: "facility-1", name: "Harare Central Hospital" } }) }));
vi.mock("@/hooks/useAuthStore", () => ({ useAuthStore: () => ({ user: { id: "user-1", displayName: "Dr. Moyo" } }) }));
vi.mock("@/hooks/useRoleGroup", () => ({ useRoleGroup: () => ({ isClinical: true, isPrescriber: true }) }));
vi.mock("@/hooks/queries/usePatients", () => ({ usePatient: () => ({ data: { data: { id: "patient-1", attributes: { fullName: "Tariro Moyo" } } } }) }));
vi.mock("@/hooks/queries/useReferrals", () => ({
  useReferrals: () => ({
    data: {
      data: [
        {
          id: "ref-1",
          attributes: {
            encounterId: "enc-1",
            status: "RESPONDED",
            specialty: "Cardiology",
            referredTo: "Cardiology Clinic",
            response_notes: "Echo reviewed and follow-up advised.",
          },
        },
      ],
    },
  }),
}));
vi.mock("@/hooks/queries/useEncounters", () => ({
  useEncounter: () => ({
    data: {
      data: {
        id: "enc-1",
        attributes: {
          status: "IN_PROGRESS",
          encounterType: "OUTPATIENT",
          startedAt: "2026-04-08T09:00:00.000Z",
        },
      },
    },
    isLoading: false,
  }),
  useCloseEncounter: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
  useUpdateEncounterPathwayProtocol: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));
vi.mock("@tanstack/react-query", () => ({
  useQuery: ({ queryKey }: { queryKey: unknown[] }) => {
    if (queryKey[0] === "triage") {
      return { data: { data: [{ id: "triage-1", attributes: { acuity: 2, vitals: {} } }] } };
    }
    return { data: { data: [] } };
  },
}));
vi.mock("@/lib/api-client", () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }));

describe("EncounterPage", () => {
  it("surfaces encounter closure orchestration above the encounter workspace", () => {
    render(<EncounterPage />);

    expect(screen.getByText("Encounter closure")).toBeInTheDocument();
    expect(screen.getByText("Encounter loop status")).toBeInTheDocument();
    expect(screen.getByText("Specialist responses are back and should be reviewed before closure.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Visit Outcome" })).toHaveAttribute("href", "/ehr/patient-1/discharge?encounterId=enc-1");
    expect(screen.getByTestId("encounter-lab-orders-panel")).toBeInTheDocument();
  });
});
