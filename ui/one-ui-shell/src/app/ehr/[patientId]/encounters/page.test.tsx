import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import EncountersPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => ({
    get: () => null,
  }),
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

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({ isClinical: true }),
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
            closedAt: null,
          },
        },
      ],
    },
    isLoading: false,
  }),
  useCreateEncounter: () => ({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
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
            body: "Referral loop update:\nLinked referral: ref-1",
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

describe("EncountersPage", () => {
  it("shows encounter coordination context alongside encounter history", () => {
    render(<EncountersPage />);

    expect(screen.getByText("Encounter coordination")).toBeInTheDocument();
    expect(screen.getByText("Open referrals")).toBeInTheDocument();
    expect(screen.getByText("Returned guidance")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Consults" })).toBeInTheDocument();
    expect(screen.getByText("Encounters (1)")).toBeInTheDocument();
  });
});
