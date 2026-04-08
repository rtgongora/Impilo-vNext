import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import TimelinePage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
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

vi.mock("@/hooks/queries/useTimeline", () => ({
  useTimeline: () => ({
    data: {
      data: [
        {
          id: "event-1",
          attributes: {
            eventType: "REFERRAL",
            title: "Cardiology referral returned",
            description: "Specialist response received",
            actorName: "Dr. Ncube",
            occurredAt: "2026-04-08T10:00:00.000Z",
          },
        },
      ],
    },
    isLoading: false,
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

describe("TimelinePage", () => {
  it("surfaces coordination context next to timeline events", () => {
    render(<TimelinePage />);

    expect(screen.getByText("Coordination timeline")).toBeInTheDocument();
    expect(screen.getByText("Referral events")).toBeInTheDocument();
    expect(screen.getByText("Returned guidance")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Consults" })).toBeInTheDocument();
    expect(screen.getByText("Timeline (1 events)")).toBeInTheDocument();
  });
});
