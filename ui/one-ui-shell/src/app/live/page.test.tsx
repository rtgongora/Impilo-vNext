import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import LiveHubPage from "./page";

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

vi.mock("@/hooks/queries/useLive", () => ({
  useLiveEvents: (status?: string) => ({
    data: status === "LIVE" ? [] : [{ id: "evt-1", title: "Community TB Talk", status: "SCHEDULED", eventType: "WEBINAR", contextType: "CITIZEN", cpdEnabled: false }],
    isLoading: false,
  }),
}));

vi.mock("@/components/live/LiveEventList", () => ({
  LiveEventList: ({ events }: { events: Array<{ title: string }> }) => (
    <ul>
      {events.map((e) => (
        <li key={e.title}>{e.title}</li>
      ))}
    </ul>
  ),
}));

describe("LiveHubPage", () => {
  it("renders the Impilo Live hub with doctrine-aligned subtitle", () => {
    render(<LiveHubPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Impilo Live" })).toBeInTheDocument();
    expect(
      screen.getByText("Governed live events, webinars, broadcasts, CPD and citizen health talks"),
    ).toBeInTheDocument();
  });

  it("shows work zone tiles with correct links", () => {
    render(<LiveHubPage />);

    expect(screen.getByRole("link", { name: /Event Management/i })).toHaveAttribute("href", "/live/manage");
    expect(screen.getByRole("link", { name: /Create Event/i })).toHaveAttribute("href", "/live/create");
    expect(screen.getByRole("link", { name: /Live CPD/i })).toHaveAttribute("href", "/live/cpd");
  });

  it("shows citizen life-zone entry points", () => {
    render(<LiveHubPage />);

    expect(screen.getByRole("link", { name: /Discover Talks/i })).toHaveAttribute("href", "/live/discover");
    expect(screen.getByRole("link", { name: /My Events/i })).toHaveAttribute("href", "/live/my-events");
    expect(screen.getByRole("link", { name: /Replays/i })).toHaveAttribute("href", "/live/replays");
  });
});
