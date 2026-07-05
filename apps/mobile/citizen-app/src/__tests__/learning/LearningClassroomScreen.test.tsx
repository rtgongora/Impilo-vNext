/**
 * Learning Classroom Screen Tests — the citizen classroom stage machine
 * (SESSION_INFO → IN_CLASS → AFTER), grid layout for classroom media,
 * audio-only toggle, and the honest attendance note.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";

const serviceMocks = vi.hoisted(() => ({
  joinLiveClassroom: vi.fn(),
}));

vi.mock("../../services/learningSessionsService", async () => {
  const actual = await vi.importActual<typeof import("../../services/learningSessionsService")>(
    "../../services/learningSessionsService",
  );
  return {
    ...actual,
    joinLiveClassroom: serviceMocks.joinLiveClassroom,
  };
});

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

vi.mock("@impilo/mobile-auth", () => ({
  authStore: { getState: () => ({ session: { actorId: "cit-actor-1" } }) },
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

vi.mock("@impilo/mobile-design-system", async () => {
  const React = await import("react");
  return {
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
  title: "Diabetes basics — live class",
  sessionType: "VIRTUAL",
  sessionMode: "LIVE",
  startsAt: "2026-07-10T09:00:00Z",
  endsAt: "2026-07-10T10:00:00Z",
  facilitator: "Nurse T. Chikafu",
  liveEventId: "evt-9",
  joinPath: "/live/event/evt-9",
  objectives: ["Recognize hypo symptoms"],
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

describe("LearningClassroomScreen stage machine (citizen)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    act(() => {
      root?.unmount();
    });
    container?.remove();
    root = undefined;
    container = undefined;
  });

  it("shows session info with objectives and a join button for a LIVE session", () => {
    const el = renderScreen(<LearningClassroomScreen session={liveSession} onBack={vi.fn()} />);

    const info = byTestId(el, "learning-classroom-info");
    expect(info.textContent).toContain("Diabetes basics — live class");
    expect(info.textContent).toContain("Recognize hypo symptoms");
    expect(info.textContent).toContain("Nurse T. Chikafu");
    expect(byTestId(el, "learning-classroom-join")).toBeTruthy();
  });

  it("hides the join button when the session has no live event anchor", () => {
    const el = renderScreen(
      <LearningClassroomScreen
        session={{ ...liveSession, sessionMode: "SCHEDULED", liveEventId: null }}
        onBack={vi.fn()}
      />,
    );
    expect(el.querySelector('[data-testid="learning-classroom-join"]')).toBeFalsy();
    expect(el.textContent).toContain("not live-joinable");
  });

  it("joins into IN_CLASS with the grid classroom layout, then leaves to AFTER", async () => {
    serviceMocks.joinLiveClassroom.mockResolvedValue({
      serverUrl: "wss://rtc.impilo.zw",
      token: "jwt-class",
    });

    const el = renderScreen(<LearningClassroomScreen session={liveSession} onBack={vi.fn()} />);
    click(byTestId(el, "learning-classroom-join"));
    await flush();

    expect(serviceMocks.joinLiveClassroom).toHaveBeenCalledWith("evt-9");
    expect(byTestId(el, "learning-classroom-in-class")).toBeTruthy();
    const stage = byTestId(el, "session-room-stage");
    // Classroom ≈ grid on mobile.
    expect(stage.getAttribute("data-layout")).toBe("grid");
    expect(stage.getAttribute("data-token")).toBe("jwt-class");

    // Audio-only toggle flows into the room props.
    click(byTestId(el, "learning-classroom-audio-only-toggle"));
    expect(byTestId(el, "session-room-stage").getAttribute("data-audio-only")).toBe("true");

    // Leave → AFTER with the honest attendance note.
    click(byTestId(el, "learning-classroom-leave"));
    const after = byTestId(el, "learning-classroom-after");
    expect(after.textContent).toContain("Your attendance was recorded");
  });

  it("surfaces a join failure without leaving SESSION_INFO", async () => {
    serviceMocks.joinLiveClassroom.mockRejectedValue(new Error("Governed classroom media is not ready yet."));

    const el = renderScreen(<LearningClassroomScreen session={liveSession} onBack={vi.fn()} />);
    click(byTestId(el, "learning-classroom-join"));
    await flush();

    expect(byTestId(el, "error-state").textContent).toContain("not ready yet");
    expect(byTestId(el, "learning-classroom-info")).toBeTruthy();
    expect(el.querySelector('[data-testid="learning-classroom-in-class"]')).toBeFalsy();
  });
});
