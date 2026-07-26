/**
 * Telehealth Waiting Room Tests — the session screen's waiting-room state
 * machine (WAITING → token flip, DENIED), audio-first mediaProfile, and the
 * post-consult stage.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";

const serviceMocks = vi.hoisted(() => ({
  joinSession: vi.fn(),
  endSession: vi.fn(),
  fetchSession: vi.fn(),
  requestSessionMediaToken: vi.fn(),
  refreshSessionMediaToken: vi.fn(),
}));

vi.mock("../../services/telehealthService", () => serviceMocks);

vi.mock("@impilo/mobile-auth", () => ({
  useAuth: () => ({ user: { sub: "cit-1", given_name: "Thandi", family_name: "Moyo", preferred_username: "thandi" } }),
}));

vi.mock("@impilo/mobile-messaging", () => ({
  useChannel: () => ({ status: "CONNECTED", messages: [], isConnected: true }),
}));

vi.mock("@impilo/mobile-session", async () => {
  const React = await import("react");
  return {
    PreJoinNative: ({ onJoin, joinDisabled, joinLabel }: { onJoin: (choices: { micEnabled: boolean; cameraEnabled: boolean }) => void; joinDisabled?: boolean; joinLabel?: string }) =>
      React.createElement(
        "button",
        {
          "data-testid": "prejoin-join",
          disabled: joinDisabled,
          onClick: () => onJoin({ micEnabled: true, cameraEnabled: true }),
        },
        joinLabel ?? "Join session"
      ),
    AdaptiveSessionRoomNative: ({ serverUrl, token, audioOnly, layout }: { serverUrl?: string; token?: string; audioOnly?: boolean; layout?: string }) =>
      React.createElement("div", {
        "data-testid": "session-room-stage",
        "data-server-url": serverUrl ?? "",
        "data-token": token ?? "",
        "data-audio-only": String(audioOnly ?? false),
        "data-layout": layout ?? "",
      }),
  };
});

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
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
    TextField: ({ value, onChange, testID }: { value: string; onChange: (next: string) => void; testID?: string }) =>
      React.createElement("input", {
        value,
        "data-testid": testID,
        onChange: (event: { target: { value: string } }) => onChange(event.target.value),
      }),
  };
});

import { TelehealthSessionScreen } from "../../screens/telehealth/TelehealthSessionScreen";
import type { TelehealthSession } from "../../types";

const session: TelehealthSession = {
  id: "ses-1",
  providerId: "prov-1",
  providerName: "Ndlovu",
  sessionType: "VIDEO",
  status: "SCHEDULED",
  scheduledAt: "2026-07-05T09:00:00Z",
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

describe("TelehealthSessionScreen waiting-room state machine", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    serviceMocks.joinSession.mockResolvedValue({ session, token: "", channel: "session-ses-1" });
  });

  afterEach(() => {
    act(() => {
      root?.unmount();
    });
    container?.remove();
    root = undefined;
    container = undefined;
    vi.useRealTimers();
  });

  it("flips WAITING → in-call once the provider admits (5s poll)", async () => {
    vi.useFakeTimers();
    serviceMocks.requestSessionMediaToken
      .mockResolvedValueOnce({ status: "WAITING" })
      .mockResolvedValueOnce({ status: "READY", roomUrl: "wss://rtc.impilo.zw", token: "jwt-admitted" });

    const el = renderScreen(<TelehealthSessionScreen session={session} onBack={vi.fn()} />);

    // Join → waiting room stage with guidance + consent line
    click(byTestId(el, "join-session"));
    await flush();
    expect(el.querySelector('[data-testid="telehealth-waiting-room"]')).toBeTruthy();
    expect(el.textContent).toContain("Who joins this consult");
    expect(el.textContent).toContain("consent");

    // Ask to join → WAITING → friendly status shown
    click(byTestId(el, "prejoin-join"));
    await flush();
    expect(serviceMocks.requestSessionMediaToken).toHaveBeenCalledWith("ses-1", {
      displayName: "Thandi Moyo",
      role: "PATIENT",
    });
    expect(el.querySelector('[data-testid="telehealth-waiting-status"]')).toBeTruthy();
    expect(el.querySelector('[data-testid="telehealth-in-call"]')).toBeFalsy();

    // Poll after 5s → READY → call stage with the scoped token
    await act(async () => {
      vi.advanceTimersByTime(5000);
    });
    await flush();
    expect(serviceMocks.requestSessionMediaToken).toHaveBeenCalledTimes(2);
    expect(el.querySelector('[data-testid="telehealth-in-call"]')).toBeTruthy();
    const stage = byTestId(el, "session-room-stage");
    expect(stage.getAttribute("data-token")).toBe("jwt-admitted");
    expect(stage.getAttribute("data-layout")).toBe("consult");
  });

  it("shows a kind message when the request is DENIED and stops polling", async () => {
    vi.useFakeTimers();
    serviceMocks.requestSessionMediaToken.mockResolvedValue({ status: "DENIED", reason: "Provider unavailable" });

    const el = renderScreen(<TelehealthSessionScreen session={session} onBack={vi.fn()} />);
    click(byTestId(el, "join-session"));
    await flush();
    click(byTestId(el, "prejoin-join"));
    await flush();

    const denied = byTestId(el, "telehealth-denied");
    expect(denied.textContent).toContain("Provider unavailable");
    expect(denied.textContent).toContain("nothing is lost");

    // No poll continues after a denial
    await act(async () => {
      vi.advanceTimersByTime(15000);
    });
    await flush();
    expect(serviceMocks.requestSessionMediaToken).toHaveBeenCalledTimes(1);
  });

  it("sends mediaProfile AUDIO_ONLY when audio-first is toggled", async () => {
    serviceMocks.requestSessionMediaToken.mockResolvedValue({ status: "WAITING" });

    const el = renderScreen(<TelehealthSessionScreen session={session} onBack={vi.fn()} />);
    click(byTestId(el, "join-session"));
    await flush();

    click(byTestId(el, "telehealth-audio-only-toggle"));
    click(byTestId(el, "prejoin-join"));
    await flush();

    expect(serviceMocks.requestSessionMediaToken).toHaveBeenCalledWith("ses-1", {
      displayName: "Thandi Moyo",
      role: "PATIENT",
      mediaProfile: "AUDIO_ONLY",
    });
  });

  it("moves to the post-consult stage after ending the session", async () => {
    serviceMocks.requestSessionMediaToken.mockResolvedValue({
      status: "READY",
      roomUrl: "wss://rtc.impilo.zw",
      token: "jwt-live",
    });
    serviceMocks.endSession.mockResolvedValue(undefined);
    serviceMocks.fetchSession.mockResolvedValue({
      ...session,
      status: "COMPLETED",
      startedAt: "2026-07-05T09:01:00Z",
      endedAt: "2026-07-05T09:20:00Z",
      notes: "Take medication twice daily",
    });

    const el = renderScreen(<TelehealthSessionScreen session={session} onBack={vi.fn()} />);
    click(byTestId(el, "join-session"));
    await flush();
    click(byTestId(el, "prejoin-join"));
    await flush();
    expect(el.querySelector('[data-testid="telehealth-in-call"]')).toBeTruthy();

    click(byTestId(el, "end-session-btn"));
    click(byTestId(el, "confirm-end-session"));
    await flush();

    expect(serviceMocks.endSession).toHaveBeenCalledWith("ses-1", undefined);
    expect(el.querySelector('[data-testid="telehealth-post-consult"]')).toBeTruthy();
    expect(byTestId(el, "telehealth-followup").textContent).toContain("Take medication twice daily");
  });
});
