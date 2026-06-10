import type { ReactNode } from "react";
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import EmergencyDepartmentPage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({
    children,
    title,
    subtitle,
  }: {
    children: ReactNode;
    title: string;
    subtitle?: string;
  }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

const mockUseEmergencyActivations = vi.fn();
const mockUseActivateEmergency = vi.fn();
const mockUseLogEmergencyAction = vi.fn();
const mockUseEndEmergency = vi.fn();

vi.mock("@/hooks/queries/useEmergency", () => ({
  useEmergencyActivations: () => mockUseEmergencyActivations(),
  useActivateEmergency: () => mockUseActivateEmergency(),
  useLogEmergencyAction: () => mockUseLogEmergencyAction(),
  useEndEmergency: () => mockUseEndEmergency(),
}));

vi.mock("@/components/trust/BreakGlassRequestPanel", () => ({
  BreakGlassRequestPanel: () => <div data-testid="break-glass-request-panel" />,
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    user: { id: "actor-1", displayName: "Test Clinician" },
  }),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } | null }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Test Hospital" } }),
}));

vi.mock("@/hooks/queries/useEdVisit", () => ({
  useEdVisits: () => ({ data: [], refetch: vi.fn(), isLoading: false, isError: false }),
  useOpenEdVisit: () => ({ mutate: vi.fn(), mutateAsync: vi.fn(), isPending: false }),
}));

function stubMutation() {
  return { mutate: vi.fn(), isPending: false, isError: false, isSuccess: false };
}

describe("EmergencyDepartmentPage", () => {
  it("renders bounded ED shell with triage and queue handoffs", () => {
    mockUseEmergencyActivations.mockReturnValue({
      data: { data: [] },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });
    mockUseActivateEmergency.mockReturnValue(stubMutation());
    mockUseLogEmergencyAction.mockReturnValue(stubMutation());
    mockUseEndEmergency.mockReturnValue(stubMutation());

    render(<EmergencyDepartmentPage />);

    expect(screen.getByRole("heading", { level: 1, name: "ED / Casualty" })).toBeInTheDocument();
    expect(screen.getByText(/ED trackboard, full patient journey, critical event activations/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Triage queue" })).toHaveAttribute("href", "/queue/triage");
    expect(screen.getByRole("link", { name: "Patient queue" })).toHaveAttribute("href", "/queue");
    expect(screen.getByRole("link", { name: "Patient search" })).toHaveAttribute("href", "/queue/search");
    expect(screen.getByText(/No activations returned for this tenant/)).toBeInTheDocument();
  });

  it("lists activations from BFF and links chart when patient_id is present", () => {
    mockUseEmergencyActivations.mockReturnValue({
      data: {
        data: [
          {
            id: "ea-1",
            patient_id: "11111111-1111-1111-1111-111111111111",
            protocol_type: "CODE_BLUE",
            status: "ACTIVE",
            activation_time: "2026-04-09T10:00:00.000Z",
            location: "Resus 1",
          },
        ],
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });
    mockUseActivateEmergency.mockReturnValue(stubMutation());
    mockUseLogEmergencyAction.mockReturnValue(stubMutation());
    mockUseEndEmergency.mockReturnValue(stubMutation());

    render(<EmergencyDepartmentPage />);

    expect(within(screen.getByRole("table")).getByText("CODE_BLUE")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Chart" })).toHaveAttribute("href", "/ehr/11111111-1111-1111-1111-111111111111/summary");
    expect(screen.getByRole("button", { name: "Log action" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "End" })).toBeInTheDocument();
  });
});
