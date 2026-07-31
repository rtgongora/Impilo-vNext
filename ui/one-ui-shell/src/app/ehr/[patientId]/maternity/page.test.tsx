import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import MaternityMonitoringPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/components/EHRLayout", () => ({
  EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "u1", displayName: "Dr. Test" } }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({ isClinical: true }),
}));

vi.mock("@/hooks/queries/useEncounters", () => ({
  useEncounters: () => ({
    data: {
      // isOpen is what the page asks for, and PCT's status vocabulary is STARTED/ON_HOLD/COMPLETED.
      // This mock said status: "IN_PROGRESS", a value PCT never sets — see the field comment in
      // useEncounters. 29e76f7b0 moved the screens onto isOpen and left the mock behind, so the page
      // correctly found no open encounter and rendered "No active encounter in scope".
      data: [{ id: "enc-1", attributes: { status: "STARTED", isOpen: true } }],
    },
  }),
}));

vi.mock("@/features/maternity/partograph/VitalsPartographSection", () => ({
  VitalsPartographSection: () => <div data-testid="partograph-section">Partograph module</div>,
}));

vi.mock("@/features/maternity/ctg/VitalsCtgSection", () => ({
  VitalsCtgSection: () => <div data-testid="ctg-section">CTG module</div>,
}));

vi.mock("@/features/maternity/nearmiss/NearMissAssessmentPanel", () => ({
  NearMissAssessmentPanel: () => <div data-testid="near-miss-panel">Near-miss module</div>,
}));

vi.mock("@/features/maternity/nearmiss/NearMissIndicatorsPanel", () => ({
  NearMissIndicatorsPanel: () => <div data-testid="near-miss-indicators-panel">Near-miss indicators module</div>,
}));

vi.mock("@/features/maternity/MaternitySummaryPanel", () => ({
  MaternitySummaryPanel: () => <div data-testid="maternity-summary-panel">Maternity summary module</div>,
}));

vi.mock("@/features/maternity/BirthDestinationPanel", () => ({
  BirthDestinationPanel: () => <div data-testid="birth-destination-panel">Birth destination module</div>,
}));

vi.mock("@/features/maternity/emergency-bundles/EmergencyBundlePanel", () => ({
  EmergencyBundlePanel: ({ testIdPrefix }: { testIdPrefix: string }) => (
    <div data-testid={`${testIdPrefix}-panel`}>Emergency bundle module</div>
  ),
}));

vi.mock("@/features/maternity/reproductive/PregnancyEpisodesPanel", () => ({
  PregnancyEpisodesPanel: () => <div data-testid="pregnancy-episodes-panel">Pregnancy episodes module</div>,
}));

vi.mock("@/features/maternity/reproductive/ReproductiveConfidentialPanel", () => ({
  ReproductiveConfidentialPanel: () => (
    <div data-testid="reproductive-confidential-panel">Confidential SRH module</div>
  ),
}));

vi.mock("@/features/maternity/reproductive/W14ReproductivePanels", () => ({
  DeliveryRecordPanel: () => <div data-testid="w14-delivery-record-panel">Delivery record module</div>,
  FertilityPanel: () => <div data-testid="w14-fertility-panel">Fertility module</div>,
  PreconceptionPanel: () => <div data-testid="w14-preconception-panel">Preconception module</div>,
  ReproductiveIntentionPanel: () => (
    <div data-testid="w14-reproductive-intention-panel">Reproductive intention module</div>
  ),
}));

vi.mock("@/features/maternity/bishop/BishopScorePanel", () => ({
  BishopScorePanel: () => <div data-testid="bishop-score-panel">Bishop score module</div>,
}));

vi.mock("@/features/maternity/journeys/MaternityClinicalJourneysSection", () => ({
  MaternityClinicalJourneysSection: () => (
    <div data-testid="maternity-clinical-journeys">Clinical journeys module</div>
  ),
}));

vi.mock("@/features/maternity/bishop/BishopScorePanel", () => ({
  BishopScorePanel: () => <div data-testid="bishop-score-panel">Bishop score module</div>,
}));

// W14-B panels call useQuery/useMutation directly; unmocked they demand a QueryClient the
// page test deliberately does not provide (every panel here is mocked to keep this a route test).
vi.mock("@/features/maternity/reproductive/W14ReproductivePanels", () => ({
  ReproductiveIntentionPanel: () => <div data-testid="intention-panel">Intention module</div>,
  PreconceptionPanel: () => <div data-testid="preconception-panel">Preconception module</div>,
  FertilityPanel: () => <div data-testid="fertility-panel">Fertility module</div>,
  DeliveryRecordPanel: () => <div data-testid="delivery-panel">Delivery module</div>,
}));

describe("MaternityMonitoringPage", () => {
  it("mounts the canonical maternity route with partograph, CTG and near-miss sections", () => {
    render(<MaternityMonitoringPage />);

    expect(screen.getByRole("heading", { level: 1, name: /Maternity Monitoring/i })).toBeInTheDocument();
    expect(screen.getByText(/Active encounter: enc-1/i)).toBeInTheDocument();
    expect(screen.getByTestId("partograph-section")).toBeInTheDocument();
    expect(screen.getByTestId("ctg-section")).toBeInTheDocument();
    expect(screen.getByTestId("near-miss-panel")).toBeInTheDocument();
    expect(screen.getByTestId("near-miss-indicators-panel")).toBeInTheDocument();
    expect(screen.getByTestId("maternity-summary-panel")).toBeInTheDocument();
    expect(screen.getByTestId("pregnancy-episodes-panel")).toBeInTheDocument();
    expect(screen.getByTestId("reproductive-confidential-panel")).toBeInTheDocument();
    expect(screen.getByTestId("birth-destination-panel")).toBeInTheDocument();
    expect(screen.getByTestId("pph-protocol-panel")).toBeInTheDocument();
    expect(screen.getByTestId("eclampsia-protocol-panel")).toBeInTheDocument();
    expect(screen.getByTestId("bishop-score-panel")).toBeInTheDocument();
    expect(screen.getByTestId("intention-panel")).toBeInTheDocument();
    expect(screen.getByTestId("preconception-panel")).toBeInTheDocument();
    expect(screen.getByTestId("fertility-panel")).toBeInTheDocument();
    expect(screen.getByTestId("delivery-panel")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Back to vitals/i })).toHaveAttribute("href", "/ehr/patient-1/vitals");
  });
});
