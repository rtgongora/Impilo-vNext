import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { HandQueue, handQueueReducer } from "./HandQueue";
import type { SessionRealtimeEvent } from "@/hooks/useSessionRealtime";

let capturedOnEvent: ((event: SessionRealtimeEvent) => void) | undefined;

vi.mock("@/hooks/useSessionRealtime", () => ({
  useSessionRealtime: ({ onEvent }: { onEvent: (event: SessionRealtimeEvent) => void }) => {
    capturedOnEvent = onEvent;
  },
}));

function emit(type: string, actorId?: string) {
  act(() => {
    capturedOnEvent?.({ type, sessionRef: "sess-1", actorId, payload: {} });
  });
}

describe("handQueueReducer", () => {
  it("appends raise_hand events in order and de-duplicates actors", () => {
    let queue = handQueueReducer([], { type: "session.raise_hand", actorId: "a" }, 1);
    queue = handQueueReducer(queue, { type: "session.raise_hand", actorId: "b" }, 2);
    queue = handQueueReducer(queue, { type: "session.raise_hand", actorId: "a" }, 3);
    expect(queue.map((e) => e.actorId)).toEqual(["a", "b"]);
  });

  it("removes actors on hand_lowered and ignores unrelated/blank events", () => {
    let queue = handQueueReducer([], { type: "session.raise_hand", actorId: "a" }, 1);
    queue = handQueueReducer(queue, { type: "session.hand_lowered", actorId: "a" });
    expect(queue).toEqual([]);
    expect(handQueueReducer(queue, { type: "session.announcement", actorId: "a" })).toEqual([]);
    expect(handQueueReducer(queue, { type: "session.raise_hand" })).toEqual([]);
  });
});

describe("HandQueue", () => {
  it("shows the empty state until raise-hand frames arrive", () => {
    render(<HandQueue sessionRef="sess-1" />);
    expect(screen.getByTestId("hand-queue-empty")).toBeInTheDocument();

    emit("session.raise_hand", "PROV-7");
    emit("session.raise_hand", "USER-3");
    expect(screen.getAllByTestId("hand-queue-entry")).toHaveLength(2);
    expect(screen.getByText(/Raised hands \(2\)/)).toBeInTheDocument();
  });

  it("hand_lowered frames and local lowering both clear entries", () => {
    render(<HandQueue sessionRef="sess-1" />);
    emit("session.raise_hand", "PROV-7");
    emit("session.raise_hand", "USER-3");

    emit("session.hand_lowered", "PROV-7");
    expect(screen.getAllByTestId("hand-queue-entry")).toHaveLength(1);

    fireEvent.click(screen.getByRole("button", { name: /Lower \(local\)/ }));
    expect(screen.queryAllByTestId("hand-queue-entry")).toHaveLength(0);
    expect(screen.getByTestId("hand-queue-empty")).toBeInTheDocument();
  });

  it("ignores frames for other event types", () => {
    render(<HandQueue sessionRef="sess-1" />);
    emit("session.poll_launched", "PROV-7");
    expect(screen.getByTestId("hand-queue-empty")).toBeInTheDocument();
  });
});
