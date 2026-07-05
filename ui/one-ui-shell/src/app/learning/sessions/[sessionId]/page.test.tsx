import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import LearningSessionDetailPage from "./page";

const state: { session: Record<string, unknown> | undefined; isLoading: boolean } = {
  session: undefined,
  isLoading: false,
};

vi.mock("next/navigation", () => ({
  useParams: () => ({ sessionId: "sess-1" }),
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

vi.mock("@/hooks/queries/useFundoDelivery", () => ({
  useFundoSession: () => ({ data: state.session, isLoading: state.isLoading, isError: false }),
  useFundoFacilitators: () => ({
    data: { data: { items: [{ id: "fac-1", subjectId: "PROV-1", displayName: "Dr Mapfumo" }] } },
  }),
  useFundoVenues: () => ({
    data: { data: { items: [{ id: "ven-1", name: "Harare Training Room A" }] } },
  }),
}));

vi.mock("@/hooks/queries/useFundoCatalog", () => ({
  useFundoCourseStructure: () => ({
    data: { data: { structure: { id: "course-1", title: "TB Course", description: "Case management refresher", modules: [] } } },
  }),
}));

const FUTURE = "2099-01-01T09:00:00Z";

describe("LearningSessionDetailPage", () => {
  beforeEach(() => {
    state.session = undefined;
    state.isLoading = false;
  });

  it("shows the Enter classroom CTA for a joinable LIVE session", () => {
    state.session = {
      id: "sess-1",
      courseId: "course-1",
      title: "TB refresher class",
      sessionMode: "LIVE",
      sessionType: "VIRTUAL",
      liveEventId: "evt-1",
      startsAt: FUTURE,
      facilitatorId: "fac-1",
    };
    render(<LearningSessionDetailPage />);

    expect(screen.getByRole("heading", { level: 1, name: "TB refresher class" })).toBeInTheDocument();
    expect(screen.getByTestId("enter-classroom-cta")).toHaveAttribute("href", "/learning/sessions/sess-1/classroom");
    expect(screen.getByTestId("session-facilitator")).toHaveTextContent("Dr Mapfumo");
    expect(screen.getByTestId("session-checkin-link")).toHaveAttribute("href", "/learning/sessions/sess-1/checkin");
    expect(screen.getByTestId("session-course-description")).toHaveTextContent("Case management refresher");
    // LIVE-only sessions have no venue block.
    expect(screen.queryByTestId("session-venue")).not.toBeInTheDocument();
  });

  it("shows the venue for IN_PERSON sessions and hides the classroom CTA", () => {
    state.session = {
      id: "sess-1",
      courseId: "course-1",
      title: "Ward workshop",
      sessionMode: "IN_PERSON",
      sessionType: "IN_PERSON",
      startsAt: FUTURE,
      venueId: "ven-1",
    };
    render(<LearningSessionDetailPage />);

    expect(screen.getByTestId("session-venue")).toHaveTextContent("Harare Training Room A");
    expect(screen.queryByTestId("enter-classroom-cta")).not.toBeInTheDocument();
  });

  it("renders honest fallbacks for unassigned facilitator and venue", () => {
    state.session = {
      id: "sess-1",
      title: "Hybrid clinic teaching",
      sessionMode: "HYBRID",
      liveEventId: "evt-2",
      startsAt: FUTURE,
    };
    render(<LearningSessionDetailPage />);

    expect(screen.getByTestId("session-facilitator")).toHaveTextContent("Facilitator not assigned yet");
    expect(screen.getByTestId("session-venue")).toHaveTextContent("Venue not assigned yet");
    expect(screen.getByTestId("enter-classroom-cta")).toBeInTheDocument();
  });

  it("shows a not-found state when the session is missing", () => {
    render(<LearningSessionDetailPage />);
    expect(screen.getByText(/not found or currently unavailable/i)).toBeInTheDocument();
  });
});
