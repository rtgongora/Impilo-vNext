/**
 * TelemedicineCallScreen tests — full-screen consult render with mocked
 * LiveKit room, media-token upgrade, audio-first toggle, and end-call flow.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";

const serviceMocks = vi.hoisted(() => ({
  endProviderTelemedicineSession: vi.fn(),
  sendProviderTelemedicineSignal: vi.fn(),
  requestProviderTelemedicineMediaToken: vi.fn(),
  refreshProviderTelemedicineMediaToken: vi.fn(),
}));

vi.mock("../../services/telemedicineService", () => serviceMocks);

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  const React = await import("react");
  return {
    ...actual,
    Screen: ({ children }: { children: React.ReactNode }) => React.createElement("div", null, children),
    Header: ({ title }: { title: string }) => React.createElement("header", null, title),
    Card: ({ children }: { children: React.ReactNode }) => React.createElement("section", null, children),
    CardBody: ({ children }: { children: React.ReactNode }) => React.createElement("div", null, children),
    Badge: ({ children }: { children: React.ReactNode }) => React.createElement("span", null, children),
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

vi.mock("@impilo/mobile-messaging", () => ({
  useChannel: () => ({ status: "CONNECTED", messages: [], isConnected: true }),
}));

vi.mock("@impilo/mobile-session", async () => {
  const React = await import("react");
  return {
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

import { TelemedicineCallScreen, mapChannelHealth } from "../../screens/provider/TelemedicineCallScreen";
import type { TelemedicineSession } from "../../types";

const session: TelemedicineSession = {
  id: "session-1",
  encounterId: "enc-1",
  patientId: "pat-1",
  providerId: "prov-1",
  status: "IN_PROGRESS",
  scheduledAt: "2026-07-05T09:00:00Z",
  sessionToken: "join-token",
  channelId: "chan-1",
  roomUrl: "wss://rtc.impilo.zw/join",
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

describe("TelemedicineCallScreen", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    serviceMocks.requestProviderTelemedicineMediaToken.mockResolvedValue({ status: "WAITING" });
  });

  afterEach(() => {
    act(() => {
      root?.unmount();
    });
    container?.remove();
    root = undefined;
    container = undefined;
  });

  it("renders the full-screen consult room with the join-payload fallback credentials", async () => {
    const el = renderScreen(<TelemedicineCallScreen session={session} onEnded={vi.fn()} />);
    await flush();

    expect(el.querySelector('[data-testid="telemedicine-call-screen"]')).toBeTruthy();
    const stage = byTestId(el, "session-room-stage");
    expect(stage.getAttribute("data-token")).toBe("join-token");
    expect(stage.getAttribute("data-server-url")).toBe("wss://rtc.impilo.zw/join");
    expect(stage.getAttribute("data-layout")).toBe("consult");
  });

  it("upgrades to the governed media token when the shared route answers READY", async () => {
    serviceMocks.requestProviderTelemedicineMediaToken.mockResolvedValue({
      status: "READY",
      roomUrl: "wss://rtc.impilo.zw/governed",
      token: "governed-token",
    });

    const el = renderScreen(
      <TelemedicineCallScreen session={session} displayName="Dr. Moyo" onEnded={vi.fn()} />
    );
    await flush();

    expect(serviceMocks.requestProviderTelemedicineMediaToken).toHaveBeenCalledWith("session-1", {
      displayName: "Dr. Moyo",
    });
    const stage = byTestId(el, "session-room-stage");
    expect(stage.getAttribute("data-token")).toBe("governed-token");
    expect(stage.getAttribute("data-server-url")).toBe("wss://rtc.impilo.zw/governed");
  });

  it("toggles audio-first mode", async () => {
    const el = renderScreen(<TelemedicineCallScreen session={session} onEnded={vi.fn()} />);
    await flush();

    expect(byTestId(el, "session-room-stage").getAttribute("data-audio-only")).toBe("false");
    act(() => {
      byTestId(el, "telemedicine-audio-only-toggle").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(byTestId(el, "session-room-stage").getAttribute("data-audio-only")).toBe("true");
  });

  it("ends the call with an optional note and notifies the parent", async () => {
    serviceMocks.endProviderTelemedicineSession.mockResolvedValue(undefined);
    const onEnded = vi.fn();
    const el = renderScreen(<TelemedicineCallScreen session={session} onEnded={onEnded} />);
    await flush();

    act(() => {
      byTestId(el, "end-session-btn").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    const note = byTestId(el, "end-call-note") as HTMLInputElement;
    act(() => {
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
      setter.call(note, "Client stable, follow-up in 2 weeks");
      note.dispatchEvent(new Event("input", { bubbles: true }));
    });
    act(() => {
      byTestId(el, "confirm-end-call").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(serviceMocks.endProviderTelemedicineSession).toHaveBeenCalledWith(
      "session-1",
      "Client stable, follow-up in 2 weeks"
    );
    expect(onEnded).toHaveBeenCalled();
  });
});

describe("mapChannelHealth (re-export)", () => {
  it("maps connected → primary", () => {
    expect(mapChannelHealth("connected").variant).toBe("primary");
  });
});
