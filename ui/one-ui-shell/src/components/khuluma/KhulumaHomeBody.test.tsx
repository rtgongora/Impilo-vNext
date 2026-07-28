import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * KhulumaHomeBody (Phase F12) — proves the panel renders standalone, with no AppLayout/
 * PageShell wrapper of its own, so it can embed inside another page's chrome (a Work Home
 * section, a compact dashboard card). KhulumaHome.test.tsx is the byte-for-byte behavioural
 * proof that extracting this component changed nothing about the full-page route.
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
import { KhulumaHomeBody } from "./KhulumaHomeBody";

function renderBody() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    // Deliberately no AppLayout/PageShell wrapper — the point of this component.
    <QueryClientProvider client={qc}>
      <KhulumaHomeBody />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  h.hasRole.mockReturnValue(false);
  h.summary.mockReturnValue(query({ messages: { unreadCount: 3, conversations: 0 } }));
  h.inbox.mockReturnValue(query([{ conversationId: "c1", title: "Care team", lastMessagePreview: "hi", unreadCount: 2 }]));
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

describe("KhulumaHomeBody", () => {
  it("renders its content with no page-chrome wrapper of its own", () => {
    renderBody();

    expect(screen.getByTestId("khuluma-home")).toBeInTheDocument();
    expect(screen.getByText("Care team")).toBeInTheDocument();
  });

  it("stamps the resolved persona on the root element, exactly as the full-page route does", () => {
    h.hasRole.mockImplementation((r: string) => ["CLINICIAN", "NURSE"].includes(r));

    renderBody();

    expect(screen.getByTestId("khuluma-home")).toHaveAttribute("data-persona", "work");
  });
});
