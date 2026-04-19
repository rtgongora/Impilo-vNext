import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import PatientChartPage from "./page";

const push = vi.fn();
const replace = vi.fn();

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
  useRouter: () => ({ push, replace }),
  useSearchParams: () => new URLSearchParams(""),
}));

vi.mock("@/components/EHRLayout", () => ({
  EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

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

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatient: () => ({
    data: {
      data: {
        id: "patient-1",
        attributes: {
          displayName: "Tariro Moyo",
          dateOfBirth: "1991-06-11",
          gender: "FEMALE",
          cpid: "CP-001",
        },
      },
    },
    isLoading: false,
  }),
}));

vi.mock("@/hooks/queries/useEncounters", () => ({
  useEncounters: () => ({
    data: {
      data: [
        {
          id: "enc-1",
          attributes: {
            status: "IN_PROGRESS",
            encounterType: "OUTPATIENT",
            startedAt: "2026-04-08T09:00:00.000Z",
          },
        },
      ],
    },
  }),
  useCreateEncounter: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}));

vi.mock("@/hooks/queries/useReferrals", () => ({
  useReferrals: () => ({
    data: {
      data: [
        {
          id: "ref-1",
          attributes: {
            status: "RESPONDED",
            receivingFacilityName: "Harare Central",
          },
        },
      ],
    },
  }),
}));

vi.mock("@/hooks/queries/useClinicalNotes", () => ({
  useClinicalNotes: () => ({
    data: {
      data: [
        {
          id: "note-1",
          attributes: {
            noteType: "CONSULTATION",
            body: "Referral loop update:\nLinked referral: ref-1\nLinked teleconsult: VIDEO session\nResponse sent from teleconsult: Yes\nNext workspace action: Review response and close the loop in Consults",
          },
        },
      ],
    },
  }),
}));

vi.mock("@/hooks/queries/useTelemedicine", () => ({
  useTelemedicineSessions: () => ({
    data: {
      data: [
        {
          id: "session-1",
          attributes: {
            status: "SCHEDULED",
          },
        },
      ],
    },
  }),
}));

vi.mock("@/hooks/queries/useInpatient", () => ({
  useAdmissions: () => ({ data: { data: [] } }),
}));

vi.mock("@/hooks/usePrivacyDisplayStore", () => {
  const privacyState: { masked: boolean; level: string } = { masked: false, level: "none" };
  const usePrivacyDisplayStore = (selector: (state: { masked: boolean; level: string }) => unknown) =>
    selector(privacyState);
  usePrivacyDisplayStore.getState = () => privacyState;
  return { usePrivacyDisplayStore };
});

describe("PatientChartPage", () => {
  beforeEach(() => {
    push.mockReset();
    replace.mockReset();
  });

  it("surfaces the care coordination pulse on the patient chart", () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <PatientChartPage />
      </QueryClientProvider>
    );

    expect(screen.getByText("Care coordination pulse")).toBeInTheDocument();
    expect(screen.getByText("Consult and teleconsult outcomes are visible from the chart")).toBeInTheDocument();
    expect(screen.getByText("Loop closures ready")).toBeInTheDocument();
    expect(screen.getByText("Receiving context")).toBeInTheDocument();
    expect(screen.getByText("Teleconsult activity")).toBeInTheDocument();
    expect(screen.getByText("Returned guidance")).toBeInTheDocument();
    expect(screen.getAllByText("1")).not.toHaveLength(0);
  });
});
