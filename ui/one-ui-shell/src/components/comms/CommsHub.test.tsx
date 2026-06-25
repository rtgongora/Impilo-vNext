import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CommsHub } from "./CommsHub";

const { get, post, put } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post, put },
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (s: { user: { id: string; healthId: string; actorType: string } }) => unknown) =>
    selector({ user: { id: "me", healthId: "me", actorType: "PROVIDER" } }),
}));

// Keep the LiveKit import chain out of jsdom; it is only used once a call is active.
vi.mock("@/components/comms/CommsCallModal", () => ({
  CommsCallModal: () => <div data-testid="call-modal" />,
}));

const CONVERSATION = {
  conversationId: "c1",
  type: "DIRECT",
  title: "Dr B",
  topic: null,
  status: "ACTIVE",
  createdBy: "me",
  lastMessagePreview: null,
  lastMessageAt: null,
  participants: [
    { participantId: "p1", actorId: "prov-b", actorType: "PROVIDER", displayName: "Dr B", role: "MEMBER", active: true, muted: false },
  ],
  links: [],
};

function renderHub() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <CommsHub persona="work" />
    </QueryClientProvider>,
  );
}

describe("CommsHub", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    put.mockReset();
    post.mockResolvedValue({});
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/khuluma/summary") {
        return Promise.resolve({ messages: { unreadCount: 2, conversations: 1 } });
      }
      if (url === "/internal/v1/khuluma/conversations") {
        return Promise.resolve([
          { conversationId: "c1", type: "DIRECT", title: "Dr B", status: "ACTIVE", lastMessagePreview: "hi", lastMessageAt: null, role: "OWNER", muted: false, unreadCount: 2 },
        ]);
      }
      if (url === "/internal/v1/khuluma/conversations/c1") {
        return Promise.resolve(CONVERSATION);
      }
      if (url === "/internal/v1/khuluma/conversations/c1/messages") {
        return Promise.resolve([
          { messageId: "m1", conversationId: "c1", senderId: "prov-b", senderType: "PROVIDER", senderDisplayName: "Dr B", contentType: "TEXT", body: "Hello there", status: "SENT", clientMessageId: null, sentAt: null },
        ]);
      }
      return Promise.resolve([]);
    });
  });

  it("renders the inbox with an unread badge", async () => {
    renderHub();
    expect(await screen.findByText("Dr B")).toBeInTheDocument();
    expect(screen.getByTestId("unread-badge")).toHaveTextContent("2");
  });

  it("opens a conversation, shows its messages and marks the newest peer message read", async () => {
    renderHub();
    const convButton = await screen.findByRole("button", { name: /Dr B/ });
    fireEvent.click(convButton);

    expect(await screen.findByText("Hello there")).toBeInTheDocument();
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/khuluma/conversations/c1/messages/read", { messageId: "m1" }),
    );
  });

  it("sends a typed message to the conversation endpoint", async () => {
    renderHub();
    fireEvent.click(await screen.findByRole("button", { name: /Dr B/ }));
    await screen.findByText("Hello there");

    fireEvent.change(screen.getByLabelText("Message"), { target: { value: "On my way" } });
    fireEvent.click(screen.getByRole("button", { name: /Send/ }));

    await waitFor(() => {
      const sendCall = post.mock.calls.find((c) => c[0] === "/internal/v1/khuluma/conversations/c1/messages");
      expect(sendCall).toBeTruthy();
      expect(sendCall?.[1]).toMatchObject({ body: "On my way" });
    });
  });

  it("updates presence when the status control changes", async () => {
    put.mockResolvedValue({ actorId: "me", status: "BUSY", statusMessage: null, lastSeenAt: null });
    renderHub();
    fireEvent.change(await screen.findByLabelText("Presence status"), { target: { value: "BUSY" } });
    await waitFor(() => expect(put).toHaveBeenCalledWith("/internal/v1/khuluma/presence", { status: "BUSY" }));
  });
});
