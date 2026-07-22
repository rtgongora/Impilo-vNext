import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * KhulumaHome unit test — the role-aware hub composes real-shaped hook data into honest
 * sections. Verifies: (1) citizen "life" persona shows honest zero-states and NO ops rail,
 * (2) provider "work" persona shows the escalation ops rail + real conversation data, and
 * (3) the upcoming-sessions composite renders items. Browser/live proof is held for deploy.
 */

const h = vi.hoisted(() => ({
  hasRole: vi.fn<(r: string) => boolean>(() => false),
  summary: vi.fn(),
  inbox: vi.fn(),
  incoming: vi.fn(),
  notifications: vi.fn(),
  channels: vi.fn(),
  caregivers: vi.fn(),
  linkages: vi.fn(),
  escalations: vi.fn(),
  onCall: vi.fn(),
  adapters: vi.fn(),
  sessions: vi.fn(),
}));

const query = (data: unknown) => ({ data, isLoading: false, isError: false, refetch: vi.fn() });
const mutation = () => ({ mutate: vi.fn(), isPending: false });

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/khuluma",
}));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/khuluma/KhulumaSubNav", () => ({ KhulumaSubNav: () => <nav data-testid="subnav" /> }));
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({ NompiloContextualGuidance: () => <div data-testid="nompilo" /> }));
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (sel: (s: { hasRole: (r: string) => boolean }) => unknown) => sel({ hasRole: h.hasRole }),
}));
vi.mock("@/hooks/queries/useComms", () => ({
  useCommsSummary: () => h.summary(),
  useCommsInbox: () => h.inbox(),
  useIncomingCalls: () => h.incoming(),
  useUpdatePresence: () => mutation(),
}));
vi.mock("@/hooks/queries/useNotifications", () => ({
  useNotifications: () => h.notifications(),
  useMarkNotificationRead: () => mutation(),
}));
vi.mock("@/hooks/queries/useKhulumaChannels", () => ({ useDiscoverChannels: () => h.channels() }));
vi.mock("@/hooks/queries/useCaregiverLinkage", () => ({
  useMyCaregivers: () => h.caregivers(),
  useMyCaregivingLinkages: () => h.linkages(),
}));
vi.mock("@/hooks/queries/useKhulumaOps", () => ({
  useEscalationQueue: () => h.escalations(),
  useOnCall: () => h.onCall(),
  useDeliveryAdapters: () => h.adapters(),
  useKhulumaSessions: () => h.sessions(),
  useEscalationAction: () => mutation(),
}));

// eslint-disable-next-line import/first
import { KhulumaHome } from "./KhulumaHome";

function renderHome() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <KhulumaHome />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  h.hasRole.mockReturnValue(false);
  h.summary.mockReturnValue(query({ messages: { unreadCount: 0, conversations: 0 } }));
  h.inbox.mockReturnValue(query([]));
  h.incoming.mockReturnValue(query([]));
  h.notifications.mockReturnValue(query({ data: [] }));
  h.channels.mockReturnValue(query([]));
  h.caregivers.mockReturnValue(query([]));
  h.linkages.mockReturnValue(query([]));
  h.escalations.mockReturnValue(query([]));
  h.onCall.mockReturnValue(query([]));
  h.adapters.mockReturnValue(query([]));
  h.sessions.mockReturnValue(query({ data: { items: [], sources: [] } }));
});

describe("KhulumaHome", () => {
  it("citizen (life) persona: honest zero-states, no ops rail", () => {
    h.hasRole.mockReturnValue(false);
    renderHome();
    expect(screen.getByText("No conversations yet")).toBeInTheDocument();
    expect(screen.getByText("Your people")).toBeInTheDocument();
    expect(screen.queryByText("Escalations & on-call")).not.toBeInTheDocument();
    expect(screen.getByText(/all caught up/i)).toBeInTheDocument();
  });

  it("provider (work) persona: ops rail with real escalation + recent conversations", () => {
    h.hasRole.mockImplementation((r: string) => ["CLINICIAN", "NURSE"].includes(r));
    h.inbox.mockReturnValue(query([{ conversationId: "c1", title: "Care team", lastMessagePreview: "hi", unreadCount: 2 }]));
    h.escalations.mockReturnValue(query([{ id: "e1", subject: "Urgent review", status: "OPEN" }]));
    renderHome();
    expect(screen.getByText("Escalations & on-call")).toBeInTheDocument();
    expect(screen.getByText("Urgent review")).toBeInTheDocument();
    expect(screen.getByText("Care team")).toBeInTheDocument();
    expect(screen.queryByText("Your people")).not.toBeInTheDocument();
  });

  it("renders the upcoming-sessions composite from real items", () => {
    h.sessions.mockReturnValue(
      query({ data: { items: [{ id: "s1", kind: "APPOINTMENT", title: "ANC visit", startAt: "2026-08-01T09:00:00Z", joinHref: "/citizen/appointments" }], sources: [] } }),
    );
    renderHome();
    expect(screen.getByText("ANC visit")).toBeInTheDocument();
  });
});
