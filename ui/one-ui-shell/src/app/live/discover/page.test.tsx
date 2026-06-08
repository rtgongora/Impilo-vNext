import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import LiveDiscoverPage from "./page";

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

vi.mock("@/hooks/queries/useLive", () => ({
  useLiveDiscover: () => ({
    data: [{ id: "evt-1", title: "Community TB Talk", status: "SCHEDULED", eventType: "WEBINAR", contextType: "CITIZEN", cpdEnabled: false }],
    isLoading: false,
  }),
  useLiveMyRegistrations: () => ({ data: [] }),
  useLiveParticipant: () => ({
    participantId: "CPID-DEMO-001",
    participantType: "CITIZEN",
  }),
  useLiveRegister: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useLiveSaveBookmark: () => ({ mutate: vi.fn() }),
}));

vi.mock("@/components/live/LiveEventList", () => ({
  LiveEventList: ({ events, onRegister }: { events: Array<{ title: string }>; onRegister?: (id: string) => void }) => (
    <ul>
      {events.map((e) => (
        <li key={e.title}>
          {e.title}
          {onRegister ? (
            <button type="button" onClick={() => onRegister("evt-1")}>
              Register
            </button>
          ) : null}
        </li>
      ))}
    </ul>
  ),
}));

describe("LiveDiscoverPage", () => {
  it("renders discover shell with context filters and events", () => {
    render(<LiveDiscoverPage />);

    expect(screen.getByRole("heading", { name: "Live Health Talks" })).toBeInTheDocument();
    expect(screen.getByText("Community TB Talk")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "CITIZEN" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Register" })).toBeInTheDocument();
  });
});
