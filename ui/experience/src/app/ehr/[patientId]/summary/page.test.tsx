import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import PatientSummaryPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
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
}));

vi.mock("@/hooks/queries/useReferrals", () => ({
  useReferrals: () => ({
    data: {
      data: [
        {
          id: "ref-1",
          attributes: {
            specialty: "Cardiology",
            reason: "Review persistent chest pain",
            urgency: "URGENT",
            status: "RESPONDED",
            referredToFacility: "Harare Central",
            createdAt: "2026-04-08T08:00:00.000Z",
            respondedAt: "2026-04-08T10:00:00.000Z",
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

vi.mock("@tanstack/react-query", () => ({
  useQuery: ({ queryKey }: { queryKey: [string, { patientId: string }] }) => {
    const key = queryKey[0];

    if (key === "vitals") {
      return {
        data: {
          data: [
            {
              id: "vital-1",
              type: "vital",
              attributes: {
                created_at: "2026-04-08T09:30:00.000Z",
                heart_rate: 88,
              },
            },
          ],
        },
      };
    }

    return { data: { data: [] } };
  },
}));

describe("PatientSummaryPage", () => {
  it("surfaces coordination signals and referral movement in summary", () => {
    render(<PatientSummaryPage />);

    expect(screen.getByText("Coordination snapshot")).toBeInTheDocument();
    expect(screen.getByText("Recent referral movement")).toBeInTheDocument();
    expect(screen.getByText("Loop closures ready")).toBeInTheDocument();
    expect(screen.getByText("Returned guidance")).toBeInTheDocument();
    expect(screen.getByText("Review persistent chest pain")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open Consults" })).toBeInTheDocument();
  });
});
