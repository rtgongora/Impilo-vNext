import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  Button: ({ title, onPress, disabled, testID }: any) =>
    React.createElement("button", { onClick: onPress, disabled, "data-testid": testID }, title),
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  TextField: ({ value, onChange, testID }: any) =>
    React.createElement("input", {
      value,
      "data-testid": testID,
      onChange: (e: { target: { value: string } }) => onChange?.(e.target.value),
    }),
  };
});

const svc = vi.hoisted(() => ({
  eventConversation: vi.fn(),
  meetingFromEvent: vi.fn(),
  fetchMessages: vi.fn(),
  sendMessage: vi.fn(),
}));
vi.mock("../../services/khulumaCommsService", () => svc);

import { EventDiscussionSection } from "../../screens/live/EventDiscussionSection";

function render() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      React.createElement(QueryClientProvider, { client }, React.createElement(EventDiscussionSection, { eventId: "ev-1" })),
    );
  });
  return { container, root };
}

// Advance REAL timers inside act() — TanStack v5 batches query notifications via timer ticks,
// so a microtask-only flush never renders a resolved query (the raw createRoot harness has no
// @testing-library waitFor). One real tick drains pending promises + the notify batch.
async function flush() {
  await act(async () => {
    await new Promise((r) => setTimeout(r, 0));
  });
}

// Poll (advancing real timers) until the predicate holds, then return; otherwise let it throw.
async function waitFor(predicate: () => void, timeout = 2000) {
  const start = Date.now();
  // eslint-disable-next-line no-constant-condition
  while (true) {
    try {
      predicate();
      return;
    } catch (err) {
      if (Date.now() - start >= timeout) throw err;
      await act(async () => {
        await new Promise((r) => setTimeout(r, 15));
      });
    }
  }
}

describe("EventDiscussionSection", () => {
  let mounted: { container: HTMLElement; root: Root } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    svc.fetchMessages.mockResolvedValue([]);
    svc.meetingFromEvent.mockResolvedValue({ conversationId: "c-9", eventId: "ev-1", mediaAvailable: false });
  });

  afterEach(() => {
    if (mounted) act(() => mounted?.root.unmount());
    mounted = undefined;
  });

  it("offers to start a discussion for an event with none, then attaches one", async () => {
    svc.eventConversation.mockResolvedValue(null);
    mounted = render();
    await flush();

    const startBtn = mounted.container.querySelector('[data-testid="start-discussion"]') as HTMLButtonElement;
    expect(startBtn).toBeTruthy();

    act(() => startBtn.dispatchEvent(new MouseEvent("click", { bubbles: true })));
    await flush();

    expect(svc.meetingFromEvent).toHaveBeenCalledWith("ev-1", []);
    expect(mounted.container.querySelector('[data-testid="event-discussion-messages"]')).toBeTruthy();
  });

  it("renders the anchored conversation's messages and composer", async () => {
    svc.eventConversation.mockResolvedValue({ conversationId: "c-1", eventId: "ev-1" });
    svc.fetchMessages.mockResolvedValue([
      { messageId: "m1", conversationId: "c-1", senderId: "prov-b", senderDisplayName: "Dr B", contentType: "TEXT", body: "Hi event", status: "SENT", sentAt: null },
    ]);
    mounted = render();

    // The dependent TanStack messages query resolves + renders the real message.
    await waitFor(() => expect(mounted!.container.textContent).toContain("Hi event"));
    expect(mounted.container.querySelector('[data-testid="start-discussion"]')).toBeNull();
    expect(mounted.container.querySelector('[data-testid="discussion-send"]')).toBeTruthy();
    expect(svc.fetchMessages).toHaveBeenCalledWith("c-1");
  });
});
