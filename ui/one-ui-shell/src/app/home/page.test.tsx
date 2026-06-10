import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import HomePage from "./page";

const { push, setMode, setOperationalModeMock } = vi.hoisted(() => ({
  push: vi.fn(),
  setMode: vi.fn(),
  setOperationalModeMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  usePathname: () => "/home",
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

vi.mock("@/components/home/WorkplaceSelectionHub", () => ({
  WorkplaceSelectionHub: () => <div>Workplace selection</div>,
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (state: {
    user: { id: string; displayName: string; roles: string[]; actorType: string };
    hasRole: (role: string) => boolean;
  }) => unknown) =>
    selector({
      user: {
        id: "user-1",
        displayName: "Tariro Moyo",
        roles: ["doctor"],
        actorType: "PROVIDER",
      },
      hasRole: (role: string) => role === "SYSTEM_ADMIN" || role === "CITIZEN",
    }),
}));

vi.mock("@/hooks/useShiftStore", () => ({
  useShiftStore: (selector: (state: { shift: { startedAt: string } | null }) => unknown) =>
    selector({
      shift: { startedAt: "2026-04-08T06:30:00.000Z" },
    }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({
    isClinical: true,
    isAdmin: true,
    isFinance: false,
    isDispenser: false,
  }),
}));

vi.mock("@/hooks/useWorkModeStore", () => ({
  useWorkModeStore: Object.assign(vi.fn(), {
    getState: () => ({
      setMode,
    }),
  }),
}));

vi.mock("@/hooks/queries/useFacilities", () => ({
  useFacilities: () => ({
    data: {
      data: [
        {
          id: "facility-1",
          attributes: {
            name: "Harare Central",
            code: "HC",
            facilityType: "Hospital",
            capabilities: ["laboratory", "pacs"],
            operatingModel: {
              facilityTier: "CENTRAL_HOSPITAL",
              deploymentMode: "DEDICATED_POD",
              continuityClass: "LOCAL_EXECUTION_REQUIRED",
              workflowArchetype: "REFERRAL_AND_SPECIALIST",
            },
          },
        },
      ],
    },
    isLoading: false,
  }),
}));

vi.mock("@/hooks/queries/useLicenses", () => ({
  useProviderLicenses: () => ({
    data: {
      data: [
        {
          licenseNumber: "LIC-001",
          cadre: "doctor",
          status: "ACTIVE",
        },
      ],
    },
  }),
  hasActiveLicense: () => true,
}));

vi.mock("@/hooks/queries/useProviderPrivileges", () => ({
  useProviderPrivileges: () => ({
    data: {
      data: [],
    },
  }),
}));

vi.mock("@/hooks/queries/useFacilityOperations", () => ({
  useFacilityQueueStats: () => ({
    data: {
      data: {
        waiting: 6,
        called: 2,
        inService: 3,
        completed: 14,
        noShow: 1,
        avgWaitSeconds: 840,
      },
    },
  }),
  useFacilityActiveShiftCount: () => ({
    activeShiftCount: 4,
  }),
}));

vi.mock("@/hooks/queries/useCommunity", () => ({
  useCommunityGroups: () => ({
    data: {
      data: [],
    },
  }),
  useJoinGroup: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}));

vi.mock("@/providers/ExperienceEntryProvider", () => ({
  useExperienceEntry: () => ({
    facility: {
      id: "facility-1",
      name: "Harare Central",
      operatingModel: {
        facilityTier: "CENTRAL_HOSPITAL",
        deploymentMode: "DEDICATED_POD",
        continuityClass: "LOCAL_EXECUTION_REQUIRED",
        workflowArchetype: "REFERRAL_AND_SPECIALIST",
      },
    },
    selectFacility: vi.fn(),
    enterMode: vi.fn(),
    operationalMode: "facility_work" as const,
    availableOperationalModes: [
      "my_life",
      "my_professional",
      "facility_work",
      "registry_admin",
      "organization_admin",
    ] as const,
    setOperationalMode: vi.fn(),
    facilityWorkSubcontext: null,
    setFacilityWorkSubcontext: vi.fn(),
    registryAdminSubtype: null,
    setRegistryAdminSubtype: vi.fn(),
  }),
}));

vi.mock("@/hooks/useOperationalContextStore", () => {
  const state = {
    setOperationalMode: setOperationalModeMock,
    operationalMode: "facility_work" as const,
    facilityWorkSubcontext: null,
    setFacilityWorkSubcontext: vi.fn(),
    registryAdminSubtype: null,
    setRegistryAdminSubtype: vi.fn(),
  };
  const useOperationalContextStore = Object.assign(
    (selector: (s: typeof state) => unknown) => selector(state),
    { getState: () => state },
  );
  return { useOperationalContextStore };
});

vi.mock("@/hooks/useIdentityContext", () => ({
  useIdentityContext: () => ({
    isCitizenOnly: false,
    hasWorkAccess: true,
    hasProfessionalAccess: true,
    isProviderActivated: true,
    defaultLandingPath: "/home",
    defaultOperationalMode: "facility_work",
    shellMode: "facility_mode",
    visibleNavZones: ["work", "professional", "life"],
    needsContextChooser: false,
    needsProviderStatusResolution: false,
    isLoading: false,
  }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: ({ queryKey }: { queryKey: string[] }) => {
    switch (queryKey[0]) {
      case "home-recent-encounters":
        return {
          data: {
            data: [
              {
                id: "enc-1",
                attributes: {
                  patient_id: "patient-1",
                  encounter_type: "OUTPATIENT_VISIT",
                  started_at: "2026-04-08T07:30:00.000Z",
                  status: "IN_PROGRESS",
                },
              },
            ],
          },
        };
      case "home-today-appointments":
        return {
          data: {
            data: [
              {
                id: "apt-1",
                attributes: {
                  appointment_type: "Follow-up",
                  start_time: "2026-04-08T09:00:00.000Z",
                  status: "CONFIRMED",
                },
              },
            ],
          },
        };
      case "announcements-banner":
        return {
          data: {
            data: [],
            stats: { total: 0, pinned: 0, urgent: 0 },
          },
        };
      case "personal-feed":
        return {
          data: {
            data: [],
          },
        };
      default:
        return { data: { data: [] } };
    }
  },
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe("HomePage", () => {
  it("surfaces the latest module-home parity updates for clinical orders and communication", async () => {
    const user = userEvent.setup();

    render(<HomePage />);

    expect(screen.getByText("Launch Client Experience")).toBeInTheDocument();
    expect(screen.getByText("Patients waiting")).toBeInTheDocument();
    expect(screen.getByText("6")).toBeInTheDocument();
    expect(screen.getByText("Facility operating model")).toBeInTheDocument();
    expect(screen.getAllByText("Central Hospital").length).toBeGreaterThan(0);
    expect(screen.getByText("Service Delivery Pathways")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /Find Patient/i })[0]).toHaveAttribute("href", "/queue/search");
    expect(screen.getByRole("link", { name: /Walk-in Registration/i })).toHaveAttribute("href", "/queue/walk-in");
    expect(screen.getByRole("link", { name: /Waiting Room/i })).toHaveAttribute("href", "/queue/waiting");
    expect(screen.getByRole("link", { name: /New Teleconsult/i })).toHaveAttribute("href", "/telemedicine/new");
    expect(screen.getByText("Communication & Access")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Omnichannel" })).toHaveAttribute("href", "/omnichannel");

    await user.click(screen.getByRole("button", { name: /Clinical Care & Orders/i }));

    expect(screen.getByRole("link", { name: /Laboratory \(LIMS\)/i })).toHaveAttribute(
      "href",
      "/queue/search?workflow=lims",
    );
    expect(screen.getByRole("link", { name: /Imaging \(PACS\)/i })).toHaveAttribute(
      "href",
      "/queue/search?workflow=pacs",
    );

    await user.click(screen.getByRole("button", { name: /Facility Operations/i }));

    expect(screen.getByRole("link", { name: /Omnichannel Hub/i })).toHaveAttribute("href", "/omnichannel");
    expect(screen.queryByText("Orders & Pharmacy")).not.toBeInTheDocument();
    expect(screen.queryByText("Omnichannel & Access")).not.toBeInTheDocument();
  });
});
