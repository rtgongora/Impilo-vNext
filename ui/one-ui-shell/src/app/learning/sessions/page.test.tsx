import type { ReactNode } from "react";
import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import LearningSessionsPage from "./page";

const state: { items: Array<Record<string, unknown>>; isLoading: boolean; isError: boolean } = {
  items: [],
  isLoading: false,
  isError: false,
};

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
  useFundoSessions: () => ({
    data: { data: { items: state.items } },
    isLoading: state.isLoading,
    isError: state.isError,
  }),
}));

const FUTURE = "2099-01-01T09:00:00Z";
const PAST = "2020-01-01T09:00:00Z";

describe("LearningSessionsPage", () => {
  beforeEach(() => {
    state.items = [];
    state.isLoading = false;
    state.isError = false;
  });

  it("splits sessions into upcoming and past with mode badges", () => {
    state.items = [
      { id: "s1", title: "Live TB class", sessionMode: "LIVE", liveEventId: "evt-1", startsAt: FUTURE },
      { id: "s2", title: "Ward workshop", sessionType: "IN_PERSON", startsAt: PAST, endsAt: PAST },
    ];
    render(<LearningSessionsPage />);

    expect(screen.getByText("Upcoming (1)")).toBeInTheDocument();
    expect(screen.getByText("Past (1)")).toBeInTheDocument();
    const badges = screen.getAllByTestId("session-mode-badge");
    expect(badges.map((b) => b.textContent)).toEqual(["LIVE", "IN PERSON"]);
  });

  it("shows the join CTA only for upcoming live-capable sessions with a live event", () => {
    state.items = [
      { id: "s1", title: "Joinable live", sessionMode: "LIVE", liveEventId: "evt-1", startsAt: FUTURE },
      { id: "s2", title: "Live without event", sessionMode: "LIVE", startsAt: FUTURE },
      { id: "s3", title: "In person", sessionMode: "IN_PERSON", startsAt: FUTURE },
      { id: "s4", title: "Past live", sessionMode: "LIVE", liveEventId: "evt-2", startsAt: PAST, endsAt: PAST },
    ];
    render(<LearningSessionsPage />);

    const rows = screen.getAllByTestId("session-row");
    expect(rows).toHaveLength(4);
    const joinable = rows.find((row) => within(row).queryByText("Joinable live"));
    expect(within(joinable!).getByTestId("session-join-cta")).toHaveAttribute(
      "href",
      "/learning/sessions/s1/classroom"
    );
    expect(screen.getAllByTestId("session-join-cta")).toHaveLength(1);
  });

  it("derives LIVE badge + join CTA from legacy metadata sessions", () => {
    state.items = [
      {
        id: "s9",
        title: "Legacy virtual",
        sessionType: "VIRTUAL",
        startsAt: FUTURE,
        metadata: { impiloLiveEventId: "evt-9" },
      },
    ];
    render(<LearningSessionsPage />);
    expect(screen.getByTestId("session-mode-badge")).toHaveTextContent("LIVE");
    expect(screen.getByTestId("session-join-cta")).toHaveAttribute("href", "/learning/sessions/s9/classroom");
  });

  it("renders honest empty and error states", () => {
    render(<LearningSessionsPage />);
    expect(screen.getByTestId("upcoming-empty")).toBeInTheDocument();

    state.isError = true;
    render(<LearningSessionsPage />);
    expect(screen.getByText(/currently unavailable/i)).toBeInTheDocument();
  });
});
