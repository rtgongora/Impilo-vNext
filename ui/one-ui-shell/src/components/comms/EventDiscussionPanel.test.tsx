import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EventDiscussionPanel } from "./EventDiscussionPanel";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (s: { user: { id: string; healthId: string; actorType: string } }) => unknown) =>
    selector({ user: { id: "me", healthId: "me", actorType: "PROVIDER" } }),
}));

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <EventDiscussionPanel eventId="ev-1" title="Webinar" />
    </QueryClientProvider>,
  );
}

describe("EventDiscussionPanel", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    post.mockResolvedValue({ messageId: "mx", conversationId: "c-1", senderId: "me", body: "x" });
  });

  it("renders the anchored conversation's messages and sends", async () => {
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/khuluma/events/ev-1/conversation") {
        return Promise.resolve({ conversationId: "c-1", eventId: "ev-1" });
      }
      if (url === "/internal/v1/khuluma/conversations/c-1/messages") {
        return Promise.resolve([
          { messageId: "m1", conversationId: "c-1", senderId: "prov-b", senderDisplayName: "Dr B", contentType: "TEXT", body: "Hi event", status: "SENT", sentAt: null },
        ]);
      }
      return Promise.resolve([]);
    });

    renderPanel();
    expect(await screen.findByText("Hi event")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Discussion message"), { target: { value: "Joining now" } });
    fireEvent.click(screen.getByRole("button", { name: "Send" }));
    await waitFor(() => {
      const sent = post.mock.calls.find((c) => c[0] === "/internal/v1/khuluma/conversations/c-1/messages");
      expect(sent?.[1]).toMatchObject({ body: "Joining now" });
    });
  });

  it("offers to start a discussion when the event has none, then attaches one", async () => {
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/khuluma/events/ev-1/conversation") {
        return Promise.reject(new Error("404")); // no conversation yet
      }
      if (url === "/internal/v1/khuluma/conversations/c-2/messages") {
        return Promise.resolve([]);
      }
      return Promise.resolve([]);
    });
    post.mockImplementation((url: string) => {
      if (url === "/internal/v1/khuluma/meetings/from-event") {
        return Promise.resolve({ conversationId: "c-2", eventId: "ev-1", mediaAvailable: false });
      }
      return Promise.resolve({});
    });

    renderPanel();
    const startBtn = await screen.findByRole("button", { name: /Start discussion/ });
    fireEvent.click(startBtn);

    await waitFor(() =>
      expect(post.mock.calls.some((c) => c[0] === "/internal/v1/khuluma/meetings/from-event")).toBe(true),
    );
    expect(await screen.findByText("No messages yet.")).toBeInTheDocument();
  });
});
