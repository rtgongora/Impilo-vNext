import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

vi.mock("@impilo/mobile-design-system", () => ({
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
}));

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

async function flush() {
  for (let i = 0; i < 6; i++) {
    await act(async () => {
      await Promise.resolve();
    });
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

  it("renders the discussion composer when a conversation is already anchored", async () => {
    svc.eventConversation.mockResolvedValue({ conversationId: "c-1", eventId: "ev-1" });
    mounted = render();
    await flush();

    // Anchored → no "start" prompt; the composer (input + send) is shown.
    expect(mounted.container.querySelector('[data-testid="start-discussion"]')).toBeNull();
    expect(mounted.container.querySelector('[data-testid="discussion-input"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="discussion-send"]')).toBeTruthy();
  });
});
