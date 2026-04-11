import type { ReactNode } from "react";
import { cleanup, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ConsultsPage from "../consults/page";
import ClinicalNotesPage from "../notes/page";

const { push, replace, searchParams, mutate, queryData } = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  searchParams: new URLSearchParams("tab=referrals"),
  mutate: vi.fn(),
  queryData: {
    referrals: [
      {
        id: "ref-1",
        type: "referral",
        attributes: {
          patientId: "patient-1",
          encounterId: "enc-1",
          referralType: "SPECIALIST",
          specialty: "Cardiology",
          referredTo: "Cardiology Team",
          referredToFacility: "Mutare Provincial",
          reason: "Review persistent chest pain",
          urgency: "URGENT",
          status: "RESPONDED",
          clinicalSummary: "Troponin elevated",
          referredBy: "provider-9",
          referredByName: "Dr. Ncube",
          receivingFacilityId: "facility-2",
          receivingFacilityName: "Mutare Provincial",
          responseNotes:
            "Specialist reviewed ECG.\n\nReferral loop update:\nLinked referral: ref-1\nLinked teleconsult: VIDEO session\nResponse sent from teleconsult: Yes\nNext workspace action: Review response and close the loop in Consults",
          response_notes:
            "Specialist reviewed ECG.\n\nReferral loop update:\nLinked referral: ref-1\nLinked teleconsult: VIDEO session\nResponse sent from teleconsult: Yes\nNext workspace action: Review response and close the loop in Consults",
          respondedAt: "2026-04-08T10:00:00.000Z",
          responded_at: "2026-04-08T10:00:00.000Z",
          acceptedAt: "2026-04-08T09:00:00.000Z",
          accepted_at: "2026-04-08T09:00:00.000Z",
          outcome: "FOLLOW_UP_REQUIRED",
          createdAt: "2026-04-08T08:00:00.000Z",
        },
      },
    ],
    notes: [
      {
        id: "note-1",
        type: "clinical_note",
        attributes: {
          patientId: "patient-1",
          encounterId: "enc-1",
          noteType: "CONSULTATION",
          subjective: null,
          objective: null,
          assessment: null,
          plan: null,
          body: "Referral loop update:\nLinked referral: ref-1\nLinked teleconsult: VIDEO session\nResponse sent from teleconsult: Yes\nNext workspace action: Review response and close the loop in Consults",
          authorId: "user-1",
          authorName: "Dr. Moyo",
          status: "SIGNED",
          signedAt: "2026-04-08T10:15:00.000Z",
          createdAt: "2026-04-08T10:10:00.000Z",
        },
      },
    ],
  },
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
  useRouter: () => ({ push, replace }),
  useSearchParams: () => searchParams,
}));

vi.mock("@/components/EHRLayout", () => ({
  EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
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

vi.mock("@/components/ReferralPackageBuilder", () => ({
  ReferralPackageBuilder: () => <div>Referral Package Builder</div>,
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    user: { id: "user-1", displayName: "Dr. Moyo", email: "moyo@example.com" },
  }),
}));

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatient: () => ({
    data: {
      data: {
        id: "patient-1",
        attributes: {
          displayName: "Tariro Moyo",
          cpid: "CP-001",
        },
      },
    },
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
      data: queryData.referrals,
    },
    isLoading: false,
  }),
  useCreateReferral: () => ({ mutate, isPending: false }),
  useCompleteReferral: () => ({ mutate, isPending: false }),
  useAcceptReferral: () => ({ mutate, isPending: false }),
  useRespondReferral: () => ({ mutate, isPending: false }),
}));

vi.mock("@/hooks/queries/useTelemedicine", () => ({
  useTelemedicineSessions: () => ({
    data: { data: [] },
    isLoading: false,
  }),
  useCreateTelemedicineSession: () => ({ mutate, isPending: false }),
}));

vi.mock("@/hooks/queries/useClinicalNotes", () => ({
  useClinicalNotes: () => ({
    data: {
      data: queryData.notes,
    },
    isLoading: false,
  }),
  useCreateNote: () => ({ mutate, isPending: false }),
  useSignNote: () => ({ mutate, isPending: false }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({ data: { data: [] } }),
}));

describe("Care coordination journey", () => {
  beforeEach(() => {
    push.mockReset();
    replace.mockReset();
    mutate.mockReset();
    cleanup();
  });

  it("keeps teleconsult referral loop state visible across consults and notes", () => {
    const consultsView = render(<ConsultsPage />);

    expect(screen.getByText("Needs action now")).toBeInTheDocument();
    expect(screen.getByText("Loop ready to close")).toBeInTheDocument();
    expect(screen.getByText("Returned from teleconsult")).toBeInTheDocument();
    expect(screen.getByText("Next: Review response and close the loop in Consults")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Complete Loop/i })).toBeInTheDocument();

    consultsView.unmount();

    render(<ClinicalNotesPage />);

    expect(screen.getByText("Referral Handoffs")).toBeInTheDocument();
    expect(screen.getByText("Consult Orchestration")).toBeInTheDocument();
    expect(screen.getByText("Linked referral")).toBeInTheDocument();
    expect(screen.getByText("Linked teleconsult")).toBeInTheDocument();
    expect(screen.getAllByText("Returned from teleconsult")).not.toHaveLength(0);
  });
});
