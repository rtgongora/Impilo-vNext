// @vitest-environment jsdom
import type { ReactNode } from "react";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import FacilityConfigurationPage from "./page";

afterEach(() => cleanup());

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "42" }),
}));

vi.mock("next/link", () => ({
  default: ({ children }: { children: ReactNode }) => <span>{children}</span>,
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/components/FeatureMaturityBadge", () => ({
  FeatureMaturityBadge: () => <span>badge</span>,
}));

const idleMutation = { mutate: vi.fn(), mutateAsync: vi.fn().mockResolvedValue({ data: {} }), isPending: false };

vi.mock("@/hooks/queries/useFacilityConfiguration", () => ({
  useFacilityConfiguration: () => ({
    data: {
      data: {
        facilityId: 42,
        facilityUuid: "uuid-1",
        name: "Test Clinic",
        facilityCode: "CODE-1",
        facilityType: "CLINIC",
        province: "Harare",
        district: "Central",
        regulatoryStatus: "IMPORTED_PENDING_CONFIGURATION",
        operationalStatus: "OPERATIONAL",
        status: "ACTIVE",
        configurationState: "IMPORTED_PENDING_CONFIGURATION",
        activeServicePointCount: 0,
        activeWorkspaceCount: 0,
        departmentCount: 0,
        activeQueueDefinitionCount: 0,
        setupComplete: false,
        missingSections: ["service_points", "workspaces"],
        sourceProvenance: "IMPORTED_FROM_MASTER_LIST",
        configVersion: 1,
        lastConfigChangeAt: null,
      },
    },
    isLoading: false,
    isError: false,
  }),
  useFacilitySetupChecklist: () => ({ data: { data: { items: [] } }, isLoading: false }),
  useConfigServicePoints: () => ({ data: { data: [] }, isLoading: false }),
  useConfigWorkspaces: () => ({ data: { data: [] }, isLoading: false }),
  useFacilityQueueDefinitions: () => ({
    data: {
      data: [
        {
          sourceRef: "sp-1",
          displayName: "Triage",
          queueType: "TRIAGE",
          servicePointType: "TRIAGE",
          workspaceId: null,
          priorityFlag: true,
          walkInSupported: true,
          appointmentSupported: true,
          active: true,
        },
      ],
    },
    isLoading: false,
  }),
  useFacilityDownstreamReadiness: () => ({ data: { data: { note: "downstream note" } }, isLoading: false }),
  useFacilityConfigHistory: () => ({ data: { data: [] }, isLoading: false }),
  useCreateServicePoint: () => idleMutation,
  useUpdateServicePoint: () => idleMutation,
  useRetireServicePoint: () => idleMutation,
  useCreateWorkspace: () => idleMutation,
  useUpdateWorkspace: () => idleMutation,
  useRetireWorkspace: () => idleMutation,
  usePublishConfiguration: () => idleMutation,
}));

vi.mock("@/hooks/queries/useFacilityQueues", () => ({
  useQueueMaterializationStatus: () => ({
    data: { data: { materialisedFromTuso: 0, seedDemoOnly: 1, lastMaterializedAt: null } },
    isLoading: false,
  }),
  useFacilityQueuesAdmin: () => ({
    data: { data: [{ sourceRef: "sp-1", source: "SEED_DEMO", materializationStatus: "SEED_DEMO" }] },
    isLoading: false,
  }),
  useReconcileQueues: () => idleMutation,
}));

describe("FacilityConfigurationPage", () => {
  it("renders the overview with real data, ownership wording and honest missing sections", () => {
    render(<FacilityConfigurationPage />);
    expect(screen.getByText("Facility configuration")).toBeTruthy();
    // State appears as a badge and as a table row value — assert it is present at least once.
    expect(screen.getAllByText("IMPORTED_PENDING_CONFIGURATION").length).toBeGreaterThan(0);
    // Ownership wording — TUSO source of truth, queues not authored here.
    expect(screen.getByText(/TUSO owns facility configuration/i)).toBeTruthy();
    // Honest missing-section reporting.
    expect(screen.getByText("Missing required sections")).toBeTruthy();
    expect(screen.getByText("service points")).toBeTruthy();
    expect(screen.getByText("IMPORTED_FROM_MASTER_LIST")).toBeTruthy();
  });

  it("shows the seed/demo non-production warning on the queue definitions tab", () => {
    render(<FacilityConfigurationPage />);
    fireEvent.click(screen.getByRole("button", { name: "Queue definitions" }));
    expect(screen.getByText(/Seed\/demo queue/i)).toBeTruthy();
    expect(screen.getByText(/read-only here/i)).toBeTruthy();
  });
});
