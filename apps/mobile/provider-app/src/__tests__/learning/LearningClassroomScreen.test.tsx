/**
 * Learning Classroom Screen Tests (provider) — stage machine with facilitator
 * affordances: role toggle (PRESENTER/ATTENDEE), attendance count line, grid
 * classroom layout, and the honest end-class note.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";

const serviceMocks = vi.hoisted(() => ({
  joinLiveClassroom: vi.fn(),
  fetchSessionAttendance: vi.fn(),
}));

vi.mock("../../services/learningSessionsService", async () => {
  const actual = await vi.importActual<typeof import("../../services/learningSessionsService")>(
    "../../services/learningSessionsService",
  );
  return {
    ...actual,
    joinLiveClassroom: serviceMocks.joinLiveClassroom,
    fetchSessionAttendance: serviceMocks.fetchSessionAttendance,
  };
});

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

vi.mock("@impilo/mobile-auth", () => ({
  authStore: { getState: () => ({ session: { actorId: "prov-actor-1" } }) },
}));

vi.mock("@impilo/mobile-session", async () => {
  const React = await import("react");
  return {
    AdaptiveSessionRoomNative: ({ serverUrl, token, layout, audioOnly }: { serverUrl?: string; token?: string; layout?: string; audioOnly?: boolean }) =>
      React.createElement("div", {
        "data-testid": "session-room-stage",
        "data-server-url": serverUrl ?? "",
        "data-token": token ?? "",
        "data-layout": layout ?? "",
        "data-audio-only": String(audioOnly ?? false),
      }),
  };
});

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal();
  const React = await import("react");
  return {
    ...actual,
    Screen: ({ children }: { children: React.ReactNode }) => React.createElement("div", null, children),
    Header: ({ title, leftElement }: { title: string; leftElement?: React.ReactNode }) =>
      React.createElement("header", null, leftElement, title),
    Card: ({ children }: { children: React.ReactNode }) => React.createElement("section", null, children),
    CardHeader: ({ title }: { title: string }) => React.createElement("h3", null, title),
    CardBody: ({ children }: { children: React.ReactNode }) => React.createElement("div", null, children),
    Badge: ({ children }: { children: React.ReactNode }) => React.createElement("span", null, children),
    ErrorState: ({ message }: { message: string }) => React.createElement("div", { "data-testid": "error-state" }, message),
    Button: ({ title, onPress, disabled, testID }: { title: string; onPress: () => void; disabled?: boolean; testID?: string }) =>
      React.createElement("button", { disabled, onClick: onPress, "data-testid": testID }, title),
  };
});

import { LearningClassroomScreen } from "../../screens/learning/LearningClassroomScreen";
import type { LearningSession } from "../../services/learningSessionsService";

const liveSession: LearningSession = {
  id: "ses-1",
  courseId: "course-1",
  title: "ETAT refresher — live class",
  sessionType: "VIRTUAL",
  sessionMode: "LIVE",
  startsAt: "2026-07-10T09:00:00Z",
  facilitator: "Dr. R. Gongora",
  liveEventId: "evt-1",
  joinPath: "/live/event/evt-1",
  objectives: [],
};

let root: Root | undefined;
let container: HTMLDivElement | undefined;

function renderScreen(element: React.ReactElement) {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
  act(() => {
    root!.render(element);
  });
  return container;
}

async function flush() {
  await act(async () => {
    await Promise.resolve();
  });
}

function byTestId(node: HTMLElement, id: string): HTMLElement {
  const found = node.querySelector(`[data-testid="${id}"]`);
  if (!found) throw new Error(`Missing testID: ${id}`);
  return found as HTMLElement;
}

function click(node: HTMLElement) {
  act(() => {
    node.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

describe("LearningClassroomScreen stage machine (provider)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    serviceMocks.fetchSessionAttendance.mockResolvedValue({ count: 4, items: [] });
  });

  afterEach(() => {
    act(() => {
      root?.unmount();
    });
    container?.remove();
    root = undefined;
    container = undefined;
  });

  it("shows session info with the attendance count line", async () => {
    const el = renderScreen(<LearningClassroomScreen session={liveSession} onBack={vi.fn()} />);
    await flush();

    expect(byTestId(el, "provider-classroom-info").textContent).toContain("ETAT refresher");
    expect(byTestId(el, "provider-classroom-attendance").textContent).toContain("Attendance recorded: 4");
    expect(byTestId(el, "provider-classroom-join")).toBeTruthy();
  });

  it("joins as facilitator by default and lands IN_CLASS with the grid layout", async () => {
    serviceMocks.joinLiveClassroom.mockResolvedValue({
      serverUrl: "wss://rtc.impilo.zw",
      token: "jwt-facilitator",
    });

    const el = renderScreen(<LearningClassroomScreen session={liveSession} onBack={vi.fn()} />);
    await flush();
    click(byTestId(el, "provider-classroom-join"));
    await flush();

    expect(serviceMocks.joinLiveClassroom).toHaveBeenCalledWith("evt-1", { asFacilitator: true });
    expect(byTestId(el, "provider-classroom-in-class")).toBeTruthy();
    const stage = byTestId(el, "session-room-stage");
    // Classroom ≈ grid on mobile.
    expect(stage.getAttribute("data-layout")).toBe("grid");
    expect(stage.getAttribute("data-token")).toBe("jwt-facilitator");
    // End-class honesty note (mobile leave ≠ ending the class for everyone).
    expect(byTestId(el, "provider-classroom-in-class").textContent).toContain("Leaving exits this device only");
  });

  it("toggling the role joins as ATTENDEE", async () => {
    serviceMocks.joinLiveClassroom.mockResolvedValue({ serverUrl: "wss://rtc.impilo.zw", token: "jwt-a" });

    const el = renderScreen(<LearningClassroomScreen session={liveSession} onBack={vi.fn()} />);
    await flush();
    click(byTestId(el, "provider-classroom-role-toggle"));
    click(byTestId(el, "provider-classroom-join"));
    await flush();

    expect(serviceMocks.joinLiveClassroom).toHaveBeenCalledWith("evt-1", { asFacilitator: false });
  });

  it("leaving moves to AFTER with the attendance summary", async () => {
    serviceMocks.joinLiveClassroom.mockResolvedValue({ serverUrl: "wss://rtc.impilo.zw", token: "jwt-f" });

    const el = renderScreen(<LearningClassroomScreen session={liveSession} onBack={vi.fn()} />);
    await flush();
    click(byTestId(el, "provider-classroom-join"));
    await flush();
    click(byTestId(el, "provider-classroom-leave"));

    const after = byTestId(el, "provider-classroom-after");
    expect(after.textContent).toContain("Attendance recorded for 4 participant(s)");
    expect(after.textContent).toContain("CPD and certificate outcomes");
  });

  it("hides the join controls for non-live sessions", async () => {
    const el = renderScreen(
      <LearningClassroomScreen
        session={{ ...liveSession, sessionMode: "SCHEDULED", liveEventId: null }}
        onBack={vi.fn()}
      />,
    );
    await flush();
    expect(el.querySelector('[data-testid="provider-classroom-join"]')).toBeFalsy();
    expect(el.textContent).toContain("not live-joinable");
  });
});
